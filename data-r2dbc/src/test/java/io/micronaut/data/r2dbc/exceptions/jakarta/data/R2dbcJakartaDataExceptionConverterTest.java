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
package io.micronaut.data.r2dbc.exceptions.jakarta.data;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.DataIntegrityViolationException;
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.R2dbcException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class R2dbcJakartaDataExceptionConverterTest {

    private final R2dbcJakartaDataExceptionConverter converter = new R2dbcJakartaDataExceptionConverter();

    @Test
    void convertsDatabaseUniqueConstraintViolationsToMicronautDataException() {
        assertEntityExists(new R2dbcDataIntegrityViolationException("Unique index or primary key violation", "23505", 23505));
        assertEntityExists(new R2dbcDataIntegrityViolationException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062));
        assertEntityExists(new R2dbcDataIntegrityViolationException("ORA-00001: unique constraint violated", "23000", 1));
        assertEntityExists(new R2dbcDataIntegrityViolationException("Violation of PRIMARY KEY constraint", "23000", 2627));
        assertEntityExists(new R2dbcDataIntegrityViolationException("Cannot insert duplicate key row", "23000", 2601));
    }

    @Test
    void convertsWrappedDatabaseUniqueConstraintViolationsToMicronautDataException() {
        R2dbcDataIntegrityViolationException r2dbcException =
            new R2dbcDataIntegrityViolationException("duplicate key value violates unique constraint", "23505", 0);
        Exception converted = converter.convert(new DataAccessException("SQL error executing INSERT", r2dbcException));

        assertInstanceOf(EntityExistsException.class, converted);
    }

    @Test
    void convertsNonUniqueIntegrityConstraintViolationsToMicronautDataIntegrityViolationException() {
        Exception converted = converter.convert(new R2dbcDataIntegrityViolationException("not null violation", "23502", 0));

        assertInstanceOf(DataIntegrityViolationException.class, converted);
    }

    @Test
    void doesNotWrapExistingDataIntegrityViolationException() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
            "Data integrity violation",
            new R2dbcDataIntegrityViolationException("not null violation", "23502", 0)
        );

        assertSame(exception, converter.convert(exception));
    }

    @Test
    void convertsCausedNonUniqueIntegrityConstraintViolationsToMicronautDataIntegrityViolationException() {
        R2dbcException exception = new R2dbcException(
            "batch update failed", "HY000", 0,
            new R2dbcDataIntegrityViolationException("not null violation", "23502", 0)
        ) {
        };

        Exception converted = converter.convert(exception);

        assertInstanceOf(DataIntegrityViolationException.class, converted);
    }

    @Test
    void runsBeforeJakartaDataExceptionConverter() {
        try (ApplicationContext context = ApplicationContext.run()) {
            List<JakartaDataInsertExceptionConverter> converters = new ArrayList<>(
                context.getBeansOfType(JakartaDataInsertExceptionConverter.class)
            );

            assertInstanceOf(R2dbcJakartaDataExceptionConverter.class, converters.get(0));

            Exception converted = new DataAccessException(
                "SQL error executing INSERT",
                new R2dbcDataIntegrityViolationException("Unique index or primary key violation", "23505", 23505)
            );
            for (JakartaDataInsertExceptionConverter exceptionConverter : converters) {
                try {
                    converted = exceptionConverter.convert(converted);
                } catch (Exception e) {
                    converted = e;
                    break;
                }
            }

            assertInstanceOf(jakarta.data.exceptions.EntityExistsException.class, converted);
            assertInstanceOf(EntityExistsException.class, converted.getCause());
        }
    }

    private void assertEntityExists(Exception exception) {
        Exception converted = converter.convert(exception);

        assertInstanceOf(EntityExistsException.class, converted);
    }
}
