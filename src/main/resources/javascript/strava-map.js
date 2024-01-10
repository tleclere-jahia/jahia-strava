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
    const map = L.map(`map-${nodeIdentifier}`, {
        fullscreenControl: true,
        layers: [
            L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="https://mapbox.com">Mapbox</a>',
                maxZoom: 18
            })
        ]
    });
    navigator.geolocation.getCurrentPosition(location => map.setView(new L.LatLng(location.coords.latitude, location.coords.longitude), 11));

    loadActivities(map, document.getElementById(`loading-${nodeIdentifier}`));
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

const loadActivities = async (map, domLoading) => {
    domLoading.style.display = 'block';
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
        L.popup(e.latlng, {content: buildDescription(activity), polyline: createPolyline(activity)}).openOn(map)
    });
    map.on('popupopen', e => e.popup.options.polyline.addTo(map))
        .on('popupclose', e => e.popup.options.polyline.remove());
    map.addLayer(markers);
    domLoading.style.display = 'none';
};

const syncData = (urlServer, nodeIdentifier, userId) => {
    const domLoading = document.getElementById(`loading-${nodeIdentifier}`);
    const backgroundJobSocket = new WebSocket(`${urlServer.replace('http', 'ws')}/modules/graphqlws`);
    backgroundJobSocket.onopen = () => backgroundJobSocket.send(JSON.stringify({
        type: 'connection_init',
        payload: {}
    }));
    backgroundJobSocket.onmessage = event => {
        console.log(event);
        const data = JSON.parse(event.data)
        if (data.type === 'connection_ack') {
            backgroundJobSocket.send(JSON.stringify({
                type: 'start',
                id: userId,
                payload: {
                    query: `subscription {
                        backgroundJobSubscription(
                            targetScheduler: SCHEDULER
                            filterByGroups: ["SyncBackgroundJob"]
                        ) {
                            name
                            jobStatus
                            jobState
                            duration
                        }
                    }`
                }
            }));
        } else if (data.type === 'data') {
            if (data.payload.data.backgroundJobSubscription?.jobState === 'STARTED') {
                domLoading.style.display = 'block';
            } else if (data.payload.data.backgroundJobSubscription?.jobState === 'FINISHED') {
                location.reload();
            }
        }
    }
};

const syncMe = (e, url) => {
    e.preventDefault();
    fetch(url);
    return false;
};
