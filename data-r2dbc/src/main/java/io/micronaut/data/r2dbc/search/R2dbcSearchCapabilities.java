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
package io.micronaut.data.r2dbc.search;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.query.builder.sql.TextSearchDialect;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.runtime.search.SearchCapabilities;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The search features an R2DBC datasource honours, by its dialect.
 *
 * @author Graeme Rocher
 * @since 5.4.0
 */
@Experimental
@EachBean(DataR2dbcConfiguration.class)
public final class R2dbcSearchCapabilities implements SearchCapabilities {

    @Nullable
    private final TextSearchDialect textSearchDialect;
    private final RuntimeEntityRegistry runtimeEntityRegistry;

    /**
     * @param configuration The datasource configuration
     * @param runtimeEntityRegistry The entity registry
     */
    R2dbcSearchCapabilities(@Parameter DataR2dbcConfiguration configuration, RuntimeEntityRegistry runtimeEntityRegistry) {
        this.textSearchDialect = TextSearchDialect.forDialect(configuration.getDialect());
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public Set<SearchCapability> getSearchCapabilities(Class<?> entityType) {
        if (textSearchDialect == null || !runtimeEntityRegistry.getEntity(entityType).getAnnotationMetadata().hasAnnotation(Searchable.class)) {
            return Set.of();
        }
        return textSearchDialect.capabilities();
    }
}
