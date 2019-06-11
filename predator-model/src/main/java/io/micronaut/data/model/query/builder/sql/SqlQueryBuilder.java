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

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;

import java.util.Iterator;
import java.util.List;
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
}
