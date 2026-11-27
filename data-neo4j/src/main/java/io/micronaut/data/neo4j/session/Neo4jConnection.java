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
import org.neo4j.driver.QueryRunner;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;

/**
 * A Neo4j session together with the transaction open in it, if any.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class Neo4jConnection {

    private final Session session;
    private @Nullable Transaction transaction;

    /**
     * @param session The session
     */
    public Neo4jConnection(Session session) {
        this.session = session;
    }

    /**
     * @return The session
     */
    public Session getSession() {
        return session;
    }

    /**
     * @return The open transaction, or null
     */
    public @Nullable Transaction getTransaction() {
        return transaction;
    }

    /**
     * @return The transaction if one is open, otherwise the session for auto-commit queries
     */
    public QueryRunner getQueryRunner() {
        Transaction tx = transaction;
        return tx != null && tx.isOpen() ? tx : session;
    }

    /**
     * @param transaction The transaction now open in the session, or null when it ended
     */
    public void setTransaction(@Nullable Transaction transaction) {
        this.transaction = transaction;
    }
}
