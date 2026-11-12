import { jahiaComponent, Render } from "@jahia/javascript-modules-library";

/**
 * The page around a foont:stravaProfileMap rendered as the main resource, the map of a user under
 * /users/<login>: /users belongs to no site, so no template set provides that page.
 */
jahiaComponent(
  {
    componentType: "template",
    nodeType: "foont:stravaProfileMap",
    name: "default",
    displayName: "Strava map page",
  },
  (_props, { currentNode, renderContext }) => (
    <html lang={renderContext.getMainResourceLocale().getLanguage()}>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>{`Strava - ${currentNode.getParent().getName()}`}</title>
      </head>
      <body style={{ margin: 0, padding: "1rem", fontFamily: "system-ui, sans-serif" }}>
        <Render node={currentNode} view="default" />
      </body>
    </html>
  ),
);
