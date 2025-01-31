/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.hibernate.jakarta.data;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataDeleteExceptionConverter;
import jakarta.data.exceptions.DataException;
import jakarta.data.exceptions.EmptyResultException;
import jakarta.data.exceptions.OptimisticLockingFailureException;
import jakarta.inject.Singleton;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.hibernate.StaleStateException;

/**
 * The Hibernate to Jakarta Data exception converter.
 *
 * @author Denis Stepanov
 * @since 4.12
 */
@Internal
@Singleton
@Requires(classes = OptimisticLockingFailureException.class)
final class HibernateJakartaDataDeleteExceptionConverter implements JakartaDataDeleteExceptionConverter {

    @Override
    public Exception convert(Exception exception) {
        if (exception instanceof NonUniqueResultException) {
            throw new jakarta.data.exceptions.NonUniqueResultException(exception.getMessage(), exception);
        }
        if (exception instanceof NoResultException) {
            throw new EmptyResultException(exception.getMessage(), exception);
        }
        if (exception instanceof StaleStateException || exception instanceof OptimisticLockException) {
            throw new OptimisticLockingFailureException(exception.getMessage(), exception);
        }
        if (exception instanceof PersistenceException) {
            return new DataException(exception.getMessage(), exception);
        }
        return exception;
    }
}
