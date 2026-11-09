/**
 * Decode an encoded polyline string
 */
const decodePolyline = polyline => {
    //precision
    const inv = 1.0 / 1e5;
    const decoded = [];
    const previous = [0, 0];
    let i = 0;
    //for each byte
    while (i < polyline.length) {
        //for each coord (lat, lon)
        const ll = [0, 0]
        for (let j = 0; j < 2; j++) {
            let shift = 0;
            let byte = 0x20;
            //keep decoding bytes until you have this coord
            while (byte >= 0x20) {
                byte = polyline.charCodeAt(i++) - 63;
                ll[j] |= (byte & 0x1f) << shift;
                shift += 5;
            }
            //add previous offset to get final value and remember for next one
            ll[j] = previous[j] + (ll[j] & 1 ? ~(ll[j] >> 1) : (ll[j] >> 1));
            previous[j] = ll[j];
        }
        //scale by precision and chop off long coords also flip the positions so
        //its the far more standard lon,lat instead of lat,lon
        decoded.push([ll[0] * inv, ll[1] * inv]);
    }
    //hand back the list of coordinates
    return decoded;
};

const initMap = (nodeIdentifier, locale) => {
    window.stravaMap = L.map(`map-${nodeIdentifier}`, {
        fullscreenControl: true,
        layers: [
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            })
        ]
    }).on('popupopen', e => e.popup.options.polyline.addTo(window.stravaMap)).on('popupclose', e => e.popup.options.polyline.remove());

    navigator.geolocation.getCurrentPosition(location => window.stravaMap.setView(new L.LatLng(location.coords.latitude, location.coords.longitude), 11));

    loadActivities(null, locale, document.getElementById(`loading-${nodeIdentifier}`), document.getElementById(`error-${nodeIdentifier}`), false, []);
};

const buildDescription = (activity, locale) => {
    const duration = Temporal.Duration.from(`PT${activity['moving_time']}S`);
    return `<h4>${activity['name']}</h4>
    <ul>
        <li>Date:<span title="${activity['start_date']}">${new Intl.DateTimeFormat(locale, {
        dateStyle: "full",
        timeStyle: "long",
    }).format(Date.parse(activity['start_date']))}</span></li>
        <li>Distance: ${activity['distance']} m</li>
        <li>Elevation: ${activity['total_elevation_gain']} m</li>
        <li>Duration: ${new Intl.DurationFormat(locale, {style: "long"}).format(duration)}</li>
    </ul>`;
};

const createPolyline = activity => {
    const polyline = L.polyline(decodePolyline(activity['map']['polyline']), {
        color: '#9900CC',
        weight: 2,
        opacity: .7,
        lineJoin: 'round'
    });
    polyline.bindTooltip(activity['name'], {sticky: true})
        .on('mouseover', e => e.target.setStyle({color: 'red', weight: 5, opacity: 1}))
        .on('mouseout', e => e.target.setStyle({color: '#9900CC', weight: 2, opacity: .7}));
    return polyline;
};

const loadActivities = async (after, locale, domLoading, domError, inBackground, features) => {
    if (!inBackground) domLoading.style.display = 'block';
    const response = await fetch('/modules/graphql', {
        method: 'POST',
        body: JSON.stringify({
            query: `query { currentUser {
                      node {
                        children(names: ["strava-activities"]) {
                            nodes {
                              descendants(
                                typesFilter: {types: ["foont:stravaActivity"]}
                                maxDepth: 3
                                first: 100 ${after ? `after: "${after}"` : ''}
                              ) {
                                pageInfo {
                                  totalCount
                                  hasNextPage
                                  endCursor
                                }
                                nodes {
                                  path
                                  json: property(name: "jsonValue") {
                                    value
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }`
        })
    });
    try {
        const data = await response.json();
        const hasNextPage = data.data.currentUser.node.children?.nodes[0].descendants.pageInfo.hasNextPage;
        document.getElementById('nbActivities').innerText = data.data.currentUser.node.children?.nodes[0].descendants.pageInfo.totalCount;
        if (hasNextPage) {
            loadActivities(data.data.currentUser.node.children?.nodes[0].descendants.pageInfo.endCursor, locale, domLoading, domError, inBackground, features);
        }
        const markers = L.markerClusterGroup({removeOutsideVisibleBounds: false});
        data.data.currentUser.node.children?.nodes[0].descendants.nodes.forEach(node => {
            const activity = JSON.parse(node.json.value);
            if (activity['start_latlng'] && Array.isArray(activity['start_latlng']) && activity['start_latlng'].length === 2) {
                const marker = L.marker(new L.LatLng(activity['start_latlng'][0], activity['start_latlng'][1]), {
                    title: activity['name'],
                    activity
                });
                features.push(marker);
                markers.addLayer(marker);
            }
        });
        markers.on('click', e => {
            const activity = e.layer.options.activity;
            const polyline = createPolyline(activity);
            L.popup(e.latlng, {content: buildDescription(activity, locale), polyline}).openOn(window.stravaMap)
            window.stravaMap.fitBounds(polyline.getBounds());
        });
        window.stravaMap.addLayer(markers);
        if (!hasNextPage && !inBackground) domLoading.style.display = 'none';
        if (!hasNextPage) window.stravaMap.fitBounds(new L.featureGroup(features).getBounds());
    } catch (error) {
        displayError(domLoading, domError, error);
    }
};

const displayError = (domLoading, domError, error) => {
    console.error(error);
    domLoading.style.display = 'none';
    domError.style.display = 'block';
};

const syncData = (urlServer, nodeIdentifier) => {
    const domLoading = document.getElementById(`loading-${nodeIdentifier}`);
    const domError = document.getElementById(`error-${nodeIdentifier}`);
    const backgroundJobSocket = new WebSocket(`${urlServer.replace('http', 'ws')}/modules/graphqlws`);
    backgroundJobSocket.onopen = () => backgroundJobSocket.send(JSON.stringify({
        type: 'connection_init',
        payload: {},
        query: `subscription {
                  subscribeToStravaSynchronization {
                    status
                    message
                  }
                }`
    }));
    backgroundJobSocket.onmessage = event => {
        const data = JSON.parse(event.data)
        try {
            if (data.data.subscribeToStravaSynchronization.status === 'FINISHED') {
                location.reload();
            } else {
                domLoading.style.display = 'block';
                domLoading.innerText = data.data?.subscribeToStravaSynchronization?.message || '';
                // TODO: loadActivities(0, domLoading, domError, true);
                console.log(`backgroundJobSocket message`, event)
            }
        } catch (error) {
            displayError(domLoading, domError, error);
        }
    }
    backgroundJobSocket.onerror = error => displayError(domLoading, domError, error);
};

const syncMe = (e, url) => {
    e.preventDefault();
    fetch(url);
    return false;
};
