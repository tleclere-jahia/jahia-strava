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

const initMap = (domId, userPath) => {
    const map = L.map(domId, {fullscreenControl: true});
    L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="https://mapbox.com">Mapbox</a>',
        maxZoom: 18
    }).addTo(map);
    navigator.geolocation.getCurrentPosition(location => {
        const latlng = new L.LatLng(location.coords.latitude, location.coords.longitude);
        map.setView(latlng, 11);
        const marker = L.marker(latlng).addTo(map);
    });

    loadActivities(map);
};

const buildDescription = activity => {
    return activity['name'] + "<br/>" +
        activity['start_date'] + "<br/>" +
        activity['distance'] + "m<br/>" +
        activity['total_elevation_gain'] + "m<br/>" +
        activity['moving_time'] + "s";
};

const addActivity = (activity, map) => {
    console.log(activity);
    L.polyline(decodePolyline(activity['map']['polyline']), {
        color: '#9900CC',
        weight: 2,
        opacity: .7,
        lineJoin: 'round'
    }).addTo(map)
        .bindPopup(buildDescription(activity))
        .bindTooltip(activity['name'], {sticky: true})
        .on('mouseover', e => e.target.setStyle({color: 'red', weight: 5, opacity: 1}))
        .on('mouseout', e => e.target.setStyle({color: '#9900CC', weight: 2, opacity: .7}));
};

const loadActivities = async map => {
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
                                        date: property(name: "date") {
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
    data.data.currentUser.node.descendants.nodes.forEach(node => addActivity(JSON.parse(node.json.value), map));
};
