import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import "leaflet/dist/leaflet.css";
import "leaflet.fullscreen/dist/Control.FullScreen.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";
import type { StravaMapHandle } from "./map";
import { loadActivity, loadAllActivities, loadPendingCount } from "./activities";
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

/** How long a synchronization error stays on screen, before the ribbon hides and the page reloads */
const SYNC_ERROR_DISPLAY_MS = 10_000;

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
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [pending, setPending] = useState<number | null>(null);

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
    let errorShownUntil = 0;
    let errorTimer: ReturnType<typeof setTimeout> | undefined;
    let reloadTimer: ReturnType<typeof setTimeout> | undefined;

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

        const labels = {
          date: t("activity.date"),
          distance: t("activity.distance"),
          elevation: t("activity.elevation"),
          duration: t("activity.duration"),
          viewOnStrava: t("activity.viewOnStrava"),
        };
        let total = 0;
        const showTotal = (count: number) => {
          total = count;
          if (nbActivitiesRef.current) nbActivitiesRef.current.innerText = String(count);
        };

        loadAllActivities({
          gqlUrl,
          language,
          labels,
          map: created,
          onTotalCount: showTotal,
        })
          .then(() => {
            if (!disposed) setPhase("ready");
          })
          .catch(displayError);

        // Strava activities after the last synchronization that the map does not hold yet
        loadPendingCount(gqlUrl)
          .then((count) => {
            if (!disposed) setPending(count);
          })
          .catch((error) => console.warn("Cannot count the pending activities", error));

        // Live synchronization status through the Jahia GraphQL websocket: each activity the job
        // saves joins the map, its clusters and the count as it arrives
        closeSocket = subscribeToSynchronization({
          wsUrl,
          onMessage: (message, path) => {
            if (disposed) return;
            setPhase("loading");
            if (!path) {
              setLoadingMessage(message);
              return;
            }
            loadActivity(gqlUrl, path)
              .then((activity) => {
                if (disposed) return;
                const added = created.addActivities([activity], language, labels);
                if (added) showTotal(total + added);
                setLoadingMessage(
                  t("stravaMap.syncActivity", {
                    date: new Intl.DateTimeFormat(language, {
                      dateStyle: "medium",
                      timeStyle: "short",
                    }).format(Date.parse(activity.start_date)),
                  }),
                );
              })
              .catch((error) => {
                console.warn(`Cannot load the synchronized activity ${path}`, error);
                setLoadingMessage(message);
              });
          },
          onSyncError: (message) => {
            if (disposed) return;
            console.warn(`Strava synchronization stopped: ${message}`);
            setErrorMessage(t("stravaMap.syncError"));
            setPhase("error");
            errorShownUntil = Date.now() + SYNC_ERROR_DISPLAY_MS;
            clearTimeout(errorTimer);
            errorTimer = setTimeout(() => {
              setPhase("ready");
              setErrorMessage(undefined);
            }, SYNC_ERROR_DISPLAY_MS);
          },
          // the job sends FINISHED right after an error: let the error stay readable first
          onFinished: () => {
            reloadTimer = setTimeout(
              () => window.location.reload(),
              Math.max(0, errorShownUntil - Date.now()),
            );
          },
          onError: displayError,
        });
      })
      .catch(displayError);

    return () => {
      disposed = true;
      clearTimeout(errorTimer);
      clearTimeout(reloadTimer);
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
        {pending ? (
          <span className={classes.pending}>
            {" · "}
            {t("stravaMap.user.pending", { count: pending })}
          </span>
        ) : null}
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
          {errorMessage ?? t("stravaMap.error")}
        </div>
      </div>
    </>
  );
}
