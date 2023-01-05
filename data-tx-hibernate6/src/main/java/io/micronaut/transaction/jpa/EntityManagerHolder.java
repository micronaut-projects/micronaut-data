/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.transaction.jpa;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.SavepointManager;
import io.micronaut.transaction.support.ResourceHolderSupport;

import jakarta.persistence.EntityManager;
import java.util.Objects;

/**
 * Resource holder wrapping a JPA {@link EntityManager}.
 * {@code JpaTransactionManager} binds instances of this class to the thread,
 * for a given {@link jakarta.persistence.EntityManagerFactory}.
 *
 * <p>Also serves as a base class for {@link org.springframework.orm.hibernate5.SessionHolder},
 * as of 5.1.
 *
 * <p>Note: This is an SPI class, not intended to be used by applications.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 2.0
 */
public class EntityManagerHolder extends ResourceHolderSupport {

    @Nullable
    private final EntityManager entityManager;

    private boolean transactionActive;

    @Nullable
    private SavepointManager savepointManager;

    /**
     * Default constructor.
     * @param entityManager The entity manager
     */
    public EntityManagerHolder(@Nullable EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * @return The entity manager
     */
    @NonNull
    public EntityManager getEntityManager() {
        Objects.requireNonNull(this.entityManager, "No EntityManager available");
        return this.entityManager;
    }

    /**
     * @param transactionActive Sets the transaction as active.
     */
    protected void setTransactionActive(boolean transactionActive) {
        this.transactionActive = transactionActive;
    }

    /**
     * @return Whether the transaction is ative
     */
    protected boolean isTransactionActive() {
        return this.transactionActive;
    }

    /**
     * @param savepointManager Sets the save point manager
     */
    protected void setSavepointManager(@Nullable SavepointManager savepointManager) {
        this.savepointManager = savepointManager;
    }

    /**
     * @return The save point manager
     */
    @Nullable
    protected SavepointManager getSavepointManager() {
        return this.savepointManager;
    }

    @Override
    public void clear() {
        super.clear();
        this.transactionActive = false;
        this.savepointManager = null;
    }

}
