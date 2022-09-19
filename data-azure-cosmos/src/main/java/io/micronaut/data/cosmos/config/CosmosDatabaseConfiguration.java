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

    private Integer throughputRequestUnits;

    private boolean throughputAutoScale;

    private String databaseName;

    /**
     * @return throughput request units for the database
     */
    public Integer getThroughputRequestUnits() {
        return throughputRequestUnits;
    }

    /**
     * Sets the throughput request units for the database.
     *
     * @param throughputRequestUnits the throughput request units for the database.
     */
    public void setThroughputRequestUnits(Integer throughputRequestUnits) {
        this.throughputRequestUnits = throughputRequestUnits;
    }

    /**
     * @return an indicator telling whether throughput is auto-scaled
     */
    public boolean isThroughputAutoScale() {
        return throughputAutoScale;
    }

    /**
     * Sets an indicator telling whether throughput is auto-scaled.
     *
     * @param throughputAutoScale an indicator telling whether throughput is auto-scaled
     */
    public void setThroughputAutoScale(boolean throughputAutoScale) {
        this.throughputAutoScale = throughputAutoScale;
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
}
