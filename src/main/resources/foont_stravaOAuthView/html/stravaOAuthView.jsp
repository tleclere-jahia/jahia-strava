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
<template:addResources type="javascript" var="i18nJSFile"
                       resources="i18n/jahia-strava-i18n_${renderContext.UILocale}.js"/>
<c:if test="${empty i18nJSFile}">
    <template:addResources type="javascript" resources="i18n/jahia-strava-i18n.js"/>
</c:if>
<template:addResources type="javascript" resources="oauth-connector-controller.js"/>

<c:set var="connectorVar" value="connector${fn:replace(currentNode.identifier, '-', '')}" scope="request"/>
<c:set var="connectorFormVar" value="connectorForm${fn:replace(currentNode.identifier, '-', '')}" scope="request"/>

<template:include view="hidden.jsonProperties" templateType="json" var="properties"/>

<md-card ng-controller="OAuthConnectorController as ${connectorVar}" class="ng-cloak"
         ng-init="${connectorVar}.init('StravaApi20', <c:out value="${properties})"/>">
    <div layout="row">
        <md-card-title flex>
            <md-card-title-text>
                <span class="md-headline" message-key="foont_stravaOAuthView"></span>
            </md-card-title-text>
        </md-card-title>
        <div flex layout="row" layout-align="end center">
            <md-button class="md-icon-button" ng-click="${connectorVar}.toggleCard()">
                <md-tooltip md-direction="top">
                    <span message-key="tooltip.toggleSettings"></span>
                </md-tooltip>
                <md-icon ng-show="!${connectorVar}.expandedCard">keyboard_arrow_down</md-icon>
                <md-icon ng-show="${connectorVar}.expandedCard">keyboard_arrow_up</md-icon>
            </md-button>
        </div>
    </div>

    <md-card-content layout="column" ng-show="${connectorVar}.expandedCard">
        <form name="${connectorFormVar}">

            <div layout="row">
                <md-switch ng-model="${connectorVar}.enabled">
                    <span message-key="label.activate"></span>
                </md-switch>
            </div>

            <div layout="row">
                <md-input-container flex>
                    <label message-key="label.apiKey"></label>
                    <input type="text" ng-model="${connectorVar}.apiKey" name="apiKey" required/>
                    <div ng-messages="${connectorFormVar}.apiKey.$error" role="alert">
                        <div ng-message="required" message-key="error.apiKey.required"></div>
                    </div>
                </md-input-container>

                <div flex="5"></div>

                <md-input-container flex>
                    <label message-key="label.apiSecret"></label>
                    <input type="text" ng-model="${connectorVar}.apiSecret" name="apiSecret" required/>
                    <div ng-messages="${connectorFormVar}.apiSecret.$error" role="alert">
                        <div ng-message="required" message-key="error.apiSecret.required"></div>
                    </div>
                </md-input-container>
            </div>

            <div layout="row">
                <md-input-container flex>
                    <label message-key="label.scope"></label>
                    <input type="text" ng-model="${connectorVar}.scope" name="scope"/>
                    <div class="hint" ng-show="!${connectorFormVar}.scope.$invalid"
                         message-key="hint.scope"></div>
                    <div ng-messages="${connectorFormVar}.scope.$error" role="alert">
                        <div ng-message="required" message-key="error.scope.required"></div>
                    </div>
                </md-input-container>
            </div>

            <div layout="row">
                <md-input-container class="md-block" flex>
                    <label message-key="label.callbackURL"></label>
                    <input type="url" ng-model="${connectorVar}.callbackUrl" name="callbackUrl"/>
                    <div class="hint" ng-show="${connectorFormVar}.callbackUrl.$valid"
                         message-key="foont_stravaOAuthView.hint.callbackURL"></div>
                    <div ng-messages="${connectorFormVar}.callbackUrl.$error"
                         ng-show="${connectorFormVar}.callbackUrl.$invalid" role="alert">
                        <div ng-message="url" message-key="error.notAValidURL"></div>
                    </div>
                </md-input-container>
            </div>
        </form>

        <md-card-actions layout="row" layout-align="end center">
            <md-button class="md-accent" message-key="label.mappers"
                       ng-click="${connectorVar}.goToMappers()" ng-show="${connectorVar}.connectorHasSettings">
            </md-button>
            <md-button class="md-accent" message-key="label.save" ng-click="${connectorVar}.saveSettings()"></md-button>
        </md-card-actions>

    </md-card-content>
</md-card>
