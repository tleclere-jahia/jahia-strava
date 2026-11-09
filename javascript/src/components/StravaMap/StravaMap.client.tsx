import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { createMap } from "./map";
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

  const mapRef = useRef<ReturnType<typeof createMap>>(undefined);

  const displayError = useCallback((error: unknown) => {
    console.error(error);
    setPhase("error");
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Leaflet is imperative: create the map once, on the client only
    mapRef.current = createMap(container);
    const map = mapRef.current;

    let disposed = false;

    // Center the map on the visitor's location
    navigator.geolocation.getCurrentPosition((location) =>
      map.setView([location.coords.latitude, location.coords.longitude], 11),
    );

    loadAllActivities({
      gqlUrl,
      language,
      map,
      onTotalCount: (count) => {
        if (nbActivitiesRef.current) nbActivitiesRef.current.innerText = String(count);
      },
    })
      .then(() => {
        if (!disposed) setPhase("ready");
      })
      .catch(displayError);

    // Live synchronization status through the Jahia GraphQL websocket
    const closeSocket = subscribeToSynchronization({
      wsUrl,
      onMessage: (message) => {
        if (!disposed) {
          setPhase("loading");
          setLoadingMessage(message);
        }
      },
      onFinished: () => window.location.reload(),
      onError: displayError,
    });

    return () => {
      disposed = true;
      closeSocket();
      map.remove();
      mapRef.current = undefined;
    };
  }, [gqlUrl, wsUrl, language, displayError]);

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
