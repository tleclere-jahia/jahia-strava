// Leaflet reads `window` when it loads: import this file only from the browser, with a dynamic
// import(). Its stylesheets are imported by StravaMap.client.tsx.
import L from "leaflet";
import "leaflet.fullscreen";
import "leaflet.markercluster";

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
  name: string;
  start_date: string;
  distance: number;
  total_elevation_gain: number;
  moving_time: number;
  start_latlng?: number[];
  map?: { polyline?: string };
}

export interface StravaMapHandle {
  map: L.Map;
  addActivities: (activities: Activity[], language: string) => void;
  fitToActivities: () => void;
  setView: (center: [number, number], zoom: number) => void;
  remove: () => void;
}

/**
 * Format the description shown in the marker popup, locale-aware.
 * `Intl.DurationFormat` is available in modern browsers; a plain fallback is used otherwise.
 */
const formatDuration = (seconds: number, language: string): string => {
  const duration = {
    days: Math.floor(seconds / 86400) || undefined,
    hours: Math.floor((seconds % 86400) / 3600) || undefined,
    minutes: Math.floor((seconds % 3600) / 60) || undefined,
    seconds: seconds % 60 || undefined,
  };
  const IntlWithDurationFormat = Intl as unknown as {
    DurationFormat?: new (
      locale: string,
      options?: { style?: string },
    ) => {
      format: (duration: Record<string, number | undefined>) => string;
    };
  };
  if (IntlWithDurationFormat.DurationFormat) {
    return new IntlWithDurationFormat.DurationFormat(language, { style: "long" }).format(duration);
  }
  // Minimal fallback if Intl.DurationFormat is not available
  return Object.entries(duration)
    .filter(([, value]) => value)
    .map(([unit, value]) => `${value} ${unit}`)
    .join(" ");
};

const buildDescription = (activity: Activity, language: string): string => {
  const formattedDate = new Intl.DateTimeFormat(language, {
    dateStyle: "full",
    timeStyle: "long",
  }).format(Date.parse(activity.start_date));
  return `<h4>${activity.name}</h4>
    <ul>
        <li>Date: <span title="${activity.start_date}">${formattedDate}</span></li>
        <li>Distance: ${activity.distance} m</li>
        <li>Elevation: ${activity.total_elevation_gain} m</li>
        <li>Duration: ${formatDuration(activity.moving_time, language)}</li>
    </ul>`;
};

/**
 * Create the Leaflet map with OSM tiles, the fullscreen control and a marker cluster group.
 * Markers open a popup describing the activity, with its polyline drawn underneath.
 */
export const createMap = (container: HTMLElement): StravaMapHandle => {
  const features = L.featureGroup();

  const map = L.map(container, {
    fullscreenControl: true,
  })
    .on("popupopen", (e) => {
      const polyline = (e.popup.options as { polyline?: L.Polyline }).polyline;
      polyline?.addTo(map);
    })
    .on("popupclose", (e) => {
      const polyline = (e.popup.options as { polyline?: L.Polyline }).polyline;
      polyline?.remove();
    });

  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(map);

  const addActivities = (activities: Activity[], language: string) => {
    const markers = L.markerClusterGroup({ removeOutsideVisibleBounds: false });
    for (const activity of activities) {
      if (activity.start_latlng?.length === 2) {
        const marker = L.marker(L.latLng(activity.start_latlng[0], activity.start_latlng[1]), {
          title: activity.name,
        });
        marker.on("click", (e) => {
          const polyline = L.polyline(decodePolyline(activity.map?.polyline ?? ""), {
            color: "#9900CC",
            weight: 2,
            opacity: 0.7,
            lineJoin: "round",
          });
          polyline.bindTooltip(activity.name, { sticky: true });
          polyline.on("mouseover", (ev) =>
            ev.target.setStyle({ color: "red", weight: 5, opacity: 1 }),
          );
          polyline.on("mouseout", (ev) =>
            ev.target.setStyle({ color: "#9900CC", weight: 2, opacity: 0.7 }),
          );
          const popup = L.popup(e.latlng, {
            content: buildDescription(activity, language),
          }) as L.Popup & { polyline?: L.Polyline };
          popup.polyline = polyline;
          popup.openOn(map);
          map.fitBounds(polyline.getBounds());
        });
        markers.addLayer(marker);
        features.addLayer(marker);
      }
    }
    map.addLayer(markers);
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
