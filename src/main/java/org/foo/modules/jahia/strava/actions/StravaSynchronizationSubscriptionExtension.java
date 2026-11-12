package org.foo.modules.jahia.strava.actions;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@GraphQLTypeExtension(DXGraphQLProvider.Subscription.class)
public class StravaSynchronizationSubscriptionExtension {
    /** subscription id → the subscriber, with the path of the user who subscribed */
    private static final Map<String, Listener> listeners = new ConcurrentHashMap<>();

    private static final class Listener {
        private final String userPath;
        private final FlowableEmitter<GqlStravaSynchronization> emitter;

        private Listener(String userPath, FlowableEmitter<GqlStravaSynchronization> emitter) {
            this.userPath = userPath;
            this.emitter = emitter;
        }
    }

    private StravaSynchronizationSubscriptionExtension() {
        throw new IllegalStateException("Subscription class are fully static and must not be instantiated");
    }

    @GraphQLField
    @GraphQLDescription("Subscription on the Strava synchronization of the current user")
    public static Publisher<GqlStravaSynchronization> subscribeToStravaSynchronization() {
        // the user of the WebSocket session, read now: the emitter runs on another thread
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        String userPath = user != null ? user.getLocalPath() : null;
        return Flowable.create(obs -> {
            String name = UUID.randomUUID().toString();
            listeners.put(name, new Listener(userPath, obs));
            obs.setCancellable(() -> listeners.remove(name));
        }, BackpressureStrategy.BUFFER);
    }

    public static void notifyListeners(String userPath, GqlStravaSynchronization.StravaSynchronizationStatus status, String message) {
        notifyListeners(userPath, status, message, null);
    }

    /** Notifies the subscriptions of the user whose activities are synchronized, and no other */
    public static void notifyListeners(String userPath, GqlStravaSynchronization.StravaSynchronizationStatus status, String message, String path) {
        GqlStravaSynchronization synchronization = new GqlStravaSynchronization(status, message, path);
        listeners.values().stream()
                .filter(listener -> userPath != null && userPath.equals(listener.userPath))
                .forEach(listener -> listener.emitter.onNext(synchronization));
    }

    @GraphQLDescription("Strava synchronization")
    public static class GqlStravaSynchronization {
        @GraphQLDescription("Status of the synchronization")
        public enum StravaSynchronizationStatus {STARTED, IN_PROGRESS, ERROR, FINISHED}

        @GraphQLField
        @GraphQLDescription("Synchronization status")
        private final StravaSynchronizationStatus status;

        @GraphQLField
        @GraphQLDescription("Synchronization message")
        private final String message;

        @GraphQLField
        @GraphQLDescription("Path of the activity node just synchronized, if any")
        private final String path;

        public GqlStravaSynchronization(StravaSynchronizationStatus status, String message, String path) {
            this.status = status;
            this.message = message;
            this.path = path;
        }
    }
}
