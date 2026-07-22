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
package io.micronaut.data.runtime.support;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilities for resolving datasource configuration metadata.
 */
@Internal
public final class DataSourceConfigurationUtils {

    private static final String CONFIGURED_DATASOURCE_NAMES_CACHE = DataSourceConfigurationUtils.class.getName() + ".configuredDataSourceNames";

    private DataSourceConfigurationUtils() {
    }

    /**
     * Resolves the datasource name from the current condition qualifier.
     *
     * <p>This method answers which datasource the current bean resolution is creating or resolving.
     * If the condition is evaluated before a specific datasource-qualified bean,
     * no current datasource exists and this method returns empty.</p>
     *
     * @param context The condition context
     * @return The datasource name, or empty when the condition is being evaluated without a datasource qualifier
     */
    public static Optional<String> resolveDataSourceName(ConditionContext<?> context) {
        BeanResolutionContext beanResolutionContext = context.getBeanResolutionContext();
        Qualifier<?> currentQualifier = null;
        if (beanResolutionContext != null) {
            currentQualifier = beanResolutionContext.getCurrentQualifier();
            if (currentQualifier == null) {
                currentQualifier = beanResolutionContext.getPath()
                    .currentSegment()
                    .map(BeanResolutionContext.Segment::getDeclaringTypeQualifier)
                    .orElse(null);
            }
        }
        if (currentQualifier == null && context.getComponent() instanceof BeanDefinition<?> definition) {
            currentQualifier = definition.getDeclaredQualifier();
        }
        if (currentQualifier instanceof Named named) {
            return Optional.of(named.getName());
        }
        return Optional.empty();
    }

    /**
     * Resolves all configured datasource names visible to the condition context.
     *
     * <p>Property entries are used first. If no entries are available, the method falls back to
     * datasource configuration bean definitions, which may already have been produced by
     * Micronaut's configuration binding. Results are cached per bean context, configuration
     * prefix and configuration bean type.</p>
     *
     * @param context The condition context
     * @param configurationPrefix The datasource configuration prefix
     * @param configurationType The datasource configuration bean type
     * @return The configured datasource names
     */
    public static List<String> resolveConfiguredDataSourceNames(ConditionContext<?> context,
                                                                String configurationPrefix,
                                                                Class<?> configurationType) {
        BeanContext beanContext = context.getBeanContext();
        CacheKey cacheKey = new CacheKey(configurationPrefix, configurationType);
        Map<CacheKey, List<String>> contextCache = contextCache(beanContext);
        return contextCache.computeIfAbsent(
            cacheKey,
            ignored -> resolveConfiguredDataSourceNamesUncached(context, configurationPrefix, configurationType));
    }

    @SuppressWarnings("unchecked")
    private static Map<CacheKey, List<String>> contextCache(BeanContext beanContext) {
        return beanContext.getAttribute(CONFIGURED_DATASOURCE_NAMES_CACHE, Map.class)
            .map(cache -> (Map<CacheKey, List<String>>) cache)
            .orElseGet(() -> {
                Map<CacheKey, List<String>> cache = new ConcurrentHashMap<>();
                beanContext.setAttribute(CONFIGURED_DATASOURCE_NAMES_CACHE, cache);
                return cache;
            });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> resolveConfiguredDataSourceNamesUncached(ConditionContext<?> context,
                                                                         String configurationPrefix,
                                                                         Class<?> configurationType) {
        Collection<String> dataSourceNames = context.getPropertyEntries(configurationPrefix);
        if (!dataSourceNames.isEmpty()) {
            return List.copyOf(dataSourceNames);
        }
        Collection<BeanDefinition<Object>> beanDefinitions = context.findBeanDefinitions((Class) configurationType);
        if (beanDefinitions.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(beanDefinitions.size());
        for (BeanDefinition<?> beanDefinition : beanDefinitions) {
            Qualifier<?> qualifier = beanDefinition.getDeclaredQualifier();
            if (qualifier instanceof Named named) {
                names.add(named.getName());
            }
        }
        return List.copyOf(names);
    }

    private record CacheKey(String configurationPrefix, Class<?> configurationType) {
    }
}
