package com.atlassian.bamboo.plugin.bbtriggerremover.rest;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.build.BuildDefinitionManager;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plugin.BambooPluginKeys;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bamboo.trigger.TriggerDefinition;
import com.atlassian.plugin.spring.scanner.annotation.imports.BambooImport;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/")
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
public class BitbucketTriggerRemovalResource {
    private static final Logger log = Logger.getLogger(BitbucketTriggerRemovalResource.class);

    @Inject
    @BambooImport
    private CachedPlanManager cachedPlanManager;

    @Inject
    @BambooImport
    private BambooPermissionManager bambooPermissionManager;

    @Inject
    @BambooImport
    private BuildDefinitionManager buildDefinitionManager;

    @Inject
    @BambooImport
    private PlanManager planManager;

    @GET
    @Path("count")
    public Response getBbTriggerStatistics() {
        if (!bambooPermissionManager.hasGlobalPermission(BambooPermission.RESTRICTEDADMINISTRATION)) {
            return Response.status(HttpStatus.SC_FORBIDDEN).build();
        }
        int counter = 0;
        for (ImmutableChain immutableChain : cachedPlanManager.getPlans(ImmutableChain.class)) {
            counter += immutableChain.getTriggerDefinitions().stream().filter(triggerDefinition -> triggerDefinition.getPluginKey().equals(BambooPluginKeys.STASH_TRIGGER_PLUGIN_KEY)).count();
        }

        return Response.ok(String.format("Found %s bitbucket server triggers", counter)).build();
    }

    @POST
    @Path("removeall")
    public Response removeAllBbServerTriggers() {
        if (!bambooPermissionManager.hasGlobalPermission(BambooPermission.RESTRICTEDADMINISTRATION)) {
            return Response.status(HttpStatus.SC_FORBIDDEN).build();
        }
        for (Chain plan : planManager.getAllPlans(Chain.class)) {
            BuildDefinition buildDefinition = buildDefinitionManager.getUnmergedBuildDefinition(plan.getPlanKey());
            if (buildDefinition.getTriggerDefinitions() == null || buildDefinition.getTriggerDefinitions().isEmpty()) {
                continue;
            }

            final List<TriggerDefinition> triggerDefinitions = buildDefinition.getTriggerDefinitions().stream().filter(triggerDefinition -> !triggerDefinition.getPluginKey().equals(BambooPluginKeys.STASH_TRIGGER_PLUGIN_KEY))
                    .collect(Collectors.toList());

            buildDefinition.setTriggerDefinitions(triggerDefinitions);
            buildDefinitionManager.savePlanAndDefinition(plan, buildDefinition, false);
        }
        return Response.noContent().build();
    }
}
