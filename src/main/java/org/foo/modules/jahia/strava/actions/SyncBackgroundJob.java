package org.foo.modules.jahia.strava.actions;

import org.foo.modules.jahia.strava.client.Activity;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.foo.modules.jahia.strava.oauth.StravaApi20;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;

public class SyncBackgroundJob extends BackgroundJob {
    private static final Logger logger = LoggerFactory.getLogger(SyncBackgroundJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) {
        String userPath = jobExecutionContext.getJobDetail().getJobDataMap().getString(BackgroundJob.JOB_USERKEY);
        if (userPath == null && logger.isDebugEnabled()) {
            logger.debug("User not found in jobDataMap: {}", jobExecutionContext.getJobDetail().getJobDataMap());
            throw new RuntimeException();
        }

        String accessToken = jobExecutionContext.getJobDetail().getJobDataMap().getString(JahiaOAuthConstants.ACCESS_TOKEN);
        if (accessToken == null && logger.isDebugEnabled()) {
            logger.debug("AccessToken not found in jobDataMap: {}", jobExecutionContext.getJobDetail().getJobDataMap());
            throw new RuntimeException();
        }

        StravaClient stravaClient = BundleUtils.getOsgiService(StravaClient.class, null);
        if (stravaClient == null) {
            logger.error("Strava client not found");
            throw new RuntimeException();
        }

        logger.info("Sync me");
        try {
            if (!BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                if (!session.nodeExists(userPath)) {
                    logger.error("User {} not found", userPath);
                    return false;
                }
                JCRNodeWrapper userNode = session.getNode(userPath);
                LocalDateTime startDate = null;
                if (userNode.hasProperty(StravaApi20.LAST_STRAVA_SYNC)) {
                    Calendar lastStravaSync = userNode.getProperty(StravaApi20.LAST_STRAVA_SYNC).getDate();
                    startDate = LocalDateTime.ofInstant(lastStravaSync.toInstant(), lastStravaSync.getTimeZone().toZoneId());
                }
                getMyActivities(stravaClient, userNode, accessToken, startDate, 1);
                return true;
            })) {
                throw new RuntimeException();
            }
        } catch (RepositoryException e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }
        logger.info(">>> END Sync me");
    }

    private static void getMyActivities(StravaClient stravaClient, JCRNodeWrapper userNode, String accessToken, LocalDateTime startDate, int page) {
        stravaClient.getActivities(accessToken, startDate, page).ifPresent(data -> {
            logger.info("{} activities found", data.size());
            data.forEach(activity -> stravaClient.getActivity(accessToken, activity.getId()).ifPresent(a -> createJCRActivity(userNode, a)));
            try {
                userNode.setProperty(StravaApi20.LAST_STRAVA_SYNC, StravaApi20.LAST_STRAVA_SYNC_FORMATTER.format(LocalDateTime.now(ZoneOffset.UTC)));
                userNode.saveSession();
            } catch (RepositoryException e) {
                logger.error("", e);
            }
            if (!data.isEmpty()) {
                getMyActivities(stravaClient, userNode, accessToken, startDate, page + 1);
            }
        });
    }

    private static JCRNodeWrapper getOrCreateActivityNode(JCRNodeWrapper rootNode, long activityId) throws RepositoryException {
        String nodename = "activity-" + activityId;
        JCRNodeIteratorWrapper it = rootNode.getSession().getWorkspace().getQueryManager().createQuery("SELECT * FROM [foont:stravaActivity] WHERE ISDESCENDANTNODE('" + rootNode.getPath() + "') AND localname() = '" + nodename + "'", Query.JCR_SQL2).execute().getNodes();
        if (it.hasNext()) {
            logger.info("Activity {} already exists", activityId);
            return (JCRNodeWrapper) it.nextNode();
        }
        logger.info("Create activity {}", activityId);
        return rootNode.addNode(nodename, "foont:stravaActivity");
    }

    private static void createJCRActivity(JCRNodeWrapper userNode, Activity activity) {
        try {
            JCRNodeWrapper node = userNode;
            if (!node.hasNode(StravaApi20.MY_STRAVA_PROFILE_ACTIVITES_FOLDER)) {
                logger.error("User {} has not activities folder", userNode.getPath());
            } else {
                node = node.getNode(StravaApi20.MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
                JCRNodeWrapper activityNode = getOrCreateActivityNode(node, activity.getId());
                activityNode.setProperty(StravaApi20.STRAVA_ACTIVITY_DATE, StravaApi20.LAST_STRAVA_SYNC_FORMATTER.format(activity.getStartDate()));
                activityNode.setProperty(StravaApi20.STRAVA_ACTIVITY_JSON, activity.getJson());
                activityNode.saveSession();
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }
}
