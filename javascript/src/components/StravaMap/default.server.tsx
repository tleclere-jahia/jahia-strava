import {
  AddResources,
  buildEndpointUrl,
  buildModuleFileUrl,
  buildNodeUrl,
  Island,
  jahiaComponent,
  server,
} from "@jahia/javascript-modules-library";
import type { TFunction } from "i18next";
import type { RenderContext } from "org.jahia.services.render";
import type { JahiaUser } from "org.jahia.services.usermanager";
import { useTranslation } from "react-i18next";
import StravaMapClient from "./StravaMap.client";
import classes from "./styles.module.css";

interface Props {
  "jcr:title"?: string;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "foont:stravaMap",
    name: "default",
    properties: {
      "cache.perUser": "true",
      "cache.mainResource": "true",
      "cache.sessionAttributes": "tokenData",
    },
  },
  ({ "jcr:title": title }: Props, { renderContext }) => {
    const { t } = useTranslation();
    const user = renderContext.getUser();

    // The view is rendered inside a page provided by the site's template set: contribute the
    // module's aggregated stylesheet (Leaflet, marker cluster, ribbons) to the document head,
    // the way the former JSP view did with <template:addResources type="css"/>
    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <StravaMapView title={title} t={t} renderContext={renderContext} user={user} />
      </>
    );
  },
);

/**
 * The actual map component, extracted so that `AddResources` wraps it in the registered view.
 */
const StravaMapView = ({
  title,
  t,
  renderContext,
  user,
}: {
  title?: string;
  t: TFunction;
  renderContext: RenderContext;
  user: JahiaUser;
}) => {
  // Not logged in: propose to connect through the OIDC connector (jahia-oauth-oidc module)
  if (!renderContext.isLoggedIn()) {
    const connectUrl = buildNodeUrl(renderContext.getMainResource().getNode(), {
      extension: ".oidc-connect.do",
    });
    return (
      <a href={connectUrl} title={title}>
        {title}
      </a>
    );
  }

  // The map content depends on the user's synchronized activities: flush when they change
  const userPath = user.getLocalPath();
  if (userPath) {
    server.render.addCacheDependency(
      { flushOnPathMatchingRegexp: `${userPath}/strava-activities/.*` },
      renderContext,
    );
  }

  const language = renderContext.getMainResource().getLocale().getLanguage();
  const firstName = user.getProperty("j:firstName");
  const lastName = user.getProperty("j:lastName");
  const name = [firstName, lastName].filter(Boolean).join(" ") || user.getUsername();

  // `lastStravaSync` is a date property on the user node: read it through the JCR session, the
  // way the former JSP view did with <jcr:node path="${currentUser.localPath}"/>
  const userNode = renderContext
    .getMainResource()
    .getNode()
    .getSession()
    .getNode(user.getLocalPath());
  const lastStravaSyncTime = userNode.hasProperty("lastStravaSync")
    ? userNode.getProperty("lastStravaSync").getValue().getTime()
    : null;
  // `getTime()` returns a java.util.Date proxy: this converts it to a plain JavaScript epoch,
  // which is a pure deterministic operation (the purity rule targets `new Date()` without args)
  const formattedLastSync = lastStravaSyncTime
    ? new Intl.DateTimeFormat(language, { dateStyle: "short", timeStyle: "short" }).format(
        // eslint-disable-next-line @eslint-react/purity -- deterministic Java -> JS date conversion
        new Date(lastStravaSyncTime.getTime()),
      )
    : null;

  // Same endpoints as the former JSP view, built through the URL generator so that they
  // respect the webapp context and URL rewriting
  const mainResourceNode = renderContext.getMainResource().getNode();
  const syncUrl = buildNodeUrl(mainResourceNode, { extension: ".syncMe.do" });

  return (
    <div className={classes.stravaMap}>
      <h1>{t("stravaMap.user.hello", { name })}</h1>
      {formattedLastSync && (
        <p className={classes.lastSync}>
          {t("stravaMap.user.lastStravaSync")} {formattedLastSync}
        </p>
      )}
      {renderContext.isEditMode() ? (
        <div className={classes.editModeNotice} role="alert">
          {t("stravaMap.editMode")}
        </div>
      ) : (
        <Island
          component={StravaMapClient}
          props={{
            language,
            gqlUrl: buildEndpointUrl("/modules/graphql"),
            wsUrl: buildEndpointUrl("/modules/graphqlws"),
            syncUrl,
          }}
        />
      )}
    </div>
  );
};
