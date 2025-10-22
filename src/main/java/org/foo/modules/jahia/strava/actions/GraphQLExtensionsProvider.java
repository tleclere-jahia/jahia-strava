package org.foo.modules.jahia.strava.actions;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

@Component(service = DXGraphQLExtensionsProvider.class)
public class GraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {
}
