package org.foo.modules.jahia.strava.actions;

import org.jahia.api.settings.SettingsBean;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.bin.Render;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(service = Action.class)
public class SyncMeAction extends Action {
    private static final Logger logger = LoggerFactory.getLogger(SyncMeAction.class);

    @Reference
    private SchedulerService schedulerService;
    @Reference
    private SettingsBean settingsBean;

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
                if (!schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty() && settingsBean.isProcessingServer()) {
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
        Map<String, Object> tokenData = (Map<String, Object>) renderContext.getRequest().getSession(false).getAttribute(JahiaOAuthConstants.TOKEN_DATA);
        if (tokenData != null) {
            JobDetail jobDetail = BackgroundJob.createJahiaJob(SyncBackgroundJob.class.getName(), SyncBackgroundJob.class);
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put(BackgroundJob.JOB_USERKEY, jcrSessionWrapper.getUser().getLocalPath());
            jobDataMap.put(JahiaOAuthConstants.ACCESS_TOKEN, (String) tokenData.get(JahiaOAuthConstants.ACCESS_TOKEN));
            jobDetail.setJobDataMap(jobDataMap);
            try {
                if (settingsBean.isProcessingServer()) {
                    schedulerService.getScheduler().scheduleJob(jobDetail, new SimpleTrigger(SyncBackgroundJob.class.getSimpleName() + "_trigger", jobDetail.getGroup()));
                    jobDetails.add(jobDetail);
                }
                return new ActionResult(HttpServletResponse.SC_OK, resource.getNode().getPath());
            } catch (SchedulerException e) {
                logger.error("", e);
            }
        }
        return ActionResult.BAD_REQUEST;
    }
}
