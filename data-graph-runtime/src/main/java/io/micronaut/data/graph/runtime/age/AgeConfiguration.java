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
    private boolean createExtension;
    private boolean createGraph = true;

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
     * The graph of a data source: its entry in {@link #getGraphs()}, else {@link #getGraph()}.
     *
     * @param dataSource The data source name
     * @return The graph name
     */
    public @NonNull String graphFor(@NonNull String dataSource) {
        return graphs.getOrDefault(dataSource, graph);
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
     * Whether to create the graph on first use if it does not exist. Default true.
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
}
