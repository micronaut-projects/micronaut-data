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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration.PREFIX;

/**
 * The Azure Cosmos database configuration.
 *
 * @author radovanradic
 * @since 3.8.0
 */
@ConfigurationProperties(PREFIX)
public final class CosmosDatabaseConfiguration {

    public static final String PREFIX = "azure.cosmos.database";

    public static final String UPDATE_POLICY = PREFIX + ".update-policy";

    private ThroughputSettings throughput;

    private Map<String, CosmosContainerSettings> cosmosContainerSettings = new HashMap<>();

    private String databaseName;

    private StorageUpdatePolicy updatePolicy = StorageUpdatePolicy.NONE;

    private List<String> packages = new ArrayList<>();

    public ThroughputSettings getThroughput() {
        return throughput;
    }

    public void setThroughput(ThroughputSettings throughput) {
        this.throughput = throughput;
    }

    public Map<String, CosmosContainerSettings> getCosmosContainerSettings() {
        return cosmosContainerSettings;
    }

    public void setCosmosContainerSettings(Map<String, CosmosContainerSettings> cosmosContainerSettings) {
        this.cosmosContainerSettings = cosmosContainerSettings;
    }

    /**
     * @return the database name
     */
    @NonNull
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Sets the database name.
     *
     * @param databaseName the database name
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * @return the update policy for the database to be used during startup.
     */
    public StorageUpdatePolicy getUpdatePolicy() {
        return updatePolicy;
    }

    /**
     * Sets the update policy for the database to be used during startup.
     *
     * @param updatePolicy the update policy for the database
     */
    public void setUpdatePolicy(StorageUpdatePolicy updatePolicy) {
        this.updatePolicy = updatePolicy;
    }

    /**
     * @return the list of package names to filter entities during init database and containers
     */
    public List<String> getPackages() {
        return packages;
    }

    /**
     * @param packages the package names to be considered during init
     */
    public void setPackages(List<String> packages) {
        this.packages = packages;
    }
}
