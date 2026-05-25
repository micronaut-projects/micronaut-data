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
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
@Requires(classes = {HttpRequest.class, MutableHttpResponse.class})
@Requires(property = OracleSessionlessTransactionHttpConfiguration.PREFIX + ".propagation-enabled", value = StringUtils.TRUE)
final class OracleSessionlessTransactionHttpServerFilter {

    private final OracleSessionlessTransactionHttpConfiguration configuration;
    private final OracleSessionlessTransactionIdCodec transactionIdCodec;

    OracleSessionlessTransactionHttpServerFilter(OracleSessionlessTransactionHttpConfiguration configuration,
                                                 OracleSessionlessTransactionIdCodec transactionIdCodec) {
        this.configuration = configuration;
        this.transactionIdCodec = transactionIdCodec;
    }

    @RequestFilter
    void readTransactionId(HttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
        OracleSessionlessTransactionState state = new OracleSessionlessTransactionState();
        Optional<String> value = request.getHeaders().findFirst(configuration.getHeaderName());
        if (value.isPresent()) {
            try {
                state.setGtrid(transactionIdCodec.decode(value.get()));
            } catch (IllegalArgumentException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid Oracle sessionless transaction id");
            }
        }
        replaceTransactionState(mutablePropagatedContext, state);
    }

    @ResponseFilter
    void writeTransactionId(MutableHttpResponse<?> response, MutablePropagatedContext mutablePropagatedContext) {
        PropagatedContext propagatedContext = mutablePropagatedContext.getContext();
        if (propagatedContext != null) {
            propagatedContext.findAll(OracleSessionlessTransactionState.class)
                .findFirst()
                .flatMap(OracleSessionlessTransactionState::getGtrid)
                .map(transactionIdCodec::encode)
                .ifPresent(transactionId -> response.getHeaders().set(configuration.getHeaderName(), transactionId));
        }
    }

    private static void replaceTransactionState(MutablePropagatedContext mutablePropagatedContext,
                                                OracleSessionlessTransactionState transactionState) {
        PropagatedContext propagatedContext = mutablePropagatedContext.getContext();
        if (propagatedContext != null) {
            List<OracleSessionlessTransactionState> transactionStates = propagatedContext.findAll(OracleSessionlessTransactionState.class).toList();
            for (OracleSessionlessTransactionState existingTransactionState : transactionStates) {
                mutablePropagatedContext.remove(existingTransactionState);
            }
        }
        mutablePropagatedContext.add(transactionState);
    }
}
