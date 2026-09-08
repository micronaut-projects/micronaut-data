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
package io.micronaut.data.runtime.convert;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.jd.SpecificationConstraint;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.data.constraint.AtLeast;
import jakarta.data.constraint.AtMost;
import jakarta.data.constraint.Constraint;
import jakarta.data.constraint.EqualTo;
import jakarta.data.constraint.GreaterThan;
import jakarta.data.constraint.In;
import jakarta.data.constraint.LessThan;
import jakarta.data.constraint.Like;
import jakarta.data.constraint.NotEqualTo;
import jakarta.data.constraint.NotIn;
import jakarta.data.constraint.NotLike;
import jakarta.data.repository.By;
import jakarta.data.repository.Is;
import jakarta.inject.Provider;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * The constraint `@Is(NotEquals) ...` to {@link PredicateSpecification} converter.
 *
 * @param <E> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class JakartaDataConstraintConverter<E> implements TypeConverter<SpecificationConstraint, PredicateSpecification<E>> {

    private final DateTimeProvider<OffsetDateTime> dateTimeProvider;
    private final Provider<RuntimeEntityRegistry> runtimeEntityRegistry;

    JakartaDataConstraintConverter(DateTimeProvider<OffsetDateTime> dateTimeProvider, Provider<RuntimeEntityRegistry> runtimeEntityRegistry) {
        this.dateTimeProvider = dateTimeProvider;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public Optional<PredicateSpecification<E>> convert(SpecificationConstraint jakartaDataConstraint, Class<PredicateSpecification<E>> targetType, ConversionContext context) {
        Argument<?> argument = jakartaDataConstraint.argument();
        AnnotationValue<Is> isAnnotation = argument.getAnnotationMetadata().getAnnotation(Is.class);
        if (isAnnotation != null) {
            return Optional.of(new PredicateSpecification<>() {
                @Override
                public Predicate toPredicate(Root<E> root, CriteriaBuilder criteriaBuilder) {
                    Class<?> constraint = isAnnotation.classValue().orElse(EqualTo.class);
                    Expression path = getExpression(root, argument);
                    Object value = jakartaDataConstraint.value();
                    if (constraint == AtLeast.class) {
                        Expression expression = (Expression<Comparable>) path;
                        Comparable comparable = (Comparable) value;
                        return criteriaBuilder.greaterThanOrEqualTo(expression, comparable);
                    }
                    if (constraint == GreaterThan.class) {
                        Expression<Comparable> expression = (Expression<Comparable>) path;
                        Comparable comparable = (Comparable) value;
                        return criteriaBuilder.greaterThan(expression, comparable);
                    }
                    if (constraint == AtMost.class) {
                        Expression<Comparable> expression = (Expression<Comparable>) path;
                        Comparable comparable = (Comparable) value;
                        return criteriaBuilder.lessThanOrEqualTo(expression, comparable);
                    }
                    if (constraint == LessThan.class) {
                        Expression<Comparable> expression = (Expression<Comparable>) path;
                        Comparable comparable = (Comparable) value;
                        return criteriaBuilder.lessThan(expression, comparable);
                    }
                    if (constraint == EqualTo.class) {
                        return criteriaBuilder.equal(path, value);
                    }
                    if (constraint == NotEqualTo.class) {
                        return criteriaBuilder.notEqual(path, value);
                    }
                    if (constraint == In.class) {
                        return in(value, path);
                    }
                    if (constraint == NotIn.class) {
                        return in(value, path).not();
                    }
                    if (constraint == Like.class) {
                        return criteriaBuilder.like((Expression<String>) path, (String) value);
                    }
                    if (constraint == NotLike.class) {
                        return criteriaBuilder.notLike((Expression<String>) path, (String) value);
                    }
                    throw new IllegalArgumentException("Unknown constraint [" + constraint + "]");
                }

                private Predicate in(Object value, Expression<?> path) {
                    if (value instanceof Collection<?> collection) {
                        return path.in(collection);
                    }
                    if (value instanceof Object[] array) {
                        return path.in(array);
                    }
                    return path.in(value);
                }
            });
        } else if (jakartaDataConstraint.value() instanceof Constraint<?> constraint) {
            return Optional.of(new PredicateSpecification<>() {

                private final JakartaDataRestrictionsConverter<E> converter = new JakartaDataRestrictionsConverter<>(dateTimeProvider);

                @Override
                public Predicate toPredicate(Root<E> root, CriteriaBuilder criteriaBuilder) {
                    return converter.toPredicate(
                        root,
                        criteriaBuilder,
                        constraint,
                        JakartaDataConstraintConverter.this.getExpression(root, argument)
                    );
                }
            });
        }
        return Optional.empty();
    }

    private <V> Expression<V> getExpression(Root<E> root, Argument<?> argument) {
        Optional<String> byPropertyName = argument.getAnnotationMetadata().stringValue(By.class)
            .or(() -> argument.getAnnotationMetadata().stringValue(io.micronaut.data.annotation.By.class));
        if (byPropertyName.isPresent()) {
            String propertyName = byPropertyName.get();
            if (propertyName.equals(By.ID)) {
                if (root instanceof PersistentEntityRoot<?> persistentEntityRoot) {
                    return (Expression<V>) persistentEntityRoot.id();
                }
                // Providers such as Hibernate supply their own criteria root implementation
                return root.get(runtimeEntityRegistry.get().getEntity(root.getJavaType()).getIdentity().getName());
            }
            return getPropertyByPath(root, propertyName);
        }
        RuntimePersistentEntity<? extends E> entity = runtimeEntityRegistry.get().getEntity(root.getJavaType());
        String propertyName = argument.getName();
        return getPropertyByPath(root, entity.getPath(propertyName)
            // Jakarta Data allows an underscore in a parameter name to delimit the segments of a nested attribute path
            .or(() -> entity.getPath(underscoreToCamelCase(propertyName)))
            .orElseThrow(() -> new IllegalStateException("Cannot find property: " + propertyName + " in entity: " + entity.getName())));
    }

    /**
     * Converts an underscore delimited nested attribute name such as {@code capital_population}
     * into the camel case form {@code capitalPopulation} understood by
     * {@link io.micronaut.data.model.PersistentEntity#getPath(String)}.
     *
     * @param propertyName The parameter name
     * @return The camel case form, or the name unchanged when it has no underscore
     */
    private static String underscoreToCamelCase(String propertyName) {
        if (propertyName.indexOf('_') == -1) {
            return propertyName;
        }
        StringBuilder camelCase = new StringBuilder(propertyName.length());
        for (String segment : StringUtils.splitOmitEmptyStrings(propertyName, '_')) {
            camelCase.append(camelCase.isEmpty() ? NameUtils.decapitalize(segment) : NameUtils.capitalize(segment));
        }
        return camelCase.toString();
    }

    private <V> Path<V> getPropertyByPath(Root<E> root, String propertyName) {
        Path path = root;
        for (String p : StringUtils.splitOmitEmptyStrings(propertyName, '.')) {
            path = path.get(p);
        }
        return path;
    }
}
