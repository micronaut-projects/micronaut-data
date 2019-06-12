/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link QueryBuilder} that builds SQL queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SqlQueryBuilder extends AbstractSqlLikeQueryBuilder implements QueryBuilder {

    @Override
    protected String selectAllColumns(PersistentEntity entity, String alias) {
        List<PersistentProperty> persistentProperties = entity.getPersistentProperties()
                .stream()
                .filter(pp -> {
                    if (pp instanceof Association) {
                        Association association = (Association) pp;
                        return !association.isForeignKey();
                    }
                    return true;
                })
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(persistentProperties)) {
            StringBuilder builder = new StringBuilder();
            Iterator<PersistentProperty> i = persistentProperties.iterator();
            boolean first = true;
            PersistentProperty identity = entity.getIdentity();
            if (identity != null) {
                builder.append(alias).append(DOT)
                        .append(identity.getPersistedName())
                        .append(",");
            }
            while (i.hasNext()) {
                PersistentProperty pp = i.next();

                if (first) {
                    first = false;
                } else {
                    builder.append(",");
                }

                builder.append(alias).append(DOT)
                        .append(pp.getPersistedName());
            }
            return builder.toString();

        } else {
            return "*";
        }
    }

    @Override
    protected String getTableName(PersistentEntity entity) {
        return entity.getPersistedName();
    }

    @Override
    protected String getColumnName(PersistentProperty persistentProperty) {
        return persistentProperty.getPersistedName();
    }

    @Override
    protected void appendProjectionRowCount(StringBuilder queryString, String logicalName) {
        queryString.append(FUNCTION_COUNT)
                .append(OPEN_BRACKET)
                .append('*')
                .append(CLOSE_BRACKET);
    }

    @Override
    protected Placeholder formatParameter(int index) {
        return new Placeholder("?", String.valueOf(index));
    }

    @Nullable
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        StringBuilder builder = new StringBuilder("INSERT INTO ");
        builder.append(getTableName(entity));
        builder.append(" (");

        List<PersistentProperty> persistentProperties = entity.getPersistentProperties();
        Map<String, String> parameters = new LinkedHashMap<>(persistentProperties.size());
        boolean hasProperties = CollectionUtils.isNotEmpty(persistentProperties);
        int index = 1;
        if (hasProperties) {
            Iterator<PersistentProperty> i = persistentProperties.iterator();
            while (i.hasNext()) {
                PersistentProperty prop = i.next();
                if (prop.isGenerated()) {
                    continue;
                }
                builder.append(getColumnName(prop));
                parameters.put(prop.getName(), String.valueOf(index++));
                if (i.hasNext()) {
                    builder.append(COMMA);
                }
            }
        }

        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {

            boolean assignedOrSequence = false;
            Optional<AnnotationValue<GeneratedValue>> generated = identity.findAnnotation(GeneratedValue.class);
            if (generated.isPresent()) {
                GeneratedValue.Type idGeneratorType = generated
                        .flatMap(av -> av.enumValue(GeneratedValue.Type.class))
                        .orElseGet(this::selectAutoStrategy);
                if (idGeneratorType == GeneratedValue.Type.SEQUENCE) {
                    assignedOrSequence = true;
                }
            } else {
                assignedOrSequence = true;
            }
            if (assignedOrSequence) {
                if (hasProperties) {
                    builder.append(COMMA);
                }
                builder.append(getColumnName(identity));
                parameters.put(identity.getName(), String.valueOf(index++));
            }
        }

        builder.append(CLOSE_BRACKET);
        builder.append(" VALUES (");
        for (int i = 1; i < index; i++) {
            builder.append('?');
            if (i < index - 1) {
                builder.append(COMMA);
            }
        }
        builder.append(CLOSE_BRACKET);
        return QueryResult.of(
            builder.toString(),
            parameters
        );
    }

    /**
     * Selects the default fallback strategy. For a generated value.
     * @return The generated value
     */
    protected GeneratedValue.Type selectAutoStrategy() {
        return GeneratedValue.Type.AUTO;
    }
}
