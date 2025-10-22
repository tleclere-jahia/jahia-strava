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

const initMap = nodeIdentifier => {
    window.stravaMap = L.map(`map-${nodeIdentifier}`, {
        fullscreenControl: true,
        layers: [
            L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="https://mapbox.com">Mapbox</a>',
                maxZoom: 18
            })
        ],
        center: [46.966671, -0.21667],
        zoom: 11
    }).on('popupopen', e => e.popup.options.polyline.addTo(window.stravaMap)).on('popupclose', e => e.popup.options.polyline.remove());

    navigator.geolocation.getCurrentPosition(location => window.stravaMap.setView(new L.LatLng(location.coords.latitude, location.coords.longitude), 11));

    loadActivities(document.getElementById(`loading-${nodeIdentifier}`), document.getElementById(`error-${nodeIdentifier}`), false);
};

const buildDescription = activity => {
    return activity['name'] + "<br/>" +
        activity['start_date'] + "<br/>" +
        activity['distance'] + "m<br/>" +
        activity['total_elevation_gain'] + "m<br/>" +
        activity['moving_time'] + "s";
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

const loadActivities = async (domLoading, domError, inBackground) => {
    if(!inBackground) domLoading.style.display = 'block';
    const response = await fetch('/modules/graphql', {
        method: 'POST',
        body: JSON.stringify({
            query: `query {
                        currentUser {
                            node {
                                descendants(typesFilter: { types: ["foont:stravaActivity"] }) {
                                    pageInfo {
                                        totalCount
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
            }`
        })
    });
    try {
        const data = await response.json();
        document.getElementById('nbActivities').innerText = data.data.currentUser.node.descendants.pageInfo.totalCount;
        const markers = L.markerClusterGroup({removeOutsideVisibleBounds: false});
        data.data.currentUser.node.descendants.nodes.forEach(node => {
            const activity = JSON.parse(node.json.value);
            if (activity['start_latlng'] && Array.isArray(activity['start_latlng']) && activity['start_latlng'].length === 2) {
                markers.addLayer(L.marker(new L.LatLng(activity['start_latlng'][0], activity['start_latlng'][1]), {
                    title: activity['name'],
                    activity
                }));
            }
        });
        markers.on('click', e => {
            const activity = e.layer.options.activity;
            L.popup(e.latlng, {content: buildDescription(activity), polyline: createPolyline(activity)}).openOn(window.stravaMap)
        });
        window.stravaMap.addLayer(markers);
        if(!inBackground) domLoading.style.display = 'none';
    } catch (error) {
        displayError(domLoading, domError);
    }
};

const displayError = (domLoading, domError) => {
    domLoading.style.display = 'none';
    domError.style.display = 'block';
    setTimeout(() => domError.style.display = 'none', 3000);
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
                loadActivities(domLoading, domError, true);
            }
        } catch (error) {
            console.error(error);
            displayError(domLoading, domError);
        }
    }
    backgroundJobSocket.onerror = error => {
        console.error(error);
        displayError(domLoading, domError);
    }
};

const syncMe = (e, url) => {
    e.preventDefault();
    fetch(url);
    return false;
};
