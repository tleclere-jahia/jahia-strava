package org.foo.modules.jahia.strava.profile;

import org.foo.modules.jahia.strava.StravaApi;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keeps the map of a user in line with their Strava profile: a foont:stravaProfileMap under their node,
 * that only they can read, served at /strava/&lt;login&gt; by {@link StravaVanityFilter}. It is
 * created or updated once stravaUrl is set, and removed once stravaUrl is emptied or gone, the
 * mixin removed included. The map carries jmix:autoPublish, so LIVE follows each save.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class StravaProfileListener extends DefaultEventListener {
    private static final Logger logger = LoggerFactory.getLogger(StravaProfileListener.class);

    @Reference
    private JCRTemplate jcrTemplate;
    @Reference
    private JahiaUserManagerService userManagerService;

    public StravaProfileListener() {
        setWorkspace(Constants.EDIT_WORKSPACE);
    }

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED;
    }

    @Override
    public String getPath() {
        return "/users";
    }

    @Override
    public void onEvent(EventIterator events) {
        Set<String> userPaths = new LinkedHashSet<>();
        while (events.hasNext()) {
            try {
                String path = events.nextEvent().getPath();
                if (path.endsWith("/" + StravaApi.STRAVA_URL)) {
                    userPaths.add(path.substring(0, path.lastIndexOf('/')));
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
        for (String userPath : userPaths) {
            // system rights, but as the user of the profile: the map and its publication are
            // recorded as theirs (jcr:createdBy, jcr:lastModifiedBy, j:lastPublishedBy)
            JCRUserNode profileUser = userManagerService.lookupUserByPath(userPath);
            if (profileUser == null) {
                continue;
            }
            JahiaUser user = profileUser.getJahiaUser();
            try {
                jcrTemplate.doExecuteWithSystemSessionAsUser(user, Constants.EDIT_WORKSPACE, null, session -> {
                    if (session.nodeExists(userPath)) {
                        sync(session.getNode(userPath));
                    }
                    return null;
                });
            } catch (RepositoryException e) {
                logger.error("Cannot update the Strava map of {}", userPath, e);
            }
        }
    }

    private static void sync(JCRNodeWrapper userNode) throws RepositoryException {
        if (hasStravaUrl(userNode)) {
            createOrUpdate(userNode);
        } else if (userNode.hasNode(StravaApi.STRAVA_MAP_NODE)) {
            userNode.getNode(StravaApi.STRAVA_MAP_NODE).remove();
            userNode.saveSession();
            logger.info("Strava map of {} removed: no Strava URL in the profile", userNode.getName());
        }
    }

    private static boolean hasStravaUrl(JCRNodeWrapper userNode) throws RepositoryException {
        return userNode.isNodeType(StravaApi.STRAVA_PROFILE) && userNode.hasProperty(StravaApi.STRAVA_URL)
                && !userNode.getProperty(StravaApi.STRAVA_URL).getString().trim().isEmpty();
    }

    private static void createOrUpdate(JCRNodeWrapper userNode) throws RepositoryException {
        String login = userNode.getName();
        JCRNodeWrapper map = userNode.hasNode(StravaApi.STRAVA_MAP_NODE)
                ? userNode.getNode(StravaApi.STRAVA_MAP_NODE)
                : userNode.addNode(StravaApi.STRAVA_MAP_NODE, StravaApi.STRAVA_MAP);
        if (!map.isNodeType(StravaApi.AUTO_PUBLISH)) {
            map.addMixin(StravaApi.AUTO_PUBLISH);
        }
        // the map shows the activities of whoever reads it: only its owner may
        map.setAclInheritanceBreak(true);
        map.grantRoles("u:" + login, Collections.singleton("owner"));
        map.saveSession();
        logger.info("Strava map of {} ready at {}{}", login, StravaApi.STRAVA_VANITY_PREFIX, login);
    }
}
