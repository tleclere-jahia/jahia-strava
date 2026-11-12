package org.foo.modules.jahia.strava.actions;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import graphql.schema.DataFetchingEnvironment;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.util.ContextUtil;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public class StravaPendingActivitiesExtension {
    private static final Logger logger = LoggerFactory.getLogger(StravaPendingActivitiesExtension.class);

    private StravaPendingActivitiesExtension() {
        throw new IllegalStateException("Extension class are fully static and must not be instantiated");
    }

    @GraphQLField
    @GraphQLDescription("Number of the Strava activities of the current user after the last synchronization that are not synchronized yet; null without a Strava session or when Strava refused the request")
    public static Integer stravaPendingActivities(DataFetchingEnvironment environment) {
        HttpServletRequest request = ContextUtil.getHttpServletRequest(environment.getGraphQlContext());
        HttpSession session = request != null ? request.getSession(false) : null;
        Map<String, Object> tokenData = session != null ? (Map<String, Object>) session.getAttribute(JahiaOAuthConstants.TOKEN_DATA) : null;
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (tokenData == null || user == null) {
            return null;
        }
        StravaClient stravaClient = BundleUtils.getOsgiService(StravaClient.class, null);
        String accessToken = (String) tokenData.get(JahiaOAuthConstants.ACCESS_TOKEN);
        try {
            return BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, systemSession ->
                    systemSession.nodeExists(user.getLocalPath()) ? PendingActivities.count(stravaClient, systemSession.getNode(user.getLocalPath()), accessToken) : null);
        } catch (RepositoryException e) {
            logger.error("Cannot count the pending activities of {}", user.getLocalPath(), e);
            return null;
        }
    }
}
