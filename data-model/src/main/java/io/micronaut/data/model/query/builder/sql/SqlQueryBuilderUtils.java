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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;

import java.lang.annotation.Annotation;
import java.sql.Blob;
import java.sql.Clob;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The utility methods for query builders.
 */
@Internal
final class SqlQueryBuilderUtils {

    private SqlQueryBuilderUtils() { }

    /**
     * Adds column type for the column for creating table.
     *
     * @param prop the persistent property
     * @param column the column name
     * @param dialect the SQL dialect
     * @param required an indicator telling whether column is required or not
     * @return column containing type definition
     */
    static String addTypeToColumn(PersistentProperty prop, String column, Dialect dialect, boolean required) {
        if (prop instanceof Association) {
            throw new IllegalStateException("Association is not supported here");
        }
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        String definition = annotationMetadata.stringValue(MappedProperty.class, "definition").orElse(null);
        DataType dataType = prop.getDataType();
        if (definition != null) {
            return column + " " + definition;
        }
        OptionalInt precision = findPersistenceColumnValue(annotationMetadata, "precision");
        OptionalInt scale = findPersistenceColumnValue(annotationMetadata, "scale");

        switch (dataType) {
            case STRING:
                int stringLength = annotationMetadata.findAnnotation("jakarta.validation.constraints.Size$List")
                    .flatMap(v -> {
                        Optional value = v.getValue(AnnotationValue.class);
                        return (Optional<AnnotationValue<Annotation>>) value;
                    }).map(v -> v.intValue("max"))
                    .orElseGet(() -> findPersistenceColumnValue(annotationMetadata, "length"))
                    .orElse(255);

                column += " VARCHAR(" + stringLength + ")";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case UUID:
                if (dialect == Dialect.ORACLE || dialect == Dialect.MYSQL) {
                    column += " VARCHAR(36)";
                } else if (dialect == Dialect.SQL_SERVER) {
                    column += " UNIQUEIDENTIFIER";
                } else {
                    column += " UUID";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BOOLEAN:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(3)";
                } else if (dialect == Dialect.SQL_SERVER) {
                    column += " BIT NOT NULL";
                } else {
                    column += " BOOLEAN";
                    if (required) {
                        column += " NOT NULL";
                    }
                }
                break;
            case TIMESTAMP:
                if (dialect == Dialect.ORACLE) {
                    column += " TIMESTAMP";
                    if (required) {
                        column += " NOT NULL";
                    }
                } else if (dialect == Dialect.SQL_SERVER) {
                    // sql server timestamp is an internal type, use datetime instead
                    column += " DATETIME2";
                    if (required) {
                        column += " NOT NULL";
                    }
                } else if (dialect == Dialect.MYSQL) {
                    // mysql doesn't allow timestamp without default
                    column += " TIMESTAMP(6) DEFAULT NOW(6)";
                } else {
                    column += " TIMESTAMP";
                    if (required) {
                        column += " NOT NULL";
                    }
                }
                break;
            case DATE:
                column += " DATE";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case TIME:
                if (dialect == Dialect.ORACLE) {
                    // OracleDB doesn't have a TIME type, so DATE is used
                    column += " DATE ";
                } else {
                    column += " TIME(6) ";
                }
                if (required) {
                    column += " NOT NULL ";
                }
                break;
            case LONG:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(19)";
                } else {
                    column += " BIGINT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case CHARACTER:
                column += " CHAR(1)";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case INTEGER:
                if (precision.isPresent()) {
                    String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                    column += " " + numericName + "(" + precision.getAsInt() + ")";
                } else if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(10)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " INTEGER";
                } else {
                    column += " INT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BIGDECIMAL:
                if (precision.isPresent()) {
                    if (scale.isPresent()) {
                        String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                        column += " " + numericName + "(" + precision.getAsInt() + "," + scale.getAsInt() + ")";
                    } else {
                        column += " FLOAT(" + precision.getAsInt() + ")";
                    }
                } else if (dialect == Dialect.ORACLE) {
                    column += " FLOAT(126)";
                } else {
                    column += " DECIMAL";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case FLOAT:
                if (precision.isPresent()) {
                    if (scale.isPresent()) {
                        String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                        column += " " + numericName + "(" + precision.getAsInt() + "," + scale.getAsInt() + ")";
                    } else {
                        column += " FLOAT(" + precision.getAsInt() + ")";
                    }
                } else if (dialect == Dialect.ORACLE || dialect == Dialect.SQL_SERVER) {
                    column += " FLOAT(53)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " REAL";
                } else {
                    column += " FLOAT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BYTE_ARRAY:
                if (dialect == Dialect.POSTGRES) {
                    column += " BYTEA";
                } else if (dialect == Dialect.SQL_SERVER) {
                    column += " VARBINARY(MAX)";
                } else if (dialect == Dialect.ORACLE) {
                    column += " BLOB";
                } else {
                    column += " BLOB";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case DOUBLE:
                if (precision.isPresent()) {
                    if (scale.isPresent()) {
                        String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                        column += " " + numericName + "(" + precision.getAsInt() + "," + scale.getAsInt() + ")";
                    } else {
                        column += " FLOAT(" + precision.getAsInt() + ")";
                    }
                } else if (dialect == Dialect.ORACLE) {
                    column += " FLOAT(23)";
                } else if (dialect == Dialect.MYSQL || dialect == Dialect.H2) {
                    column += " DOUBLE";
                } else {
                    column += " DOUBLE PRECISION";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case SHORT:
            case BYTE:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(5)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " SMALLINT";
                } else {
                    column += " TINYINT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case JSON:
                switch (dialect) {
                    case POSTGRES:
                        column += " JSONB";
                        break;
                    case SQL_SERVER:
                        column += " NVARCHAR(MAX)";
                        break;
                    case ORACLE:
                        column += " CLOB";
                        break;
                    default:
                        column += " JSON";
                        break;
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case STRING_ARRAY:
            case CHARACTER_ARRAY:
                column += " VARCHAR(255) ARRAY";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case SHORT_ARRAY:
                if (dialect == Dialect.POSTGRES) {
                    column += " SMALLINT ARRAY";
                } else {
                    column += " TINYINT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case INTEGER_ARRAY:
                if (dialect == Dialect.POSTGRES || dialect == Dialect.H2) {
                    column += " INTEGER ARRAY";
                } else {
                    column += " INT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case LONG_ARRAY:
                column += " BIGINT ARRAY";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case FLOAT_ARRAY:
                if (dialect == Dialect.H2 || dialect == Dialect.POSTGRES) {
                    column += " REAL ARRAY";
                } else {
                    column += " FLOAT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case DOUBLE_ARRAY:
                if (dialect == Dialect.POSTGRES || dialect == Dialect.H2) {
                    column += " DOUBLE PRECISION ARRAY";
                } else {
                    column += " DOUBLE ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BOOLEAN_ARRAY:
                column += " BOOLEAN ARRAY";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            default:
                if (prop.isEnum()) {
                    column += " VARCHAR(255)";
                    if (required) {
                        column += " NOT NULL";
                    }
                    break;
                } else if (prop.isAssignable(Clob.class)) {
                    if (dialect == Dialect.POSTGRES) {
                        column += " TEXT";
                    } else {
                        column += " CLOB";
                    }
                    if (required) {
                        column += " NOT NULL";
                    }
                    break;
                } else if (prop.isAssignable(Blob.class)) {
                    if (dialect == Dialect.POSTGRES) {
                        column += " BYTEA";
                    } else {
                        column += " BLOB";
                    }
                    if (required) {
                        column += " NOT NULL";
                    }
                    break;
                } else {
                    throw new MappingException("Unable to create table column for property [" + prop.getName() + "] of entity [" + prop.getOwner().getName() + "] with unknown data type: " + dataType);
                }
        }
        return column;
    }

    /**
     * Finds int value for javax.persistence.Column given value, if not present falls back to jakarta.persistence.Column.
     *
     * @param annotationMetadata the annotation metadata
     * @param value the annotation value to be looked at
     * @return OptionalInt for given annotation value
     */
    private static OptionalInt findPersistenceColumnValue(AnnotationMetadata annotationMetadata, String value) {
        String annotationName = "javax.persistence.Column";
        OptionalInt optionalInt = annotationMetadata.intValue(annotationName, value);
        if (optionalInt.isEmpty()) {
            annotationName = "jakarta.persistence.Column";
            optionalInt = annotationMetadata.intValue(annotationName, value);
        }
        return optionalInt;
    }
}
