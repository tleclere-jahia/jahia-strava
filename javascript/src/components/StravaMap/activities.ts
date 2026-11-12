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

const ACTIVITY_QUERY = `
query Activity($path: String!) {
  jcr {
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
export const loadPendingCount = async (gqlUrl: string): Promise<number | null> => {
  const response = await fetch(gqlUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query: "{ stravaPendingActivities }" }),
  });
  const data = await response.json();
  return data.data?.stravaPendingActivities ?? null;
};

/** Load one activity, such as the one the synchronization just saved */
export const loadActivity = async (gqlUrl: string, path: string): Promise<Activity> => {
  const response = await fetch(gqlUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query: ACTIVITY_QUERY, variables: { path } }),
  });
  const data = await response.json();
  const json: string | undefined = data.data?.jcr?.nodeByPath?.json?.value;
  if (!json) {
    throw new Error(data.errors?.[0]?.message ?? `No activity at ${path}`);
  }
  return JSON.parse(json) as Activity;
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
