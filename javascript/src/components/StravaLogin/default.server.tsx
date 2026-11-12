import {
  AddResources,
  buildEndpointUrl,
  buildModuleFileUrl,
  jahiaComponent,
} from "@jahia/javascript-modules-library";
import { useTranslation } from "react-i18next";
import classes from "./styles.module.css";

/**
 * /strava/ is the map of the visitor: StravaVanityFilter sends a guest to the Strava login, which
 * fills their stravaUrl and so creates their map, and sends a logged-in user to /strava/<login>.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "foont:stravaLogin",
    name: "default",
    displayName: "Strava login button",
    properties: { "cache.perUser": "true" },
  },
  (_props, { renderContext }) => {
    const { t } = useTranslation();
    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <a className={classes.stravaLogin} href={buildEndpointUrl("/strava/")}>
          {renderContext.isLoggedIn() ? t("stravaLogin.myMap") : t("stravaLogin.login")}
        </a>
      </>
    );
  },
);
