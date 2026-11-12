package org.foo.modules.jahia.strava.actions;

import org.foo.modules.jahia.strava.StravaApi;
import org.foo.modules.jahia.strava.client.Activity;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Strava activities started after LAST_STRAVA_SYNC that the JCR does not hold yet. Listing
 * them costs one request to Strava for each 200 activities, against one request for each
 * activity to load its details.
 */
public final class PendingActivities {
    /** The largest page the Strava API returns */
    private static final int PAGE_SIZE = 200;
    private static final long COUNT_CACHE_MILLIS = 5 * 60 * 1000L;
    private static final String ACTIVITY_PREFIX = "activity-";

    /** user path → [time of the count, count], so that a page view does not call Strava each time */
    private static final Map<String, long[]> counts = new ConcurrentHashMap<>();

    private PendingActivities() {
        throw new IllegalStateException("Utility class");
    }

    /** The date after which Strava holds activities that the last synchronization did not see */
    public static LocalDateTime lastSync(JCRNodeWrapper userNode) throws RepositoryException {
        if (!userNode.hasProperty(StravaApi.LAST_STRAVA_SYNC)) {
            return null;
        }
        Calendar lastStravaSync = userNode.getProperty(StravaApi.LAST_STRAVA_SYNC).getDate();
        return LocalDateTime.ofInstant(lastStravaSync.toInstant(), ZoneOffset.UTC);
    }

    /**
     * The summaries of the pending activities, oldest first, or empty when Strava refused a
     * request.
     */
    public static Optional<List<Activity>> find(StravaClient stravaClient, JCRNodeWrapper userNode, String accessToken) throws RepositoryException {
        LocalDateTime after = lastSync(userNode);
        Set<Long> known = knownIds(userNode, after);
        List<Activity> pending = new ArrayList<>();
        for (int page = 1; ; page++) {
            Optional<List<Activity>> summaries = stravaClient.getActivities(accessToken, after, page, PAGE_SIZE);
            if (!summaries.isPresent()) {
                return Optional.empty();
            }
            for (Activity summary : summaries.get()) {
                if (!known.contains(summary.getId())) {
                    pending.add(summary);
                }
            }
            if (summaries.get().size() < PAGE_SIZE) {
                counts.put(userNode.getPath(), new long[]{System.currentTimeMillis(), pending.size()});
                return Optional.of(pending);
            }
        }
    }

    /** The number of pending activities, counted again at most every 5 minutes; null when unknown */
    public static Integer count(StravaClient stravaClient, JCRNodeWrapper userNode, String accessToken) throws RepositoryException {
        long[] cached = counts.get(userNode.getPath());
        if (cached != null && System.currentTimeMillis() - cached[0] < COUNT_CACHE_MILLIS) {
            return (int) cached[1];
        }
        return find(stravaClient, userNode, accessToken).map(List::size).orElse(null);
    }

    /** Forget the cached count of a user, once a synchronization saved activities */
    public static void forget(String userPath) {
        counts.remove(userPath);
    }

    private static Set<Long> knownIds(JCRNodeWrapper userNode, LocalDateTime after) throws RepositoryException {
        Set<Long> ids = new HashSet<>();
        if (!userNode.hasNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER)) {
            return ids;
        }
        String folderPath = userNode.getNode(StravaApi.MY_STRAVA_PROFILE_ACTIVITES_FOLDER).getPath();
        String query = "SELECT * FROM [" + StravaApi.STRAVA_ACTIVITY + "] WHERE ISDESCENDANTNODE('" + folderPath + "')"
                + (after != null ? " AND [" + StravaApi.STRAVA_ACTIVITY_DATE + "] > CAST('" + StravaApi.LAST_STRAVA_SYNC_FORMATTER.format(after) + "' AS DATE)" : "");
        JCRNodeIteratorWrapper it = userNode.getSession().getWorkspace().getQueryManager().createQuery(query, Query.JCR_SQL2).execute().getNodes();
        while (it.hasNext()) {
            String name = it.nextNode().getName();
            if (name.startsWith(ACTIVITY_PREFIX)) {
                try {
                    ids.add(Long.parseLong(name.substring(ACTIVITY_PREFIX.length())));
                } catch (NumberFormatException e) {
                    // a node that the synchronization did not name: not an activity of Strava
                }
            }
        }
        return ids;
    }
}
