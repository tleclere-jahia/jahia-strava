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

<c:if test="${!renderContext.editMode}">
    <div>
        <fmt:message key="stravaMap.user.activities"/>&nbsp;(<fmt:message key="stravaMap.user.lastStravaSync"/>:
        <fmt:formatDate pattern="dd/MM/yyyy HH:mm" value="${userNode.properties['lastStravaSync'].time}"/>)<br/>
        <a href="#" onclick="return syncMe(event, '${url.base}${renderContext.mainResource.node.path}.syncMe.do')"><fmt:message
                key="stravaMap.user.syncMe"/></a>&nbsp;(<fmt:message key="stravaMap.user.syncMe.description"/>)
    </div>

    <template:addResources key="leaflet">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
              integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
        <script src='https://api.mapbox.com/mapbox.js/plugins/leaflet-fullscreen/v1.0.1/Leaflet.fullscreen.min.js'></script>
        <link href='https://api.mapbox.com/mapbox.js/plugins/leaflet-fullscreen/v1.0.1/leaflet.fullscreen.css'
              rel='stylesheet'/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.4.1/dist/MarkerCluster.css"/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.4.1/dist/MarkerCluster.Default.css"/>
        <script src="https://unpkg.com/leaflet.markercluster@1.4.1/dist/leaflet.markercluster.js"></script>
    </template:addResources>
    <template:addResources type="javascript" resources="strava-map.js"/>
    <template:addResources type="css" resources="strava-map.css"/>
    <div id="map-${currentNode.identifier}" class="map">
        <div id="loading-${currentNode.identifier}" class="ribbon"><fmt:message key="stravaMap.loading"/></div>
    </div>

    <template:addResources type="inlinejavascript">
        <script>
            document.addEventListener("DOMContentLoaded", () => {
                initMap('${currentNode.identifier}');
                syncData('${url.server}${url.context}', '${currentNode.identifier}', '${userNode.identifier}');
            });
        </script>
    </template:addResources>
</c:if>
