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
package io.micronaut.data.cosmos.config;

/**
 * The settings for Cosmos container.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class CosmosContainerSettings {

    private String partitionKeyPath;

    private CosmosDatabaseConfiguration.ThroughputSettings throughputSettings;

    /**
     * @return the partition key path for the container
     */
    public String getPartitionKeyPath() {
        return partitionKeyPath;
    }

    /**
     * Sets the container partition key path.
     *
     * @param partitionKeyPath the partition key path
     */
    public void setPartitionKeyPath(String partitionKeyPath) {
        this.partitionKeyPath = partitionKeyPath;
    }

    /**
     * @return container throughput settings
     */
    public CosmosDatabaseConfiguration.ThroughputSettings getThroughputSettings() {
        return throughputSettings;
    }

    /**
     * Sets the container throughput settings.
     *
     * @param throughputSettings the throughput settings
     */
    public void setThroughputSettings(CosmosDatabaseConfiguration.ThroughputSettings throughputSettings) {
        this.throughputSettings = throughputSettings;
    }
}
