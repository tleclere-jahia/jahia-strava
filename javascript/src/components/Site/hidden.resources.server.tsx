import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
} from "@jahia/javascript-modules-library";
import "../../assets/global.css";

/**
 * The `jahia-strava-js` module is not a template set: it contributes views for content types
 * owned by the `jahia-strava` Java module. This hidden view on the site node injects the module's
 * stylesheet (which contains the Leaflet and marker cluster styles, imported by the client bundle)
 * into the page's head, the same way the platform's default template does for its own resources.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "jnt:virtualsite",
    name: "hidden.resources",
  },
  () => <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />,
);
