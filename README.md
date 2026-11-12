# jahia-strava

Strava integration for Jahia 8.2, split in two artifacts:

| Artifact | Type | Content |
|---|---|---|
| `jahia-strava` (root, Maven) | Java OSGi bundle | OAuth glue (token storage, refresh, `strava-users` membership), Strava API client, `syncMe` action, synchronization background job, GraphQL subscription `subscribeToStravaSynchronization`, `foont:stravaActivity` / `foont:stravaMap` node types |
| `jahia-strava-js` (`javascript/`, Yarn) | [Jahia JavaScript module](https://academy.jahia.com/tutorials-get-started/front-end-developer/introduction) | React server view for `foont:stravaMap` + hydrated island (Leaflet map, marker cluster, GraphQL pagination, WebSocket synchronization status) |

The OAuth dance itself is delegated to [jahia-oauth-oidc](https://github.com/Jahia/jahia-oauth-oidc) (`.oidc-connect.do` / `.oidc-callback.do`): this module only keeps the `ConnectorResultProcessor` and login listener that persist the Strava token data on the user node and refresh it.

## Architecture

```
Browser          jahia-strava-js (React)         jahia-strava (Java)
  │  GraphQL /modules/graphql ──────────────► activities (jsonValue)
  │  WebSocket /modules/graphqlws ──────────► subscribeToStravaSynchronization
  │  GET .syncMe.do ─────────────────────────► SyncMeAction → SyncBackgroundJob → Strava API
```

- `javascript/src/components/StravaMap/default.server.tsx`: server-rendered view (login link, greeting, cache dependencies, island wiring)
- `javascript/src/components/StravaMap/StravaMap.client.tsx`: hydrated island (loading/error ribbons, sync link)
- `javascript/src/components/StravaMap/activities.ts`: paginated GraphQL loading of activities
- `javascript/src/components/StravaMap/sync.ts`: GraphQL WebSocket subscription
- `javascript/src/components/StravaMap/map.ts`: Leaflet map, polyline decoding, popups

## Development

Prerequisites: Java 11 + Maven (Java bundle), Node 22+ with Yarn 4 (`corepack enable yarn`), Docker.

```bash
# Start a local Jahia (installs javascript-modules-engine, jahia-authentication,
# jahia-oauth, jahia-oauth-oidc through provisioning)
docker compose up -d

# Build & deploy the Java bundle
mvn clean install

# Build & deploy the JavaScript module (deploys to http://localhost:8080, root/root1234,
# see javascript/.env)
cd javascript
yarn install
yarn build          # one-shot build + package (dist/package.tgz)
yarn dev            # watch mode, packages and deploys on every build
yarn deploy         # deploy dist/package.tgz to the running Jahia
```

Create a site, drop a `My Strava map` content on a page, connect with Strava (OIDC connector) and open the page in live/preview mode.

## Deployment to a server

```bash
mvn clean install                    # produces target/jahia-strava-1.0.0-SNAPSHOT.jar
cd javascript && yarn build          # produces dist/package.tgz
```

Install both artifacts through the Jahia UI (Server settings > Modules) or the provisioning API. `jahia-strava-js` declares dependencies on `javascript-modules-engine` and `jahia-strava`, so install it last (or let provisioning resolve the order).

## Versioning and release

Both artifacts carry the same version, and `pom.xml` is the reference. Jahia records the version of `jahia-strava-js` from `javascript/package.json`, so that file follows the pom:

- `mvn release:prepare` rewrites `javascript/package.json` with the release version before the tag, and with the next `-SNAPSHOT` after it, through the `release-prepare` profile (`javascript/sync-version.js`), and commits it beside the pom.
- After changing the version of the pom by hand, run `mvn -P release-prepare validate` to copy it to `javascript/package.json`.

A new major version also moves the range `jahia-strava=[1,2)` of `module-dependencies` in `javascript/package.json`.
