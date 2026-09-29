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
package io.micronaut.data.model.query.builder.sql.validation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A validator for SQLite table mappings.
 * <p>
 * SQLite uses dynamic typing, a declared column type only determines the column type affinity.
 * The column types are therefore matched by their affinity (see <a href="https://www.sqlite.org/datatype3.html">Datatypes In SQLite</a>).
 *
 * @since 5.3.0
 */
@Internal
@Singleton
final class SqliteSqlTableMappingValidator extends BaseSqlTableMappingValidator {

    @Override
    public Dialect getSupportedDialect() {
        return Dialect.SQLITE;
    }

    @Override
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping,
                                                SqlColumnMetadata columnMetadata,
                                                SqlDialectOptions dialectOptions) {
        return affinity(columnMapping.getSqlType(dialectOptions)).equals(affinity(columnMetadata.typeName()));
    }

    @Override
    protected boolean matchingDialectDefinedColumnType(String expectedType, SqlColumnMetadata columnMetadata, SqlDialectOptions dialectOptions) {
        return affinity(expectedType).equals(affinity(columnMetadata.typeName()));
    }

    /**
     * The SQLite type affinity of the declared type, determined by the SQLite rules applied in order
     * (see <a href="https://www.sqlite.org/datatype3.html#determination_of_column_affinity">Determination Of Column Affinity</a>):
     * a name containing {@code INT} is INTEGER, {@code CHAR}, {@code CLOB} or {@code TEXT} is TEXT, {@code BLOB} or no type is BLOB,
     * {@code REAL}, {@code FLOA} or {@code DOUB} (FLOAT, DOUBLE, DOUBLE PRECISION) is REAL, anything else NUMERIC.
     * The order matters, SQLite gives {@code FLOATING POINT} the INTEGER affinity.
     */
    private static String affinity(@Nullable String typeName) {
        String type = typeName == null ? "" : typeName.toUpperCase(Locale.ENGLISH);
        if (type.contains("INT")) {
            return "INTEGER";
        }
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) {
            return "TEXT";
        }
        if (type.isBlank() || type.contains("BLOB")) {
            return "BLOB";
        }
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) {
            return "REAL";
        }
        return "NUMERIC";
    }
}
