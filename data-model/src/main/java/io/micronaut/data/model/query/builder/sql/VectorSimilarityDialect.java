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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.annotation.VectorStorage;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.search.VectorStorageShapeResolver;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Internal SQL rendering strategy for vector-score expressions.
 *
 * <p>This contract converts the logical score function ({@code mn_vector_score}) used by
 * criteria/query generation into dialect-specific SQL fragments.</p>
 *
 * @since 5.0.0
 */
interface VectorSimilarityDialect {

    /**
     * Appends a dialect-specific vector score expression.
     *
     * @param query target SQL buffer
     * @param left left vector expression (usually persisted column)
     * @param right right vector expression (usually query vector parameter)
     * @param appendExpression callback to render child expressions
     */
    void appendVectorScore(StringBuilder query,
                           Expression<?> left,
                           Expression<?> right,
                           Consumer<Expression<?>> appendExpression);

    /**
     * Resolves the vector similarity dialect strategy for a SQL dialect.
     *
     * @param dialect the SQL dialect
     * @return matching vector similarity strategy, or {@code null} when unsupported
     */
    static @Nullable VectorSimilarityDialect forDialect(Dialect dialect) {
        return switch (dialect) {
            case POSTGRES -> PostgresVectorSimilarityDialect.INSTANCE;
            case ORACLE -> OracleVectorSimilarityDialect.INSTANCE;
            case MYSQL -> MySqlVectorSimilarityDialect.INSTANCE;
            default -> null;
        };
    }

    @Nullable
    private static OracleVectorConfig resolveOracleVectorConfig(Expression<?> left) {
        if (!(left instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPathExpression)) {
            return null;
        }
        PersistentPropertyPath propertyPath = propertyPathExpression.getPropertyPath();
        PersistentProperty property = propertyPath.getProperty();
        int configuredLength = property.getAnnotationMetadata().intValue(VectorStorage.class, "length").orElse(-1);
        String dimensions = configuredLength > 0 ? Integer.toString(configuredLength) : "*";
        boolean sparse = VectorStorageShapeResolver.isSparse(property.getAnnotationMetadata());
        if (property.isAssignable(FloatVector.class)) {
            return new OracleVectorConfig(dimensions, "FLOAT32", sparse);
        }
        if (property.isAssignable(DoubleVector.class)) {
            return new OracleVectorConfig(dimensions, "FLOAT64", sparse);
        }
        if (property.isAssignable(ByteVector.class)) {
            return new OracleVectorConfig(dimensions, "INT8", sparse);
        }
        return null;
    }

    /**
     * Oracle VECTOR rendering configuration extracted from property metadata.
     *
     * @param dimensions vector dimensions or {@code *}
     * @param format Oracle vector element format (for example {@code FLOAT32})
     * @param sparse whether sparse vector notation should be used
     */
    record OracleVectorConfig(String dimensions, String format, boolean sparse) {
    }

    /**
     * MySQL vector score SQL renderer.
     */
    enum MySqlVectorSimilarityDialect implements VectorSimilarityDialect {
        INSTANCE;

        @Override
        public void appendVectorScore(StringBuilder query,
                                      Expression<?> left,
                                      Expression<?> right,
                                      Consumer<Expression<?>> appendExpression) {
            query.append("DISTANCE(");
            appendExpression.accept(left);
            query.append(',');
            appendExpression.accept(right);
            query.append(",'EUCLIDEAN')");
        }
    }

    /**
     * PostgreSQL/pgvector score SQL renderer.
     */
    enum PostgresVectorSimilarityDialect implements VectorSimilarityDialect {
        INSTANCE;

        @Override
        public void appendVectorScore(StringBuilder query,
                                      Expression<?> left,
                                      Expression<?> right,
                                      Consumer<Expression<?>> appendExpression) {
            appendExpression.accept(left);
            query.append(" <=> ");
            appendExpression.accept(right);
        }
    }

    /**
     * Oracle VECTOR_DISTANCE score SQL renderer.
     */
    enum OracleVectorSimilarityDialect implements VectorSimilarityDialect {
        INSTANCE;

        private static final String TO_VECTOR = "TO_VECTOR(";

        @Override
        public void appendVectorScore(StringBuilder query,
                                      Expression<?> left,
                                      Expression<?> right,
                                      Consumer<Expression<?>> appendExpression) {
            OracleVectorConfig config = resolveOracleVectorConfig(left);
            query.append("VECTOR_DISTANCE(");
            if (config == null) {
                query.append(TO_VECTOR);
                appendExpression.accept(left);
                query.append(')');
            } else {
                query.append(TO_VECTOR);
                appendExpression.accept(left);
                query.append(',').append(config.dimensions()).append(',').append(config.format());
                if (config.sparse()) {
                    query.append(",SPARSE");
                }
                query.append(')');
            }
            query.append(',');
            if (config == null) {
                query.append(TO_VECTOR);
                appendExpression.accept(right);
                query.append(')');
            } else {
                query.append(TO_VECTOR);
                appendExpression.accept(right);
                query.append(',').append(config.dimensions()).append(',').append(config.format());
                if (config.sparse()) {
                    query.append(",SPARSE");
                }
                query.append(')');
            }
            query.append(",COSINE)");
        }
    }
}
