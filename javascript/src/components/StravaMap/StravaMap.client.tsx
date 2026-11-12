import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import "leaflet/dist/leaflet.css";
import "leaflet.fullscreen/dist/Control.FullScreen.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";
import type { StravaMapHandle } from "./map";
import { loadAllActivities } from "./activities";
import { subscribeToSynchronization } from "./sync";
import classes from "./styles.module.css";

export interface StravaMapClientProps {
  /** Display language for dates and durations */
  language: string;
  /** URL of the Jahia GraphQL endpoint (/modules/graphql) */
  gqlUrl: string;
  /** URL of the Jahia GraphQL websocket endpoint (/modules/graphqlws) */
  wsUrl: string;
  /** URL of the syncMe action (.syncMe.do) */
  syncUrl: string;
}

type Phase = "loading" | "ready" | "error";

export default function StravaMapClient({
  language,
  gqlUrl,
  wsUrl,
  syncUrl,
}: StravaMapClientProps) {
  const { t } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  const nbActivitiesRef = useRef<HTMLSpanElement>(null);
  const [phase, setPhase] = useState<Phase>("loading");
  const [loadingMessage, setLoadingMessage] = useState<string | undefined>(undefined);

  const mapRef = useRef<StravaMapHandle>(undefined);

  const displayError = useCallback((error: unknown) => {
    console.error(error);
    setPhase("error");
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let disposed = false;
    let closeSocket: (() => void) | undefined;
    let map: StravaMapHandle | undefined;

    // Leaflet reads `window` as soon as it loads, so it is loaded here, in the browser only:
    // a static import would run it on the server (GraalVM) when the module starts.
    import("./map")
      .then(({ createMap }) => {
        if (disposed) return;

        // Leaflet is imperative: create the map once
        const created = createMap(container);
        map = created;
        mapRef.current = created;

        // Center the map on the visitor's location
        navigator.geolocation.getCurrentPosition((location) =>
          created.setView([location.coords.latitude, location.coords.longitude], 11),
        );

        loadAllActivities({
          gqlUrl,
          language,
          labels: {
            date: t("activity.date"),
            distance: t("activity.distance"),
            elevation: t("activity.elevation"),
            duration: t("activity.duration"),
            viewOnStrava: t("activity.viewOnStrava"),
          },
          map: created,
          onTotalCount: (count) => {
            if (nbActivitiesRef.current) nbActivitiesRef.current.innerText = String(count);
          },
        })
          .then(() => {
            if (!disposed) setPhase("ready");
          })
          .catch(displayError);

        // Live synchronization status through the Jahia GraphQL websocket
        closeSocket = subscribeToSynchronization({
          wsUrl,
          onMessage: (message, date) => {
            if (!disposed) {
              setPhase("loading");
              setLoadingMessage(
                date
                  ? t("stravaMap.syncActivity", {
                      date: new Intl.DateTimeFormat(language, {
                        dateStyle: "medium",
                        timeStyle: "short",
                      }).format(Date.parse(date)),
                    })
                  : message,
              );
            }
          },
          onFinished: () => window.location.reload(),
          onError: displayError,
        });
      })
      .catch(displayError);

    return () => {
      disposed = true;
      closeSocket?.();
      map?.remove();
      mapRef.current = undefined;
    };
  }, [gqlUrl, wsUrl, language, displayError, t]);

  const ribbonClasses = useMemo(
    () => `${classes.ribbon} ${phase === "loading" ? classes.loading : classes.error}`,
    [phase],
  );

  const syncMe = (event: React.MouseEvent) => {
    event.preventDefault();
    fetch(syncUrl);
  };

  return (
    <>
      <p>
        <span ref={nbActivitiesRef} id="nbActivities" /> {t("stravaMap.user.activities")} (
        <a href="#" onClick={syncMe}>
          {t("stravaMap.user.syncMe")}
        </a>
        )
      </p>
      <p className={classes.syncDescription}>{t("stravaMap.user.syncMeDescription")}</p>
      <div ref={containerRef} className={classes.map}>
        <div className={ribbonClasses} style={{ display: phase === "loading" ? "block" : "none" }}>
          {loadingMessage ?? t("stravaMap.loading")}
        </div>
        <div
          className={`${classes.ribbon} ${classes.error}`}
          style={{ display: phase === "error" ? "block" : "none" }}
        >
          {t("stravaMap.error")}
        </div>
      </div>
    </>
  );
}
