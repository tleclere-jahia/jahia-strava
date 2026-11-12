import type { Activity, PopupLabels, StravaMapHandle } from "./map";

interface LoadAllActivitiesOptions {
  gqlUrl: string;
  language: string;
  labels: PopupLabels;
  map: StravaMapHandle;
  onTotalCount: (count: number) => void;
}

interface DescendantsPage {
  pageInfo: {
    totalCount: number;
    hasNextPage: boolean;
    endCursor: string | null;
  };
  nodes: {
    path: string;
    json: { value: string } | null;
  }[];
}

const ACTIVITIES_QUERY = `
query Activities($after: String) {
  currentUser {
    node {
      children(names: ["strava-activities"]) {
        nodes {
          descendants(
            typesFilter: {types: ["foont:stravaActivity"]}
            maxDepth: 3
            first: 100
            after: $after
          ) {
            pageInfo {
              totalCount
              hasNextPage
              endCursor
            }
            nodes {
              path
              json: property(name: "jsonValue") {
                value
              }
            }
          }
        }
      }
    }
  }
}`;

// LIVE: a visitor of the live site cannot read its own user node in the edit workspace, and the
// activities reach LIVE through jmix:autoPublish
const ACTIVITY_QUERY = `
query Activity($path: String!) {
  jcr(workspace: LIVE) {
    nodeByPath(path: $path) {
      json: property(name: "jsonValue") {
        value
      }
    }
  }
}`;

/**
 * The number of Strava activities after the last synchronization that are not synchronized yet,
 * or null when unknown (no Strava session, or Strava refused the request)
 */
export const loadPendingCount = async (
  gqlUrl: string,
): Promise<{ count: number | null; retryAfter: number | null }> => {
  const response = await fetch(gqlUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    // stravaRetryAfter comes second: a refusal while counting starts the wait it reports
    body: JSON.stringify({ query: "{ stravaPendingActivities stravaRetryAfter }" }),
  });
  const data = await response.json();
  return {
    count: data.data?.stravaPendingActivities ?? null,
    retryAfter: data.data?.stravaRetryAfter ?? null,
  };
};

const ACTIVITY_ATTEMPTS = 4;
const ACTIVITY_RETRY_MS = 500;

/**
 * Load one activity, such as the one the synchronization just saved. Its publication to LIVE may
 * lag behind the notification of the job, so a missing activity is asked for again a few times.
 */
export const loadActivity = async (gqlUrl: string, path: string): Promise<Activity> => {
  let lastError = `No activity at ${path}`;
  for (let attempt = 1; attempt <= ACTIVITY_ATTEMPTS; attempt++) {
    const response = await fetch(gqlUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query: ACTIVITY_QUERY, variables: { path } }),
    });
    const data = await response.json();
    const json: string | undefined = data.data?.jcr?.nodeByPath?.json?.value;
    if (json) {
      return JSON.parse(json) as Activity;
    }
    lastError = data.errors?.[0]?.message ?? lastError;
    await new Promise((resolve) => setTimeout(resolve, ACTIVITY_RETRY_MS * attempt));
  }
  throw new Error(lastError);
};

/**
 * Load every activity of the current user through the GraphQL API, page by page,
 * and add them to the map.
 */
export const loadAllActivities = async ({
  gqlUrl,
  language,
  labels,
  map,
  onTotalCount,
}: LoadAllActivitiesOptions): Promise<void> => {
  const activities: Activity[] = [];
  let after: string | null = null;

  for (;;) {
    const response = await fetch(gqlUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: ACTIVITIES_QUERY,
        variables: { after },
      }),
    });
    const data = await response.json();
    const page: DescendantsPage | undefined =
      data.data?.currentUser?.node?.children?.nodes?.[0]?.descendants;
    if (!page) {
      throw new Error(data.errors?.[0]?.message ?? "Unexpected GraphQL response");
    }

    onTotalCount(page.pageInfo.totalCount);

    for (const node of page.nodes) {
      if (!node.json?.value) continue;
      try {
        activities.push(JSON.parse(node.json.value) as Activity);
      } catch (error) {
        console.warn(`Skipping unparsable activity ${node.path}`, error);
      }
    }

    if (!page.pageInfo.hasNextPage) break;
    after = page.pageInfo.endCursor;
  }

  map.addActivities(activities, language, labels);
  map.fitToActivities();
};
