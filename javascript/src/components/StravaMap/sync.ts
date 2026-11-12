interface SubscribeOptions {
  /** WebSocket URL of the Jahia GraphQL subscription endpoint (/modules/graphqlws) */
  wsUrl: string;
  /**
   * Called on every progress message (synchronization running), with the path of the activity
   * node just synchronized, if any
   */
  onMessage: (message: string, path?: string) => void;
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
            path
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
        onMessage(synchronization.message ?? "", synchronization.path ?? undefined);
      }
    } catch (error) {
      onError(error);
    }
  };

  socket.onerror = (error) => onError(error);

  return () => socket.close();
};
