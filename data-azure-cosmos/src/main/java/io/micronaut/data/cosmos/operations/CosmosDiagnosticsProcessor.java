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
package io.micronaut.data.cosmos.operations;

import com.azure.cosmos.CosmosDiagnostics;
import io.micronaut.core.annotation.Nullable;

/**
 * The Cosmos diagnostics processor interface. Implementations can log diagnostics or perform some metrics, but it is important
 * that processing doesn't take much time since it is being called from all Cosmos operations.
 *
 * @author radovanradic
 * @since 3.9.0
 */
public interface CosmosDiagnosticsProcessor {

    String CREATE_DATABASE_IF_NOT_EXISTS = "CreateDatabaseIfNotExists";
    String REPLACE_DATABASE_THROUGHPUT = "ReplaceDatabaseThroughput";
    String CREATE_CONTAINER_IF_NOT_EXISTS = "CreateContainerIfNotExists";
    String REPLACE_CONTAINER_THROUGHPUT = "ReplaceContainerThroughput";
    String REPLACE_CONTAINER = "ReplaceContainer";
    String QUERY_ITEMS = "QueryItems";
    String EXECUTE_BULK = "ExecuteBulk";
    String CREATE_ITEM = "CreateItem";
    String REPLACE_ITEM = "ReplaceItem";
    String DELETE_ITEM = "DeleteItem";

    /**
     * Process diagnostics from the Cosmos response.
     *
     * @param operationName the operation name
     * @param cosmosDiagnostics the Cosmos diagnostics
     * @param activityId the activity id (will be null in case of cross partition queries)
     * @param requestCharge the request charge for the operation
     */
    void processDiagnostics(String operationName, @Nullable CosmosDiagnostics cosmosDiagnostics, @Nullable String activityId, double requestCharge);
}
