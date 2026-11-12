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
package io.micronaut.data.neo4j.session;

import org.jspecify.annotations.Nullable;
import org.neo4j.driver.reactivestreams.ReactiveQueryRunner;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.reactivestreams.ReactiveTransaction;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A reactive Neo4j session with the transaction open in it, if any.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class Neo4jReactiveConnection {

    private final ReactiveSession session;
    private final AtomicReference<@Nullable ReactiveTransaction> transaction = new AtomicReference<>();

    /**
     * @param session The reactive session
     */
    public Neo4jReactiveConnection(ReactiveSession session) {
        this.session = session;
    }

    /**
     * @return The reactive session
     */
    public ReactiveSession getSession() {
        return session;
    }

    /**
     * @return The open transaction, or null
     */
    public @Nullable ReactiveTransaction getTransaction() {
        return transaction.get();
    }

    /**
     * @param transaction The transaction now open in the session, or null when it ended
     */
    public void setTransaction(@Nullable ReactiveTransaction transaction) {
        this.transaction.set(transaction);
    }

    /**
     * @return The transaction if one is open, otherwise the session for auto-commit queries
     */
    public ReactiveQueryRunner getQueryRunner() {
        ReactiveTransaction tx = transaction.get();
        return tx != null ? tx : session;
    }
}
