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
package io.micronaut.data.jdbc.exceptions.jakarta.data;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JdbcJakartaDataExceptionConverterTest {

    private final JdbcJakartaDataExceptionConverter converter = new JdbcJakartaDataExceptionConverter();

    @Test
    void convertsDatabaseUniqueConstraintViolationsToMicronautDataException() {
        assertEntityExists(new SQLException("Unique index or primary key violation", "23505", 23505));
        assertEntityExists(new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062));
        assertEntityExists(new SQLException("ORA-00001: unique constraint violated", "23000", 1));
        assertEntityExists(new SQLException("Violation of PRIMARY KEY constraint", "23000", 2627));
        assertEntityExists(new SQLException("Cannot insert duplicate key row", "23000", 2601));
    }

    @Test
    void convertsWrappedDatabaseUniqueConstraintViolationsToMicronautDataException() {
        SQLException sqlException = new SQLException("duplicate key value violates unique constraint", "23505", 0);
        Exception converted = converter.convert(new DataAccessException("SQL error executing INSERT", sqlException));

        assertInstanceOf(EntityExistsException.class, converted);
    }

    @Test
    void convertsOtherSqlExceptionsToMicronautDataAccessException() {
        Exception converted = converter.convert(new SQLException("syntax error", "42000", 0));

        assertInstanceOf(DataAccessException.class, converted);
    }

    @Test
    void runsBeforeJakartaDataExceptionConverter() {
        try (ApplicationContext context = ApplicationContext.run()) {
            List<JakartaDataInsertExceptionConverter> converters = new ArrayList<>(
                context.getBeansOfType(JakartaDataInsertExceptionConverter.class)
            );

            assertInstanceOf(JdbcJakartaDataExceptionConverter.class, converters.get(0));

            Exception converted = new DataAccessException(
                "SQL error executing INSERT",
                new SQLException("Unique index or primary key violation", "23505", 23505)
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
