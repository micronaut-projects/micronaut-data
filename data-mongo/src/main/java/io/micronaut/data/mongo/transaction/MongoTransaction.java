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
package io.micronaut.data.mongo.transaction;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import org.bson.types.ObjectId;

@Internal
final class MongoTransaction implements AutoCloseable {

    private final ObjectId id = new ObjectId();
    private String name;
    private ClientSession clientSession;
    private boolean newClientSession;
    private boolean rollbackOnly = false;

    public MongoTransaction(ClientSession clientSession) {
        this.clientSession = clientSession;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasClientSession() {
        return clientSession != null;
    }

    public void setClientSessionHolder(ClientSession clientSession, boolean newClientSession) {
        this.clientSession = clientSession;
        this.newClientSession = newClientSession;
    }

    public void beginTransaction(TransactionOptions transactionOptions) {
        clientSession.startTransaction(transactionOptions);
    }

    public void commitTransaction() {
        clientSession.commitTransaction();
    }

    public void abortTransaction() {
        clientSession.abortTransaction();
    }

    public boolean hasActiveTransaction() {
        return clientSession != null && clientSession.hasActiveTransaction();
    }

    @Nullable
    public ClientSession getClientSession() {
        return clientSession;
    }

    public boolean isNewClientSession() {
        return newClientSession;
    }

    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    public void close() {
        if (newClientSession) {
            newClientSession = false;
            clientSession.close();
            clientSession = null;
        }
    }

    @Override
    public String toString() {
        if (name != null) {
            return name + " " + id.toHexString();
        }
        return id.toHexString();
    }
}
