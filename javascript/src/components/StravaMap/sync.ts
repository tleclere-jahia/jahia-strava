interface SubscribeOptions {
  /** WebSocket URL of the Jahia GraphQL subscription endpoint (/modules/graphqlws) */
  wsUrl: string;
  /** Called on every progress message (synchronization running) */
  onMessage: (message: string) => void;
  /** Called when the synchronization finished: the page needs a refresh */
  onFinished: () => void;
  onError: (error: unknown) => void;
}

/**
 * Subscribe to the Strava synchronization background job status through the
 * Jahia GraphQL websocket endpoint. The subscription type
 * (`subscribeToStravaSynchronization`) is contributed by the `jahia-strava` Java module.
 */
export const subscribeToSynchronization = ({
  wsUrl,
  onMessage,
  onFinished,
  onError,
}: SubscribeOptions): (() => void) => {
  const socket = new WebSocket(wsUrl.replace(/^http/, "ws"));

  socket.onopen = () =>
    socket.send(
      JSON.stringify({
        type: "connection_init",
        payload: {},
        query: `subscription {
          subscribeToStravaSynchronization {
            status
            message
          }
        }`,
      }),
    );

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      const synchronization = data.data?.subscribeToStravaSynchronization;
      if (!synchronization) return;
      if (synchronization.status === "FINISHED") {
        onFinished();
      } else {
        onMessage(synchronization.message ?? "");
      }
    } catch (error) {
      onError(error);
    }
  };

  socket.onerror = (error) => onError(error);

  return () => socket.close();
};
