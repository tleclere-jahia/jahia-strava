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
<h1><fmt:message key="stravaMap.user.hello">
    <fmt:param value="${userNode.properties['j:firstName'].string} ${userNode.properties['j:lastName'].string}"/>
</fmt:message></h1>

<template:addCacheDependency flushOnPathMatchingRegexp="${userNode.path}/strava-activities/.*"/>
<c:set var="activities" value="${jcr:getDescendantNodes(userNode, 'foont:stravaActivity')}"/>
<fmt:message key="stravaMap.user.activities">
    <fmt:param value="${functions:length(activities)}"/>
</fmt:message>, <a href="${url.base}${renderContext.mainResource.node.path}.syncMe.do"><fmt:message
        key="stravaMap.user.syncMe"/></a>

<template:addResources key="leaflet">
    <script src="http://cdn.leafletjs.com/leaflet-0.7/leaflet.js"></script>
    <script type="text/javascript" src="https://rawgit.com/jieter/Leaflet.encoded/master/Polyline.encoded.js"></script>
    <link rel="stylesheet" href="http://cdn.leafletjs.com/leaflet-0.7/leaflet.css"/>
</template:addResources>
<div id="map-${currentNode.identifier}" style="width: 100%; height: 380px"></div>

<template:addResources type="inlinejavascript">
    <script>
        document.addEventListener("DOMContentLoaded", () => {
            L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 18})
                .addTo(L.map('map-${currentNode.identifier}').setView([51.498265, -0.135642], 13));
        });
    </script>
</template:addResources>
