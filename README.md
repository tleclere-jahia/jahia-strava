# jahia-strava

Strava integration for Jahia 8.2, split in two artifacts:

| Artifact | Type | Content |
|---|---|---|
| `jahia-strava` (root, Maven) | Java OSGi bundle | OAuth glue (token storage, refresh, `strava-users` membership), Strava API client, `syncMe` action, synchronization background job, GraphQL subscription `subscribeToStravaSynchronization`, `foont:stravaActivity`, `foont:stravaActivities`, `foont:stravaProfileMap`, `foont:stravaLogin` node types and `foont:stravaProfile` mixin |
| `jahia-strava-js` (`javascript/`, Yarn) | [Jahia JavaScript module](https://academy.jahia.com/tutorials-get-started/front-end-developer/introduction) | React server view for `foont:stravaProfileMap` + hydrated island (Leaflet map, marker cluster, GraphQL pagination, WebSocket synchronization status), and the `foont:stravaLogin` button |

The OAuth dance itself is delegated to [jahia-oauth-oidc](https://github.com/Jahia/jahia-oauth-oidc) (`.oidc-connect.do` / `.oidc-callback.do`): this module only keeps the `ConnectorResultProcessor` and login listener that persist the Strava token data on the user node and refresh it.

## Architecture

```
Browser          jahia-strava-js (React)         jahia-strava (Java)
  │  GraphQL /modules/graphql ──────────────► activities (jsonValue)
  │  WebSocket /modules/graphqlws ──────────► subscribeToStravaSynchronization
  │  GET .syncMe.do ─────────────────────────► SyncMeAction → SyncBackgroundJob → Strava API
```

- `javascript/src/components/StravaProfileMap/fullpage.server.tsx`: page template of `foont:stravaProfileMap`, which `/users` lacks
- `javascript/src/components/StravaProfileMap/default.server.tsx`: server-rendered view (greeting, cache dependencies, island wiring)
- `javascript/src/components/StravaProfileMap/StravaMap.client.tsx`: hydrated island (loading/error ribbons, sync link)
- `javascript/src/components/StravaProfileMap/activities.ts`: paginated GraphQL loading of activities
- `javascript/src/components/StravaProfileMap/sync.ts`: GraphQL WebSocket subscription
- `javascript/src/components/StravaProfileMap/map.ts`: Leaflet map, polyline decoding, popups
- `javascript/src/components/StravaLogin/default.server.tsx`: the "Log in with Strava" button, "My Strava map" once logged in

## Strava profile and `/strava/<login>`

The Strava login fills the profile: `StravaConnectorResultProcessor` reads the athlete who owns the token (`GET /api/v3/athlete`), adds the `foont:stravaProfile` mixin to the user and sets `stravaUrl` to `https://www.strava.com/athletes/<id>`. A user who is not `site-privileged` never edits their profile, so no form asks for it. `StravaProfileListener` then creates or updates the `foont:stravaProfileMap` `strava-map` under the user node, keeps it for its owner (ACL inheritance broken, `owner` role for the user only). The map carries `jmix:autoPublish`, so LIVE follows each save: once `stravaUrl` is emptied, or the mixin removed, the listener removes the map from the edit workspace, LIVE loses it too, and `/strava/<login>` answers 404.

`/strava/<login>` serves that map:

- a logged-in visitor gets `/users/.../<login>/strava-map` rendered from LIVE, inside the `foont:stravaProfileMap` page template of `jahia-strava-js`; the ACL refuses it to anyone but its owner;
- a guest is redirected to the Strava login (`oidc-connect.do`) of the first site where the `OidcConnector` is enabled, with a `strava-return` cookie that brings them back after the login.

`/strava/` is the map of the visitor, the link of the `foont:stravaLogin` button: a guest goes through the Strava login and comes back there, which creates their map on the way, and a logged-in user is redirected to `/strava/<login>`. To drop the button on a page, enable `jahia-strava` and `jahia-strava-js` on that site too.

`/users` belongs to `systemsite`, so both modules must be enabled there, and the connector must return through the cookie. Once both modules are deployed, run through the provisioning API:

```yaml
- enable: "jahia-strava"
  site: "systemsite"
- enable: "jahia-strava-js"
  site: "systemsite"
- editConfiguration: "org.jahia.modules.auth"
  configIdentifier: "<site key>"
  properties:
    OidcConnector.returnMode: "cookie"
    OidcConnector.returnCookie: "strava-return"
```

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
