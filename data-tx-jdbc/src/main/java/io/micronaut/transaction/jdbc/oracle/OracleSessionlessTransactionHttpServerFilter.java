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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Bridges Oracle sessionless transaction ids between HTTP headers and propagated context.
 */
@Internal
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
@Requires(classes = {HttpRequest.class, MutableHttpResponse.class, ServerFilter.class})
@Requires(property = OracleSessionlessTransactionHttpConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE)
final class OracleSessionlessTransactionHttpServerFilter {

    private final OracleSessionlessTransactionHttpConfiguration configuration;

    OracleSessionlessTransactionHttpServerFilter(OracleSessionlessTransactionHttpConfiguration configuration) {
        this.configuration = configuration;
    }

    @RequestFilter
    void readTransactionId(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
        Optional<String> value = request.getHeaders().findFirst(configuration.getHeaderName());
        if (value.isEmpty()) {
            return;
        }
        try {
            replaceTransactionContext(propagatedContext, OracleSessionlessTransactionContext.decode(value.get()));
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid Oracle sessionless transaction id");
        }
    }

    @ResponseFilter
    void writeTransactionId(MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
        PropagatedContext context = propagatedContext.getContext();
        Optional<OracleSessionlessTransactionContext> transactionContext = OracleSessionlessTransactionContext.find(context == null ? PropagatedContext.empty() : context);
        if (transactionContext.isPresent()) {
            response.getHeaders().set(configuration.getHeaderName(), transactionContext.get().encode());
        }
    }

    private static void replaceTransactionContext(MutablePropagatedContext propagatedContext,
                                                  OracleSessionlessTransactionContext transactionContext) {
        PropagatedContext context = propagatedContext.getContext();
        if (context != null) {
            List<OracleSessionlessTransactionContext> transactionContexts = context.findAll(OracleSessionlessTransactionContext.class).toList();
            for (OracleSessionlessTransactionContext existingTransactionContext : transactionContexts) {
                propagatedContext.remove(existingTransactionContext);
            }
        }
        propagatedContext.add(transactionContext);
    }
}
