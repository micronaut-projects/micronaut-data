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
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import jakarta.inject.Singleton;

import java.util.function.Function;

/**
 * Vector index DDL provider for Oracle.
 *
 * It expects a neutral clause on the mapping in the form:
 *   ALGO <IVF|HNSW> DISTANCE <COSINE|DOT|EUCLIDEAN_SQUARED|EUCLIDEAN|MANHATTAN|HAMMING> ACCURACY <n>
 *
 * Example output:
 *   CREATE VECTOR INDEX IDX ON "TABLE" ("COL") ORGANIZATION NEIGHBOR PARTITIONS DISTANCE COSINE WITH TARGET ACCURACY 90
 *
 * @since 5.0.0
 */
@Singleton
@Internal
public final class OracleVectorSqlIndexDefinitionProvider implements SqlIndexDefinitionProvider {

    @Override
    public boolean supports(Argument<?> argument, Dialect dialect) {
        return dialect == Dialect.ORACLE
            && argument != null
            && io.micronaut.data.model.vector.Vector.class.isAssignableFrom(argument.getType());
    }

    @Override
    public String getIndexDefinition(String indexName,
                                     String escapedTableName,
                                     String[] columns,
                                     boolean escape,
                                     Function<String, String> quoter,
                                     SqlIndexMapping mapping,
                                     Dialect dialect) {
        String columnNames = String.join(", ", columns);
        String indexColumnNames = escape
            ? String.join(", ", java.util.Arrays.stream(columns).map(quoter).toList())
            : columnNames;

        var meta = mapping.vectorIndexMetadata();
        boolean hnsw = meta != null && meta.vectorIndexType() == io.micronaut.data.annotation.VectorIndexType.HNSW;
        String organization = hnsw ? "ORGANIZATION NEIGHBOR GRAPH" : "ORGANIZATION NEIGHBOR PARTITIONS";
        String distance = switch (meta.distanceType()) {
            case DOT -> "DOT";
            case L2_EUCLIDEAN_SQUARED -> "EUCLIDEAN SQUARED";
            case L2_EUCLIDEAN -> "EUCLIDEAN";
            case L1_MANHATTAN -> "MANHATTAN";
            case HAMMING -> "HAMMING";
            default -> "COSINE";
        };
        int accuracy = meta != null ? meta.accuracy() : 90;

        StringBuilder vec = new StringBuilder();
        vec.append("CREATE VECTOR INDEX ")
           .append(indexName)
           .append(" ON ")
           .append(escapedTableName)
           .append(" (")
           .append(indexColumnNames)
           .append(")")
           .append(' ')
           .append(organization)
           .append(' ')
           .append("DISTANCE ")
           .append(distance)
           .append(' ')
           .append("WITH TARGET ACCURACY ")
           .append(accuracy);
        return vec.toString();
    }
}
