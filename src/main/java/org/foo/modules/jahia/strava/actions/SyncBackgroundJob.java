package org.foo.modules.jahia.strava.actions;

import org.foo.modules.jahia.strava.client.Activity;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.foo.modules.jahia.strava.oauth.StravaLoginListener;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SyncBackgroundJob extends BackgroundJob {
    private static final Logger logger = LoggerFactory.getLogger(SyncBackgroundJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) {
        String userPath = jobExecutionContext.getJobDetail().getJobDataMap().getString(BackgroundJob.JOB_USERKEY);
        if (userPath == null && logger.isDebugEnabled()) {
            logger.debug("User not found in jobDataMap: {}", jobExecutionContext.getJobDetail().getJobDataMap());
            return;
        }

        String accessToken = jobExecutionContext.getJobDetail().getJobDataMap().getString(JahiaOAuthConstants.ACCESS_TOKEN);
        if (accessToken == null && logger.isDebugEnabled()) {
            logger.debug("AccessToken not found in jobDataMap: {}", jobExecutionContext.getJobDetail().getJobDataMap());
            return;
        }

        logger.info("Sync me");
        getMyActivities(userPath, accessToken, null, 1);
        logger.info(">>> END Sync me");
    }

    private void getMyActivities(String userPath, String accessToken, LocalDate startDate, int page) {
        StravaClient stravaClient = BundleUtils.getOsgiService(StravaClient.class, null);
        if (stravaClient == null) {
            logger.error("Strava client not found");
            return;
        }

        stravaClient.getActivities(accessToken, startDate, page).ifPresent(data -> {
            logger.info("{} activities found", data.size());
            data.forEach(activity -> stravaClient.getActivity(accessToken, activity.getId()).ifPresent(a -> createJCRActivity(userPath, a)));
            if (!data.isEmpty()) {
                getMyActivities(userPath, accessToken, startDate, page + 1);
                /* try {
                    Thread.sleep(60 * 1000);
                    getMyActivities(userPath, accessToken, startDate, page + 1);
                } catch (InterruptedException e) {
                    logger.error("", e);
                } */
            }
        });
    }

    private JCRNodeWrapper getOrCreateActivityNode(JCRNodeWrapper rootNode, long activityId) throws RepositoryException {
        String nodename = "activity-" + activityId;
        JCRNodeIteratorWrapper it = rootNode.getSession().getWorkspace().getQueryManager().createQuery("SELECT * FROM [foont:stravaActivity] WHERE ISDESCENDANTNODE('" + rootNode.getPath() + "') AND localname() = '" + nodename + "'", Query.JCR_SQL2).execute().getNodes();
        if (it.hasNext()) {
            logger.info("Activity {} already exists", activityId);
            return (JCRNodeWrapper) it.nextNode();
        }
        logger.info("Create activity {}", activityId);
        return rootNode.addNode(nodename, "foont:stravaActivity");
    }

    private void createJCRActivity(String userPath, Activity activity) {
        JCRTemplate jcrTemplate = BundleUtils.getOsgiService(JCRTemplate.class, null);
        try {
            jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                if (!session.nodeExists(userPath)) {
                    logger.error("User {} not found", userPath);
                    return false;
                }
                JCRNodeWrapper node = session.getNode(userPath);
                if (!node.hasNode(StravaLoginListener.MY_STRAVA_PROFILE_ACTIVITES_FOLDER)) {
                    logger.error("User {} has not activities folder", userPath);
                    return false;
                }

                node = node.getNode(StravaLoginListener.MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
                JCRNodeWrapper activityNode = getOrCreateActivityNode(node, activity.getId());
                activityNode.setProperty(StravaLoginListener.STRAVA_ACTIVITY_DATE, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(activity.getStartDate()));
                activityNode.setProperty(StravaLoginListener.STRAVA_ACTIVITY_JSON, activity.getJson());
                activityNode.saveSession();
                return true;
            });
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }
}
