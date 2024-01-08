<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="ui" uri="http://www.jahia.org/tags/uiComponentsLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="query" uri="http://www.jahia.org/tags/queryLib" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="s" uri="http://www.jahia.org/tags/search" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="currentUser" type="org.jahia.services.usermanager.JahiaUser"--%>
<jcr:node var="userNode" path="${currentUser.localPath}"/>
<template:addCacheDependency node="${userNode}"/>
<h1><fmt:message key="stravaMap.user.hello">
    <fmt:param value="${userNode.properties['j:firstName'].string} ${userNode.properties['j:lastName'].string}"/>
</fmt:message></h1>

<template:addCacheDependency flushOnPathMatchingRegexp="${userNode.path}/strava-activities/.*"/>
<c:set var="activities" value="${jcr:getDescendantNodes(userNode, 'foont:stravaActivity')}"/>
<c:set var="nbActivities" value="${functions:length(activities)}"/>
<fmt:message key="stravaMap.user.activities">
    <fmt:param value="${nbActivities}"/>
</fmt:message>, <fmt:message key="stravaMap.user.lastStravaSync"/>: <fmt:formatDate pattern="dd/MM/yyyy HH:mm"
        value="${userNode.properties['lastStravaSync'].time}"/>,
<a href="${url.base}${renderContext.mainResource.node.path}.syncMe.do"><fmt:message
        key="stravaMap.user.syncMe"/></a>

<template:addResources key="leaflet">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
          integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
            integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <script src='https://api.mapbox.com/mapbox.js/plugins/leaflet-fullscreen/v1.0.1/Leaflet.fullscreen.min.js'></script>
    <link href='https://api.mapbox.com/mapbox.js/plugins/leaflet-fullscreen/v1.0.1/leaflet.fullscreen.css'
          rel='stylesheet'/>
</template:addResources>
<div id="map-${currentNode.identifier}" style="height:580px"></div>

<template:addResources type="inlinejavascript">
    <script>
        //decode an encoded string
        const decode = encoded => {
            //precision
            const inv = 1.0 / 1e5;
            const decoded = [];
            const previous = [0, 0];
            let i = 0;
            //for each byte
            while (i < encoded.length) {
                //for each coord (lat, lon)
                const ll = [0, 0]
                for (let j = 0; j < 2; j++) {
                    let shift = 0;
                    let byte = 0x20;
                    //keep decoding bytes until you have this coord
                    while (byte >= 0x20) {
                        byte = encoded.charCodeAt(i++) - 63;
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

        const activities = [];
        <c:forEach items="${activities}" var="activity">
        activities.push(${activity.properties['jsonValue'].string});
        </c:forEach>

        const buildDescription = activity => {
            return activity['name'] + "<br/>" +
                activity['start_date'] + "<br/>" +
                activity['distance'] + "m<br/>" +
                activity['total_elevation_gain'] + "m<br/>" +
                activity['moving_time'] + "s";
        }

        document.addEventListener("DOMContentLoaded", () => {
            const map = L.map('map-${currentNode.identifier}', {fullscreenControl: true});
            L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="https://mapbox.com">Mapbox</a>',
                maxZoom: 18
            }).addTo(map);

            activities.forEach(activity => {
                console.log(activity);
                L.polyline(decode(activity['map']['polyline']), {
                    color: '#9900CC',
                    weight: 2,
                    opacity: .7,
                    lineJoin: 'round'
                }).addTo(map)
                    .bindPopup(buildDescription(activity))
                    .bindTooltip(activity['name'], {sticky: true})
                    .on('mouseover', e => e.target.setStyle({color: 'red', weight: 5, opacity: 1}))
                    .on('mouseout', e => e.target.setStyle({color: '#9900CC', weight: 2, opacity: .7}));
            });

            navigator.geolocation.getCurrentPosition(location => {
                const latlng = new L.LatLng(location.coords.latitude, location.coords.longitude);
                map.setView(latlng, 13);
                const marker = L.marker(latlng).addTo(map);
            });
        });
    </script>
</template:addResources>
