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
package io.micronaut.data.mongodb.exceptions.jakarta.data;

import com.mongodb.MongoWriteException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataDeleteExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataUpdateExceptionConverter;
import jakarta.data.exceptions.EntityExistsException;
import jakarta.inject.Singleton;

/**
 * The Micronaut Data to Jakarta Data exception converter.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Singleton
@Requires(classes = jakarta.data.exceptions.OptimisticLockingFailureException.class)
final class MongoJakartaDataExceptionConverter implements JakartaDataExceptionConverter, JakartaDataUpdateExceptionConverter,
    JakartaDataDeleteExceptionConverter, JakartaDataInsertExceptionConverter {

    @Override
    public Exception convert(Exception exception) {
        if (exception instanceof DataAccessException) {
            if (exception.getCause() instanceof MongoWriteException e && e.getError().getCode() == 11000) {
                throw new EntityExistsException(exception.getMessage(), e);
            }
        }
        return exception;
    }
}
