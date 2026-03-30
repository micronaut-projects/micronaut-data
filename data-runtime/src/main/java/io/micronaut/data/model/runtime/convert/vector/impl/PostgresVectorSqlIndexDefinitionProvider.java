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
package io.micronaut.data.model.runtime.convert.vector.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.VectorIndexType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.metadata.VectorIndexMetadata;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

import java.util.function.Function;

/**
 * Vector index DDL provider for PostgreSQL (pgvector extension).
 *
 * It expects a neutral clause on the mapping in the form:
 *   ALGO &lt;IVF|HNSW&gt; DISTANCE &lt;COSINE|DOT|EUCLIDEAN_SQUARED|EUCLIDEAN|MANHATTAN&gt; ACCURACY &lt;n&gt;
 *
 * Example output:
 *   CREATE INDEX idx ON "table" USING ivfflat ("col" vector_cosine_ops);
 *
 * @since 5.0.0
 */
@Singleton
@Internal
final class PostgresVectorSqlIndexDefinitionProvider implements SqlIndexDefinitionProvider {

    @Override
    public boolean supports(Argument<?> argument, Dialect dialect) {
        return dialect == Dialect.POSTGRES
            && argument != null
            && Vector.class.isAssignableFrom(argument.getType());
    }

    @Override
    public String getIndexDefinition(String indexName,
                                     String escapedTableName,
                                     String[] columns,
                                     boolean escape,
                                     Function<String, String> quoter,
                                     SqlIndexMapping mapping,
                                     Dialect dialect) {
        String column = (columns != null && columns.length > 0) ? columns[0] : "embedding";
        String renderedColumn = escape ? quoter.apply(column) : column;

        // Choose method and operator class from metadata
        VectorIndexMetadata meta = mapping.vectorIndexMetadata();
        if (meta == null) {
            throw new IllegalArgumentException("Vector index metadata is required for PostgreSQL vector index definition");
        }
        boolean hnsw = meta.vectorIndexType() == VectorIndexType.HNSW;
        boolean sparse = meta.sparse();

        if (sparse && !hnsw) {
            throw new IllegalArgumentException("PostgreSQL sparse vectors support HNSW indexes only");
        }

        String usingMethod = hnsw ? "hnsw" : "ivfflat";

        String operatorClass = switch (meta.distanceType()) {
            case COSINE -> sparse ? "sparsevec_cosine_ops" : "vector_cosine_ops";
            case DOT -> sparse ? "sparsevec_ip_ops" : "vector_ip_ops";
            case L1_MANHATTAN -> sparse ? "sparsevec_l1_ops" : "vector_l1_ops";
            case L2_EUCLIDEAN_SQUARED -> sparse ? "sparsevec_l2_ops" : "vector_l2_ops";
            case L2_EUCLIDEAN -> sparse ? "sparsevec_l2_ops" : "vector_l2_ops";
            default -> throw new IllegalArgumentException("Distance type " + meta.distanceType() + " is not supported for PostgreSQL vector indexes");
        };

        return "CREATE INDEX " + indexName + " ON " + escapedTableName
            + " USING " + usingMethod + " (" + renderedColumn + " " + operatorClass + ");";
    }
}
