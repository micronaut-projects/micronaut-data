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
package io.micronaut.data.jpa.operations;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.operations.PrimaryRepositoryOperations;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.Serializable;

/**
 * Operations interface specific to JPA.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface JpaRepositoryOperations extends PrimaryRepositoryOperations {

    /**
     * @return The currrent entity manager
     */
    @NonNull
    EntityManager getCurrentEntityManager();

    /**
     * @return The entity manager factory
     */
    @NonNull
    EntityManagerFactory getEntityManagerFactory();

    /**
     * Create an uninitialized proxy.
     * @param type the entity type
     * @param id the entity id
     * @param <T> the entity type
     * @return an uninitialized proxy
     */
    @NonNull
    <T> T load(@NonNull Class<T> type, @NonNull Serializable id);

    /**
     * Flush the current session.
     */
    void flush();
}
