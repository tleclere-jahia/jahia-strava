// Leaflet reads `window` when it loads: import this file only from the browser, with a dynamic
// import(). Its stylesheets are imported by StravaMap.client.tsx.
import L from "leaflet";
import "leaflet.fullscreen";
import "leaflet.markercluster";
import classes from "./styles.module.css";

/**
 * Decode an encoded polyline string (Google polyline algorithm, as returned by Strava)
 */
export const decodePolyline = (polyline: string): [number, number][] => {
  // precision
  const inv = 1.0 / 1e5;
  const decoded: [number, number][] = [];
  const previous = [0, 0];
  let i = 0;
  // for each byte
  while (i < polyline.length) {
    // for each coord (lat, lon)
    const ll = [0, 0];
    for (let j = 0; j < 2; j++) {
      let shift = 0;
      let byte = 0x20;
      // keep decoding bytes until you have this coord
      while (byte >= 0x20) {
        byte = polyline.charCodeAt(i++) - 63;
        ll[j] |= (byte & 0x1f) << shift;
        shift += 5;
      }
      // add previous offset to get final value and remember for next one
      ll[j] = previous[j] + (ll[j] & 1 ? ~(ll[j] >> 1) : ll[j] >> 1);
      previous[j] = ll[j];
    }
    // scale by precision and flip the positions so it's the far more standard
    // lon,lat instead of lat,lon
    decoded.push([ll[0] * inv, ll[1] * inv]);
  }
  return decoded;
};

export interface Activity {
  id: number;
  name: string;
  sport_type?: string;
  start_date: string;
  distance: number;
  total_elevation_gain: number;
  moving_time: number;
  start_latlng?: number[];
  map?: { polyline?: string };
}

/** Translated labels of the activity popup */
export interface PopupLabels {
  date: string;
  distance: string;
  elevation: string;
  duration: string;
  viewOnStrava: string;
}

export interface StravaMapHandle {
  map: L.Map;
  /** Add the activities not on the map yet, and return how many were added */
  addActivities: (activities: Activity[], language: string, labels: PopupLabels) => number;
  fitToActivities: () => void;
  setView: (center: [number, number], zoom: number) => void;
  remove: () => void;
}

const STRAVA_PURPLE = "#9900cc";

/** Pictogram and color of the marker, by Strava sport type */
const SPORTS: Record<string, { emoji: string; color: string }> = {
  Ride: { emoji: "🚴", color: STRAVA_PURPLE },
  GravelRide: { emoji: "🚴", color: "#a0522d" },
  MountainBikeRide: { emoji: "🚵", color: "#2e7d32" },
  VirtualRide: { emoji: "🚴", color: "#546e7a" },
  EBikeRide: { emoji: "🚴", color: "#00838f" },
  Run: { emoji: "🏃", color: "#fc4c02" },
  TrailRun: { emoji: "🏃", color: "#e65100" },
  Walk: { emoji: "🚶", color: "#6d4c41" },
  Hike: { emoji: "🥾", color: "#6d4c41" },
  Swim: { emoji: "🏊", color: "#0277bd" },
};
const DEFAULT_SPORT = { emoji: "📍", color: STRAVA_PURPLE };

const markerIcon = (activity: Activity): L.DivIcon => {
  const sport = SPORTS[activity.sport_type ?? ""] ?? DEFAULT_SPORT;
  return L.divIcon({
    className: classes.marker,
    html: `<span class="${classes.markerPin}" style="--sport-color: ${sport.color}"><span>${sport.emoji}</span></span>`,
    iconSize: [32, 32],
    // the pin is a square rotated by 45°: its tip sits 16√2 px below its center
    iconAnchor: [16, 39],
    popupAnchor: [0, -36],
  });
};

/**
 * Hours and minutes of a duration, locale-aware. `Intl.DurationFormat` is available in modern
 * browsers; a plain fallback is used otherwise.
 */
const formatDuration = (seconds: number, language: string): string => {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.round((seconds % 3600) / 60);
  const IntlWithDurationFormat = Intl as unknown as {
    DurationFormat?: new (
      locale: string,
      options?: { style?: string },
    ) => {
      format: (duration: Record<string, number>) => string;
    };
  };
  if (IntlWithDurationFormat.DurationFormat) {
    return new IntlWithDurationFormat.DurationFormat(language, { style: "short" }).format(
      hours ? { hours, minutes } : { minutes },
    );
  }
  return hours ? `${hours} h ${String(minutes).padStart(2, "0")}` : `${minutes} min`;
};

const element = <K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className: string,
  text?: string,
): HTMLElementTagNameMap[K] => {
  const node = document.createElement(tag);
  node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
};

/** The popup of an activity, built as DOM nodes so that its name is never read as HTML */
const buildPopup = (activity: Activity, language: string, labels: PopupLabels): HTMLElement => {
  const number = (value: number, unit: string, maximumFractionDigits: number) =>
    new Intl.NumberFormat(language, { style: "unit", unit, maximumFractionDigits }).format(value);
  const stats: [string, string][] = [
    [
      labels.date,
      new Intl.DateTimeFormat(language, { dateStyle: "medium", timeStyle: "short" }).format(
        Date.parse(activity.start_date),
      ),
    ],
    [labels.distance, number(activity.distance / 1000, "kilometer", 1)],
    [labels.elevation, number(activity.total_elevation_gain, "meter", 0)],
    [labels.duration, formatDuration(activity.moving_time, language)],
  ];

  const popup = element("div", classes.popup);
  popup.append(element("div", classes.popupTitle, activity.name));
  const list = element("dl", classes.popupStats);
  for (const [label, value] of stats) {
    list.append(element("dt", "", label), element("dd", "", value));
  }
  popup.append(list);
  const link = element("a", classes.popupLink, labels.viewOnStrava);
  link.href = `https://www.strava.com/activities/${activity.id}`;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  popup.append(link);
  return popup;
};

/**
 * Create the Leaflet map with OSM tiles, the fullscreen control and a marker cluster group.
 * Markers open a popup describing the activity, with its polyline drawn underneath.
 */
export const createMap = (container: HTMLElement): StravaMapHandle => {
  const features = L.featureGroup();
  // one cluster group for the whole map, so that activities added later join the same clusters
  const markers = L.markerClusterGroup({ removeOutsideVisibleBounds: false });
  const activityIds = new Set<number>();

  const map = L.map(container, {
    fullscreenControl: true,
  })
    .on("popupopen", (e) => {
      const polyline = (e.popup as L.Popup & { polyline?: L.Polyline }).polyline;
      polyline?.addTo(map);
    })
    .on("popupclose", (e) => {
      const polyline = (e.popup as L.Popup & { polyline?: L.Polyline }).polyline;
      polyline?.remove();
    });

  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(map);
  map.addLayer(markers);

  const addActivities = (activities: Activity[], language: string, labels: PopupLabels) => {
    let added = 0;
    const newMarkers: L.Marker[] = [];
    for (const activity of activities) {
      if (activityIds.has(activity.id)) continue;
      activityIds.add(activity.id);
      added++;
      if (activity.start_latlng?.length === 2) {
        const marker = L.marker(L.latLng(activity.start_latlng[0], activity.start_latlng[1]), {
          title: activity.name,
          icon: markerIcon(activity),
        });
        marker.on("click", (e) => {
          const polyline = L.polyline(decodePolyline(activity.map?.polyline ?? ""), {
            color: STRAVA_PURPLE,
            weight: 2,
            opacity: 0.7,
            lineJoin: "round",
          });
          polyline.bindTooltip(activity.name, { sticky: true });
          polyline.on("mouseover", (ev) =>
            ev.target.setStyle({ color: "red", weight: 5, opacity: 1 }),
          );
          polyline.on("mouseout", (ev) =>
            ev.target.setStyle({ color: STRAVA_PURPLE, weight: 2, opacity: 0.7 }),
          );
          const popup = L.popup(e.latlng, {
            content: buildPopup(activity, language, labels),
            minWidth: 220,
            maxWidth: 300,
          }) as L.Popup & { polyline?: L.Polyline };
          popup.polyline = polyline;
          popup.openOn(map);
          map.fitBounds(polyline.getBounds());
        });
        newMarkers.push(marker);
        features.addLayer(marker);
      }
    }
    markers.addLayers(newMarkers);
    return added;
  };

  const fitToActivities = () => {
    if (features.getLayers().length > 0) {
      map.fitBounds(features.getBounds());
    }
  };

  return {
    map,
    addActivities,
    fitToActivities,
    setView: (center, zoom) => map.setView(center, zoom),
    remove: () => map.remove(),
  };
};
