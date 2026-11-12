package org.foo.modules.jahia.strava.actions;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.bin.Render;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.scheduler.SchedulerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(service = Action.class)
public class SyncMeAction extends Action {
    private static final Logger logger = LoggerFactory.getLogger(SyncMeAction.class);
    /** HttpServletResponse of the Servlet API in use names no constant for 429 */
    private static final int SC_TOO_MANY_REQUESTS = 429;

    @Reference
    private SchedulerService schedulerService;

    private final Set<JobDetail> jobDetails;

    public SyncMeAction() {
        setName("syncMe");
        setRequiredMethods(Render.METHOD_GET);
        jobDetails = new HashSet<>();
    }

    @Deactivate
    private void onDeactivate() {
        jobDetails.forEach(jobDetail -> {
            try {
                if (!schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty()) {
                    schedulerService.getAllJobs(jobDetail.getGroup()).forEach(detail -> {
                        try {
                            schedulerService.getScheduler().deleteJob(detail.getName(), detail.getGroup());
                        } catch (SchedulerException e) {
                            logger.error("", e);
                        }
                    });
                }
            } catch (SchedulerException e) {
                logger.error("", e);
            }
        });
    }

    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource, JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> parameters, URLResolver urlResolver) {
        logger.info("Sync me");
        if (PendingActivities.retryAfterSeconds() > 0) {
            // Strava refused a request less than 15 minutes ago: a new synchronization would be refused too
            logger.info("Sync me refused: Strava rate limit, retry in {}s", PendingActivities.retryAfterSeconds());
            return new ActionResult(SC_TOO_MANY_REQUESTS);
        }
        Map<String, Object> tokenData = (Map<String, Object>) renderContext.getRequest().getSession(false).getAttribute(JahiaOAuthConstants.TOKEN_DATA);
        if (tokenData != null) {
            try {
                jobDetails.add(SyncBackgroundJob.schedule(schedulerService, jcrSessionWrapper.getUser().getLocalPath(), (String) tokenData.get(JahiaOAuthConstants.ACCESS_TOKEN)));
                return ActionResult.OK_JSON;
            } catch (SchedulerException e) {
                logger.error("", e);
            }
        }
        return ActionResult.BAD_REQUEST;
    }
}
