/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.graph.runtime.age;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.graph.model.query.builder.AgeStatements;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration of the Apache AGE repositories.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@ConfigurationProperties(AgeConfiguration.PREFIX)
public class AgeConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micronaut.data.age";

    /**
     * The default graph name.
     */
    public static final String DEFAULT_GRAPH = "graph";

    private String graph = DEFAULT_GRAPH;
    private Map<String, String> graphs = Map.of();
    private Map<String, String> tenantGraphs = Map.of();
    private Map<String, String> hyphenatedTenantGraphs = Map.of();
    private String tenantGraphPrefix = "";
    private boolean createExtension;
    private boolean createGraph = true;
    private boolean createTenantGraphs;

    /**
     * The graph that repositories use when their data source has no graph of its own in {@link #getGraphs()}.
     * Default {@value #DEFAULT_GRAPH}.
     *
     * @return The graph name
     */
    public @NonNull String getGraph() {
        return graph;
    }

    /**
     * @param graph The graph name
     */
    public void setGraph(@NonNull String graph) {
        this.graph = graph;
    }

    /**
     * The graph of each data source, by the name of the data source, for applications whose data sources hold
     * different graphs: {@code micronaut.data.age.graphs.reporting: analytics}.
     *
     * @return The graphs by data source name
     */
    public @NonNull Map<String, String> getGraphs() {
        return graphs;
    }

    /**
     * @param graphs The graphs by data source name
     */
    public void setGraphs(@NonNull Map<String, String> graphs) {
        this.graphs = graphs;
    }

    /**
     * The graph of a data source: its entry in {@link #getGraphs()}, else {@link #getGraph()}. Names are compared in
     * their hyphenated form, as configuration binds them, so the entry {@code reporting-db} is that of the data source
     * {@code reportingDb}.
     *
     * @param dataSource The data source name
     * @return The graph name
     */
    public @NonNull String graphFor(@NonNull String dataSource) {
        String exact = graphs.get(dataSource);
        if (exact != null) {
            return exact;
        }
        String name = NameUtils.hyphenate(dataSource);
        for (Map.Entry<String, String> entry : graphs.entrySet()) {
            if (NameUtils.hyphenate(entry.getKey()).equals(name)) {
                return entry.getValue();
            }
        }
        return graph;
    }

    /**
     * The graph of each tenant, by tenant id, when multi-tenancy keeps a graph per tenant:
     * {@code micronaut.data.age.tenant-graphs.acme: acme_graph}. A tenant without an entry uses its id, after
     * {@link #getTenantGraphPrefix()}, as its graph name.
     *
     * @return The graphs by tenant id
     * @since 5.3.0
     */
    public @NonNull Map<String, String> getTenantGraphs() {
        return tenantGraphs;
    }

    /**
     * @param tenantGraphs The graphs by tenant id
     */
    public void setTenantGraphs(@NonNull Map<String, String> tenantGraphs) {
        this.tenantGraphs = tenantGraphs;
        Map<String, String> hyphenated = new HashMap<>();
        tenantGraphs.forEach((tenant, tenantGraph) -> hyphenated.put(NameUtils.hyphenate(tenant), tenantGraph));
        this.hyphenatedTenantGraphs = hyphenated;
    }

    /**
     * The prefix put before the id of a tenant without an entry in {@link #getTenantGraphs()} to name its graph,
     * for example {@code tenant_}. Default empty: the tenant id is the graph name.
     *
     * @return The prefix
     * @since 5.3.0
     */
    public @NonNull String getTenantGraphPrefix() {
        return tenantGraphPrefix;
    }

    /**
     * @param tenantGraphPrefix The prefix
     */
    public void setTenantGraphPrefix(@NonNull String tenantGraphPrefix) {
        this.tenantGraphPrefix = tenantGraphPrefix;
    }

    /**
     * The graph of a tenant: its entry in {@link #getTenantGraphs()}, else its id after {@link #getTenantGraphPrefix()}.
     * Tenant ids are compared in their hyphenated form, as configuration binds them, so the entry {@code tenant-a} is
     * that of the tenant {@code tenantA}. A tenant cannot use {@link #getGraph()}, a reserved schema of the database, or
     * the graph mapped to another tenant.
     *
     * @param tenantId The tenant id
     * @return The graph name
     * @throws IllegalArgumentException if the graph name is not a valid identifier, or belongs to something else
     * @since 5.3.0
     */
    public @NonNull String tenantGraph(@NonNull String tenantId) {
        String tenant = NameUtils.hyphenate(tenantId);
        String mapped = tenantGraphs.get(tenantId);
        if (mapped == null) {
            mapped = hyphenatedTenantGraphs.get(tenant);
        }
        String name = AgeStatements.validateTenantGraphName(mapped != null ? mapped : tenantGraphPrefix + tenantId, graph);
        for (Map.Entry<String, String> entry : hyphenatedTenantGraphs.entrySet()) {
            if (entry.getValue().equals(name) && !entry.getKey().equals(tenant)) {
                throw new IllegalArgumentException("Apache AGE graph name [" + name + "] of tenant [" + tenantId + "] is mapped to tenant [" + entry.getKey() + "]");
            }
        }
        return name;
    }

    /**
     * Whether to run {@code CREATE EXTENSION IF NOT EXISTS age} on first use. Default false.
     *
     * @return True to create the extension
     */
    public boolean isCreateExtension() {
        return createExtension;
    }

    /**
     * @param createExtension Whether to create the extension
     */
    public void setCreateExtension(boolean createExtension) {
        this.createExtension = createExtension;
    }

    /**
     * Whether to create the configured graph of a data source on first use if it does not exist. Default true. The
     * graphs of tenants are governed by {@link #isCreateTenantGraphs()}.
     *
     * @return True to create the graph
     */
    public boolean isCreateGraph() {
        return createGraph;
    }

    /**
     * @param createGraph Whether to create the graph
     */
    public void setCreateGraph(boolean createGraph) {
        this.createGraph = createGraph;
    }

    /**
     * Whether to create the graph of a tenant on first use if it does not exist. Default false: an operation of a
     * tenant whose graph does not exist fails. Each graph is a schema of the database, so only enable this when tenant
     * ids are trusted: with a tenant id taken from a request, a client could otherwise create schemas at will.
     *
     * @return True to create the graphs of tenants
     * @since 5.3.0
     */
    public boolean isCreateTenantGraphs() {
        return createTenantGraphs;
    }

    /**
     * @param createTenantGraphs Whether to create the graphs of tenants
     * @since 5.3.0
     */
    public void setCreateTenantGraphs(boolean createTenantGraphs) {
        this.createTenantGraphs = createTenantGraphs;
    }
}
