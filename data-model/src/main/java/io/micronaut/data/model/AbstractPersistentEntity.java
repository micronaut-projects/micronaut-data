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
package io.micronaut.data.model;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.naming.NamingStrategy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract version of the {@link PersistentEntity} interface.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractPersistentEntity implements PersistentEntity {

    private static final Map<String, NamingStrategy> NAMING_STRATEGIES = new ConcurrentHashMap<>(3);

    private final AnnotationMetadataProvider annotationMetadataProvider;
    @Nullable
    private final NamingStrategy namingStrategy;

    /**
     * Default constructor.
     * @param annotationMetadataProvider The annotation metadata provider.
     */
    protected AbstractPersistentEntity(AnnotationMetadataProvider annotationMetadataProvider) {
        this.annotationMetadataProvider = annotationMetadataProvider;
        this.namingStrategy = getNamingStrategy(annotationMetadataProvider.getAnnotationMetadata());
    }

    @NonNull
    @Override
    public String getAliasName() {
        return getAnnotationMetadata().stringValue(MappedEntity.class, "alias")
                .orElseGet(() -> NamingStrategy.DEFAULT.mappedName(getSimpleName()) + "_");
    }

    private NamingStrategy getNamingStrategy(AnnotationMetadata annotationMetadata) {
        return annotationMetadata
                .stringValue(io.micronaut.data.annotation.NamingStrategy.class)
                .flatMap(className -> getNamingStrategy(className, getClass().getClassLoader()))
                .orElse(null);
    }

    @NonNull
    private static Optional<NamingStrategy> getNamingStrategy(String className, ClassLoader classLoader) {
        NamingStrategy namingStrategy = NAMING_STRATEGIES.get(className);
        if (namingStrategy != null) {
            return Optional.of(namingStrategy);
        } else {
            Object o = InstantiationUtils.tryInstantiate(className, classLoader).orElse(null);
            if (o instanceof NamingStrategy ns) {
                NAMING_STRATEGIES.put(className, ns);
                return Optional.of(ns);
            }
            return Optional.empty();
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadataProvider.getAnnotationMetadata();
    }

    /**
     * Obtain the naming strategy for the entity.
     * @return The naming strategy
     */
    @Override
    public @NonNull NamingStrategy getNamingStrategy() {
        return namingStrategy == null ? NamingStrategy.DEFAULT : namingStrategy;
    }

    @NonNull
    @Override
    public Optional<NamingStrategy> findNamingStrategy() {
        return Optional.ofNullable(namingStrategy);
    }

    @NonNull
    @Override
    public String getPersistedName() {
        return getNamingStrategy().mappedName(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().isInstance(o)) {
            return false;
        }
        AbstractPersistentEntity that = (AbstractPersistentEntity) o;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
