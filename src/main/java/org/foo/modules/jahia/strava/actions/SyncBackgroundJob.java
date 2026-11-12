package org.foo.modules.jahia.strava.actions;

import org.foo.modules.jahia.strava.StravaApi;
import org.foo.modules.jahia.strava.client.Activity;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;
import java.time.LocalDateTime;
import java.util.Calendar;

public class SyncBackgroundJob extends BackgroundJob {
    private static final Logger logger = LoggerFactory.getLogger(SyncBackgroundJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) {
        StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.STARTED, "Start synchronization...");
        try {
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

            try {
                if (!BundleUtils.getOsgiService(JCRTemplate.class, null).doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                    if (!session.nodeExists(userPath)) {
                        logger.error("User {} not found", userPath);
                        return false;
                    }
                    JCRNodeWrapper userNode = session.getNode(userPath);
                    LocalDateTime startDate = null;
                    if (userNode.hasProperty(StravaApi.LAST_STRAVA_SYNC)) {
                        Calendar lastStravaSync = userNode.getProperty(StravaApi.LAST_STRAVA_SYNC).getDate();
                        startDate = LocalDateTime.ofInstant(lastStravaSync.toInstant(), lastStravaSync.getTimeZone().toZoneId());
                    }
                    getMyActivities(stravaClient, userNode, accessToken, startDate, 1);

                    RowIterator it = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + StravaApi.STRAVA_ACTIVITY + "] WHERE ISDESCENDANTNODE('" + userNode.getPath() + "') ORDER BY [" + StravaApi.STRAVA_ACTIVITY_DATE + "] DESC", Query.JCR_SQL2).execute().getRows();
                    if (it.hasNext()) {
                        userNode.setProperty(StravaApi.LAST_STRAVA_SYNC, it.nextRow().getNode().getProperty(StravaApi.STRAVA_ACTIVITY_DATE).getDate());
                        userNode.saveSession();
                    }
                    return true;
                })) {
                    throw new RuntimeException();
                }
            } catch (RepositoryException e) {
                logger.error("", e);
                throw new RuntimeException(e);
            }
            logger.info(">>> END Sync me");
        } finally {
            StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.FINISHED, "...End synchronization");
        }
    }

    private static void getMyActivities(StravaClient stravaClient, JCRNodeWrapper userNode, String accessToken, LocalDateTime startDate, int page) {
        StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, "Get my activities...");
        stravaClient.getActivities(accessToken, startDate, page).ifPresent(data -> {
            StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, "Processing " + data.size() + " activities");
            logger.debug("{} activities found", data.size());
            data.forEach(activity -> stravaClient.getActivity(accessToken, activity.getId()).ifPresent(a -> createJCRActivity(userNode, a)));
            StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, data.size() + " activities found");
            if (!data.isEmpty()) {
                getMyActivities(stravaClient, userNode, accessToken, startDate, page + 1);
            }
        });
    }

    private static JCRNodeWrapper getOrCreateActivityNode(JCRNodeWrapper rootNode, long activityId) throws RepositoryException {
        String nodename = "activity-" + activityId;
        JCRNodeIteratorWrapper it = rootNode.getSession().getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + StravaApi.STRAVA_ACTIVITY + "] WHERE ISDESCENDANTNODE('" + rootNode.getPath() + "') AND localname() = '" + nodename + "'", Query.JCR_SQL2).execute().getNodes();
        if (it.hasNext()) {
            logger.debug("Activity {} already exists", activityId);
            return (JCRNodeWrapper) it.nextNode();
        }
        logger.debug("Create activity {}", activityId);
        return rootNode.addNode(nodename, StravaApi.STRAVA_ACTIVITY);
    }

    public static boolean checkMyStravaProfileActivitiesFolder(JCRNodeWrapper jcrUserNode) {
        try {
            JCRNodeWrapper jcrNodeWrapper;
            if (!jcrUserNode.hasNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER)) {
                jcrNodeWrapper = jcrUserNode.addNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER, StravaApi.STRAVA_ACTIVITIES);
            } else {
                jcrNodeWrapper = jcrUserNode.getNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
            }
            JCRAutoSplitUtils.enableAutoSplitting(jcrNodeWrapper, "date," + StravaApi.STRAVA_ACTIVITY_DATE + ",yyyy;date," + StravaApi.STRAVA_ACTIVITY_DATE + ",MM", StravaApi.STRAVA_ACTIVITIES);
            jcrNodeWrapper.saveSession();
            return true;
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return false;
    }

    private static void createJCRActivity(JCRNodeWrapper userNode, Activity activity) {
        try {
            JCRNodeWrapper node = userNode;
            if (!checkMyStravaProfileActivitiesFolder(userNode)) {
                logger.error("User {} has not activities folder", userNode.getPath());
            } else {
                String date = StravaApi.LAST_STRAVA_SYNC_FORMATTER.format(activity.getStartDate());
                StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, "Sync my activity: " + date);
                node = node.getNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
                JCRNodeWrapper activityNode = getOrCreateActivityNode(node, activity.getId());
                activityNode.setProperty(StravaApi.STRAVA_ACTIVITY_DATE, date);
                activityNode.setProperty(StravaApi.STRAVA_ACTIVITY_JSON, activity.getJson());
                activityNode.saveSession();
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }
}
