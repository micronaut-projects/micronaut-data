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
package io.micronaut.data.runtime.exceptions.jakarta.data;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataDeleteExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataUpdateExceptionConverter;
import jakarta.data.exceptions.DataException;
import jakarta.data.exceptions.EmptyResultException;
import jakarta.data.exceptions.EntityExistsException;
import jakarta.inject.Singleton;

/**
 * The Micronaut Data to Jakarta Data exception converter.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Singleton
@Requires(classes = jakarta.data.exceptions.OptimisticLockingFailureException.class)
final class MicronautDataJakartaDataExceptionConverter implements JakartaDataExceptionConverter, JakartaDataUpdateExceptionConverter,
    JakartaDataDeleteExceptionConverter, JakartaDataInsertExceptionConverter {

    @Override
    public Exception convert(Exception exception) {
        if (exception instanceof io.micronaut.data.exceptions.EntityExistsException) {
            throw new EntityExistsException(exception.getMessage(), exception);
        }
        if (exception instanceof OptimisticLockException) {
            throw new jakarta.data.exceptions.OptimisticLockingFailureException(exception.getMessage(), exception);
        }
        if (exception instanceof NonUniqueResultException) {
            throw new jakarta.data.exceptions.NonUniqueResultException(exception.getMessage(), exception);
        }
        if (exception instanceof io.micronaut.data.exceptions.EmptyResultException) {
            throw new EmptyResultException(exception.getMessage(), exception);
        }
        if (exception instanceof MappingException) {
            return new DataException(exception.getMessage(), exception);
        }
        if (exception instanceof DataAccessException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Unique index or primary key violation")) {
                throw new EntityExistsException(exception.getMessage(), exception);
            }
            return new DataException(exception.getMessage(), exception);
        }
        return exception;
    }
}
