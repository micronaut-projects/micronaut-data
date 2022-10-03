/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.cosmos.common;

import com.azure.cosmos.models.ThroughputProperties;

/**
 * The model containing values read from {@link io.micronaut.data.cosmos.annotation.CosmosContainerDef}.
 */
public class CosmosContainerProps {

    private final String containerName;
    private final String partitionKeyPath;
    private final ThroughputProperties throughputProperties;

    /**
     * Creates an instance of {@link CosmosContainerProps}.
     *
     * @param containerName the container name
     * @param partitionKeyPath the partition key path, may be blank
     * @param throughputProperties the throughput properties for the container, can be null and then not used on the container
     */
    public CosmosContainerProps(String containerName, String partitionKeyPath, ThroughputProperties throughputProperties) {
        this.containerName = containerName;
        this.partitionKeyPath = partitionKeyPath;
        this.throughputProperties = throughputProperties;
    }

    /**
     * @return the container name
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * @return the partition key path for the container, can be empty
     */
    public String getPartitionKeyPath() {
        return partitionKeyPath;
    }

    /**
     * @return the container throughput properties, can be null
     */
    public ThroughputProperties getThroughputProperties() {
        return throughputProperties;
    }
}
