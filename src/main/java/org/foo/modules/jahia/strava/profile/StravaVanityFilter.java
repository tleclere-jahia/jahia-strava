package org.foo.modules.jahia.strava.profile;

import org.foo.modules.jahia.strava.StravaApi;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.bin.filters.AbstractServletFilter;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Serves /strava/&lt;login&gt;, the vanity URL of the map of a user. A logged-in visitor gets the
 * map rendered from LIVE, which its ACL keeps for its owner. A guest goes through the Strava login
 * of jahia-oauth-oidc, which brings them back here through the cookie that its returnMode=cookie
 * reads.
 */
@Component(service = AbstractServletFilter.class, immediate = true)
public class StravaVanityFilter extends AbstractServletFilter {
    private static final Logger logger = LoggerFactory.getLogger(StravaVanityFilter.class);

    /** The cookie that OidcConnector.returnCookie names, for OidcConnector.returnMode=cookie */
    public static final String RETURN_COOKIE = "strava-return";
    private static final String OIDC_CONNECTOR = "OidcConnector";
    /** The session attribute that holds the Jahia user, as org.jahia.params.ProcessingContext names it */
    private static final String SESSION_USER = "org.jahia.usermanager.jahiauser";
    private static final String DEFAULT_LANGUAGE = "en";

    @Reference
    private JahiaUserManagerService userManagerService;
    @Reference
    private JahiaSitesService sitesService;
    @Reference
    private SettingsService settingsService;
    @Reference
    private JCRTemplate jcrTemplate;

    @Activate
    public void activate() {
        setUrlPatterns(new String[]{StravaApi.STRAVA_VANITY_PREFIX + "*"});
        setMatchAllUrls(false);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to initialize
    }

    @Override
    public void destroy() {
        // nothing to release
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        if (!uri.startsWith(StravaApi.STRAVA_VANITY_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String login = uri.substring(StravaApi.STRAVA_VANITY_PREFIX.length()).replaceAll("/+$", "");
        JCRUserNode userNode = login.isEmpty() || login.contains("/") ? null : userManagerService.lookupUser(login);
        String mapPath = userNode != null ? userNode.getPath() + "/" + StravaApi.STRAVA_MAP_NODE : null;
        if (mapPath == null || !existsInLive(mapPath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        HttpSession session = request.getSession(false);
        JahiaUser sessionUser = session != null ? (JahiaUser) session.getAttribute(SESSION_USER) : null;
        if (sessionUser == null || org.jahia.services.usermanager.JahiaUserManagerService.isGuest(sessionUser)) {
            String connectUrl = oidcConnectUrl(request);
            if (connectUrl == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            Cookie returnCookie = new Cookie(RETURN_COOKIE, request.getContextPath() + uri);
            returnCookie.setPath("/");
            returnCookie.setHttpOnly(true);
            response.addCookie(returnCookie);
            response.sendRedirect(connectUrl);
            return;
        }

        request.getRequestDispatcher("/cms/render/" + Constants.LIVE_WORKSPACE + "/" + language(userNode) + mapPath + ".html").forward(request, response);
    }

    private static String language(JCRUserNode userNode) {
        try {
            return userNode.hasProperty("preferredLanguage") ? userNode.getPropertyAsString("preferredLanguage") : DEFAULT_LANGUAGE;
        } catch (RepositoryException e) {
            return DEFAULT_LANGUAGE;
        }
    }

    private boolean existsInLive(String path) {
        try {
            return Boolean.TRUE.equals(jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, session -> session.nodeExists(path)));
        } catch (RepositoryException e) {
            logger.error("Cannot read {}", path, e);
            return false;
        }
    }

    /** The Strava login of the first site where the OidcConnector of jahia-oauth-oidc is enabled */
    private String oidcConnectUrl(HttpServletRequest request) {
        try {
            for (String siteKey : sitesService.getSitesNames()) {
                ConnectorConfig config = settingsService.getConnectorConfig(siteKey, OIDC_CONNECTOR);
                if (config != null && Boolean.TRUE.equals(config.getBooleanProperty("enabled"))) {
                    String homePath = jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null,
                            session -> ((JCRSiteNode) session.getNode("/sites/" + siteKey)).getHome().getPath());
                    return request.getContextPath() + "/cms/render/" + Constants.LIVE_WORKSPACE + "/" + DEFAULT_LANGUAGE + homePath + ".oidc-connect.do";
                }
            }
        } catch (RepositoryException e) {
            logger.error("Cannot find the site of the Strava login", e);
        }
        return null;
    }
}
