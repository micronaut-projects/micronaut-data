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
import io.micronaut.core.io.service.SoftServiceLoader;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Finds the {@link GeneratedRowMapper} of an entity. The generated mappers are registered as Micronaut services
 * (a {@code META-INF/micronaut/} entry per mapper, written by the annotation processor next to the generated
 * class), so they are found without scanning and the registration is what a native image needs to load them.
 * They are loaded once, on the first lookup.
 *
 * @since 5.3.0
 */
@Internal
public final class GeneratedRowMappers {

    @Nullable
    private static volatile Map<Class<?>, GeneratedRowMapper<?>> mappers;

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
        Map<Class<?>, GeneratedRowMapper<?>> resolved = mappers;
        if (resolved == null) {
            synchronized (GeneratedRowMappers.class) {
                resolved = mappers;
                if (resolved == null) {
                    resolved = load();
                    mappers = resolved;
                }
            }
        }
        return (GeneratedRowMapper<T>) resolved.get(entityType);
    }

    @SuppressWarnings("rawtypes")
    private static Map<Class<?>, GeneratedRowMapper<?>> load() {
        Map<Class<?>, GeneratedRowMapper<?>> byEntity = new HashMap<>();
        for (GeneratedRowMapper mapper : SoftServiceLoader.load(GeneratedRowMapper.class).collectAll()) {
            byEntity.put(mapper.entityType(), mapper);
        }
        return byEntity;
    }
}
