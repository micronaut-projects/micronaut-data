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

import com.azure.cosmos.CosmosDiagnostics;
import com.azure.cosmos.CosmosException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.cosmos.operations.CosmosDiagnosticsProcessor;
import reactor.core.Exceptions;

/**
 * The utility class for Cosmos operations, currently used for diagnostic processing and error handling.
 *
 * @author radovanradic
 * @since 3.9.0
 */
@Internal
public final class CosmosUtils {

    private CosmosUtils() { }

    /**
     * Returns {@link CosmosAccessException} and processes diagnostics if wrapped exception is {@link CosmosException}.
     *
     * @param cosmosDiagnosticsProcessor the Cosmos response diagnostics processor
     * @param operationName the operation name
     * @param message the detail message
     * @param throwable exception
     * @return CosmosAccessException for operations on Cosmos Db
     */
    public static CosmosAccessException cosmosAccessException(CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor, String operationName, String message, Throwable throwable) {
        if (StringUtils.isEmpty(message)) {
            message = "Failed to access cosmos db database";
        }
        //  Unwrap the exception in case if it is a reactive exception
        final Throwable unwrappedThrowable = Exceptions.unwrap(throwable);
        if (unwrappedThrowable instanceof CosmosException && cosmosDiagnosticsProcessor != null) {
            CosmosException cosmosException = (CosmosException) unwrappedThrowable;
            processDiagnostics(cosmosDiagnosticsProcessor, operationName, cosmosException.getDiagnostics(), cosmosException.getActivityId(),
                cosmosException.getRequestCharge());
        }
        return new CosmosAccessException(message, unwrappedThrowable);
    }

    /**
     * Processes diagnostics from the Cosmos response.
     *
     * @param cosmosDiagnosticsProcessor the Cosmos response diagnostics processor
     * @param operationName the operation name
     * @param cosmosDiagnostics the Cosmos diagnostics
     * @param activityId the activity id (will be null in case of cross partition queries)
     * @param requestCharge the request charge for the operation
     */
    public static void processDiagnostics(CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor, String operationName, @Nullable CosmosDiagnostics cosmosDiagnostics,
                                          @Nullable String activityId, double requestCharge) {
        if (cosmosDiagnosticsProcessor == null) {
            return;
        }
        cosmosDiagnosticsProcessor.processDiagnostics(operationName, cosmosDiagnostics, activityId, requestCharge);
    }

}
