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
import org.jahia.services.scheduler.SchedulerService;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;
import java.util.List;
import java.util.Optional;

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
                    getMyActivities(stravaClient, userNode, accessToken);
                    PendingActivities.forget(userNode.getPath());

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

    /**
     * Schedules the synchronization of a user now. Each job gets a trigger of its own, so that a
     * synchronization asked at login and one asked from the map do not collide.
     */
    public static JobDetail schedule(SchedulerService schedulerService, String userPath, String accessToken) throws SchedulerException {
        JobDetail jobDetail = BackgroundJob.createJahiaJob(SyncBackgroundJob.class.getName(), SyncBackgroundJob.class);
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(BackgroundJob.JOB_USERKEY, userPath);
        jobDataMap.put(JahiaOAuthConstants.ACCESS_TOKEN, accessToken);
        jobDetail.setJobDataMap(jobDataMap);
        schedulerService.getScheduler().scheduleJob(jobDetail, new SimpleTrigger(jobDetail.getName() + "_trigger", jobDetail.getGroup()));
        return jobDetail;
    }

    /**
     * Checks which activities Strava holds after LAST_STRAVA_SYNC and the JCR lacks, then loads
     * the details of those only. A request that Strava refuses, most often with a 429 once its rate
     * limit is reached, stops the synchronization: the next requests would be refused too.
     * HttpClientService logs the status and returns no response.
     */
    private static void getMyActivities(StravaClient stravaClient, JCRNodeWrapper userNode, String accessToken) throws RepositoryException {
        StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, "Get my activities...");
        Optional<List<Activity>> pending = PendingActivities.find(stravaClient, userNode, accessToken);
        if (!pending.isPresent()) {
            notifyStravaError("Strava refused the list of activities");
            return;
        }
        StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, pending.get().size() + " activities to synchronize");
        logger.debug("{} activities to synchronize", pending.get().size());
        for (Activity summary : pending.get()) {
            Optional<Activity> activity = stravaClient.getActivity(accessToken, summary.getId());
            if (!activity.isPresent()) {
                notifyStravaError("Strava refused the activity " + summary.getId());
                return;
            }
            createJCRActivity(userNode, activity.get());
        }
    }

    private static void notifyStravaError(String message) {
        logger.warn("Synchronization stopped: {}", message);
        StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.ERROR, message);
    }

    private static JCRNodeWrapper findActivityNode(JCRNodeWrapper rootNode, long activityId) throws RepositoryException {
        JCRNodeIteratorWrapper it = rootNode.getSession().getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + StravaApi.STRAVA_ACTIVITY + "] WHERE ISDESCENDANTNODE('" + rootNode.getPath() + "') AND localname() = 'activity-" + activityId + "'", Query.JCR_SQL2).execute().getNodes();
        return it.hasNext() ? (JCRNodeWrapper) it.nextNode() : null;
    }

    private static JCRNodeWrapper getOrCreateActivityNode(JCRNodeWrapper rootNode, long activityId) throws RepositoryException {
        JCRNodeWrapper activityNode = findActivityNode(rootNode, activityId);
        if (activityNode != null) {
            logger.debug("Activity {} already exists", activityId);
            return activityNode;
        }
        logger.debug("Create activity {}", activityId);
        return rootNode.addNode("activity-" + activityId, StravaApi.STRAVA_ACTIVITY);
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
                node = node.getNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
                JCRNodeWrapper activityNode = getOrCreateActivityNode(node, activity.getId());
                activityNode.setProperty(StravaApi.STRAVA_ACTIVITY_DATE, date);
                activityNode.setProperty(StravaApi.STRAVA_ACTIVITY_JSON, activity.getJson());
                activityNode.saveSession();
                // the save moves a new activity to its year/month split folder: read its path again
                JCRNodeWrapper savedNode = findActivityNode(node, activity.getId());
                StravaSynchronizationSubscriptionExtension.notifyListeners(StravaSynchronizationSubscriptionExtension.GqlStravaSynchronization.StravaSynchronizationStatus.IN_PROGRESS, "Sync my activity: " + date, savedNode != null ? savedNode.getPath() : null);
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }
}
