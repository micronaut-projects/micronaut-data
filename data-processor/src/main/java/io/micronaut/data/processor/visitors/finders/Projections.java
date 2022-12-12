/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Selection;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Projections.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class Projections {

    private static final List<Projection> PROJECTION_LIST = Arrays.stream(Projections.class.getClasses())
            .filter(clazz -> Projection.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers()))
            .map(clazz -> {
                try {
                    return (Projection) clazz.getDeclaredConstructor().newInstance();
                } catch (Throwable e) {
                    return null;
                }
            }).collect(Collectors.toList());

    @Nullable
    public static Selection<?> find(@NonNull PersistentEntityRoot<?> entityRoot,
                                    @NonNull PersistentEntityCriteriaBuilder cb,
                                    String value,
                                    BiFunction<PersistentEntityRoot<?>, String, PersistentPropertyPath<?>> findFunction) {
        String decapitalized = NameUtils.decapitalize(value);
        Optional<String> path = entityRoot.getPersistentEntity().getPath(decapitalized);
        if (path.isPresent()) {
            return entityRoot.get(path.get());
        }
        for (Projection projection : PROJECTION_LIST) {
            Selection<?> selection = projection.find(entityRoot, cb, value, findFunction);
            if (selection != null) {
                return selection;
            }
        }
        return null;
    }

    /**
     * The MAX projection.
     */
    public static class Max extends PrefixedPropertyProjection {

        @Override
        public Selection<?> createProjection(CriteriaBuilder cb, PersistentPropertyPath<?> propertyPath) {
            return propertyPath.isNumeric()
                ? cb.max((PersistentPropertyPath<? extends Number>) propertyPath)
                : cb.greatest((PersistentPropertyPath<? extends Comparable>) propertyPath);
        }

        @Override
        protected String getPrefix() {
            return "Max";
        }
    }

    /**
     * The MIN projection.
     */
    public static class Min extends PrefixedPropertyProjection {

        @Override
        public Selection<?> createProjection(CriteriaBuilder cb, PersistentPropertyPath<?> propertyPath) {
            return propertyPath.isNumeric()
                ? cb.min((PersistentPropertyPath<? extends Number>) propertyPath)
                : cb.least((PersistentPropertyPath<? extends Comparable>) propertyPath);
        }

        @Override
        protected String getPrefix() {
            return "Min";
        }
    }

    /**
     * The SUM projection.
     */
    public static class Sum extends PrefixedPropertyProjection {

        @Override
        public Selection<?> createProjection(CriteriaBuilder cb, PersistentPropertyPath<?> propertyPath) {
            return cb.sum((PersistentPropertyPath<? extends Number>) propertyPath);
        }

        @Override
        protected String getPrefix() {
            return "Sum";
        }
    }

    /**
     * The AVG projection.
     */
    public static class Avg extends PrefixedPropertyProjection {

        @Override
        public Selection<?> createProjection(CriteriaBuilder cb, PersistentPropertyPath<?> propertyPath) {
            return cb.avg((PersistentPropertyPath<? extends Number>) propertyPath);
        }

        @Override
        protected String getPrefix() {
            return "Avg";
        }
    }

    private abstract static class PrefixedPropertyProjection implements Projection {

        @Override
        public final Selection<?> find(PersistentEntityRoot<?> entityRoot, PersistentEntityCriteriaBuilder cb, String value, BiFunction<PersistentEntityRoot<?>, String, PersistentPropertyPath<?>> findFunction) {
            String prefix = getPrefix();
            if (value.startsWith(prefix)) {
                String remaining = value.substring(prefix.length());
                String propertyName = NameUtils.decapitalize(remaining);
                PersistentPropertyPath<?> propertyPath = findFunction.apply(entityRoot, propertyName);
                if (propertyPath == null) {
                    throw new IllegalStateException("Cannot project on non-existent property " + propertyName);
                }
                return createProjection(cb, propertyPath);
            }
            return null;
        }

        public abstract Selection<?> createProjection(CriteriaBuilder cb, PersistentPropertyPath<?> propertyPath);

        protected abstract String getPrefix();
    }

    interface Projection {

        @Nullable
        Selection<?> find(@NonNull PersistentEntityRoot<?> entityRoot,
                          @NonNull PersistentEntityCriteriaBuilder cb,
                          @NonNull String value,
                          BiFunction<PersistentEntityRoot<?>, String, PersistentPropertyPath<?>> findFunction);

    }

}
