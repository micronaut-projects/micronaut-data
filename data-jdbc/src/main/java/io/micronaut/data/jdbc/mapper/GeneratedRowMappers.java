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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the {@link GeneratedRowMapper} of an entity. The generated mappers are registered as Micronaut services
 * (a {@code META-INF/micronaut/} entry per mapper, written by the annotation processor next to the generated
 * class), so they are found without scanning and the registration is what a native image needs to load them.
 *
 * <p>The service entries are read once, on the first lookup, into a map by entity name: the mapper of an entity
 * is named after it, so nothing is loaded until the entity is looked up, and then only that entity's mapper. The
 * per-class cache is a {@link ClassValue}, so it holds no entity class reachable (an application redeployed under
 * a parent class loader holding data-jdbc is collected); it is static rather than per application context: a
 * mapper is stateless and belongs to its entity class, and a mapper whose entity class is not the one looked up
 * (another class loader) is treated as absent.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class GeneratedRowMappers {

    private static final ClassValue<Optional<GeneratedRowMapper<?>>> MAPPERS = new ClassValue<>() {
        @Override
        protected Optional<GeneratedRowMapper<?>> computeValue(Class<?> type) {
            return load(type);
        }
    };

    private GeneratedRowMappers() {
    }

    /**
     * @param entityType The entity type
     * @param <T>        The entity type
     * @return The mapper generated for the entity, or {@code null} when none was generated
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> GeneratedRowMapper<T> find(Class<T> entityType) {
        return (GeneratedRowMapper<T>) MAPPERS.get(entityType).orElse(null);
    }

    @SuppressWarnings("rawtypes")
    private static Optional<GeneratedRowMapper<?>> load(Class<?> entityType) {
        ServiceDefinition<GeneratedRowMapper> definition = Definitions.BY_NAME.get(entityType.getName() + GeneratedRowMapper.CLASS_NAME_SUFFIX);
        if (definition == null || !definition.isPresent()) {
            return Optional.empty();
        }
        GeneratedRowMapper<?> mapper = definition.load();
        return mapper.entityType() == entityType ? Optional.of(mapper) : Optional.empty();
    }

    /**
     * The service entries by class name, read when first needed.
     */
    @SuppressWarnings("rawtypes")
    private static final class Definitions {
        static final Map<String, ServiceDefinition<GeneratedRowMapper>> BY_NAME = load();

        private Definitions() {
        }

        private static Map<String, ServiceDefinition<GeneratedRowMapper>> load() {
            Map<String, ServiceDefinition<GeneratedRowMapper>> byName = new HashMap<>();
            for (ServiceDefinition<GeneratedRowMapper> definition : SoftServiceLoader.load(GeneratedRowMapper.class)) {
                byName.put(definition.getName(), definition);
            }
            return byName;
        }
    }
}
