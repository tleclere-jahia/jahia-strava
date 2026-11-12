package org.foo.modules.jahia.strava.actions;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.reactivestreams.Publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@GraphQLTypeExtension(DXGraphQLProvider.Subscription.class)
public class StravaSynchronizationSubscriptionExtension {
    private static final Map<String, FlowableEmitter<GqlStravaSynchronization>> listeners = new HashMap<>();

    private StravaSynchronizationSubscriptionExtension() {
        throw new IllegalStateException("Subscription class are fully static and must not be instantiated");
    }

    @GraphQLField
    @GraphQLDescription("Subscription on Strava synchronization")
    public static Publisher<GqlStravaSynchronization> subscribeToStravaSynchronization() {
        return Flowable.create(obs -> {
            String name = UUID.randomUUID().toString();
            listeners.put(name, obs);
            obs.setCancellable(() -> listeners.remove(name));
        }, BackpressureStrategy.BUFFER);
    }

    public static void notifyListeners(GqlStravaSynchronization.StravaSynchronizationStatus status, String message) {
        notifyListeners(status, message, null);
    }

    public static void notifyListeners(GqlStravaSynchronization.StravaSynchronizationStatus status, String message, String path) {
        listeners.forEach((name, emitter) -> emitter.onNext(new GqlStravaSynchronization(status, message, path)));
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
