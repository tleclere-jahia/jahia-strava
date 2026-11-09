import type { Activity, StravaMapHandle } from "./map";

interface LoadAllActivitiesOptions {
  gqlUrl: string;
  language: string;
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

/**
 * Load every activity of the current user through the GraphQL API, page by page,
 * and add them to the map.
 */
export const loadAllActivities = async ({
  gqlUrl,
  language,
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

  map.addActivities(activities, language);
  map.fitToActivities();
};
