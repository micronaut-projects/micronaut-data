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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.intercept.annotation.PostgresChangeListenerQuery;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.postgres.PostgresIdentifiers;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Processes {@code @PostgresChangeNotification} methods during compilation.
 *
 * <p>The visitor verifies that the method is also a {@code @ChangeListener}, validates the
 * notification channel and supported primary-key mappings, and generates an internal
 * {@link PostgresChangeListenerQuery} annotation containing the entity reload query and the
 * mapped key columns and property paths in query binding order.</p>
 *
 * <p>This visitor performs compile-time processing only. It does not open a datasource
 * connection or install PostgreSQL notification functions or triggers.</p>
 */
@Internal
public final class PostgresChangeNotificationVisitor implements TypeElementVisitor<Object, Object> {
    private static final String ANNOTATION = "io.micronaut.data.jdbc.annotation.PostgresChangeNotification";

    @Override
    public int getOrder() {
        return MappedEntityVisitor.POSITION + 10;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    /**
     * Validates PostgreSQL notification settings and generates the entity reload query at compilation time.
     *
     * @param element the annotated listener method
     * @param context the compilation visitor context used to report errors and add generated metadata
     */
    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasStereotype(ANNOTATION)) {
            return;
        }
        if (!element.hasStereotype(ChangeListenerVisitor.CHANGE_LISTENER)) {
            context.fail("@PostgresChangeNotification requires @ChangeListener", element);
            return;
        }
        if (!validateChannel(element, context)) {
            return;
        }
        var entityType = ChangeListenerMethodUtils.resolveEntityType(element);
        if (entityType == null || !entityType.hasStereotype(MappedEntity.class)) {
            return;
        }
        var resolver = new SourcePersistentEntityResolver(context, new HashMap<>());
        var entity = resolver.apply(context.getClassElement(entityType.getName()).orElse(entityType));
        List<PersistentPropertyPath> keys = new ArrayList<>();
        entity.getIdentityProperties().forEach(id -> PersistentEntityUtils.traversePersistentProperties(id,
            (associations, property) -> keys.add(new PersistentPropertyPath(associations, property))));
        if (!validatePrimaryKeys(keys, element, context)) {
            return;
        }
        generateReloadQuery(entity, keys, resolver, element, context);
    }

    /**
     * Validates the channel name without changing its spelling or case.
     *
     * @param element the annotated listener method
     * @param context the compilation visitor context used to report errors
     * @return {@code true} if the channel is supported
     */
    private static boolean validateChannel(MethodElement element, VisitorContext context) {
        String channel = element.stringValue(ANNOTATION, "channel").orElse("");
        if (channel.isBlank() || channel.indexOf('\0') >= 0 || channel.getBytes(StandardCharsets.UTF_8).length > 63) {
            context.fail("@PostgresChangeNotification channel must be non-blank, contain no NUL characters, "
                + "and occupy at most 63 UTF-8 bytes", element);
            return false;
        }
        return true;
    }

    /**
     * Rejects primary-key mappings that require association traversal or value conversion.
     *
     * @param keys the flattened primary-key property paths
     * @param element the annotated listener method
     * @param context the compilation visitor context used to report errors
     * @return {@code true} if the key mappings are supported
     */
    private static boolean validatePrimaryKeys(List<PersistentPropertyPath> keys, MethodElement element, VisitorContext context) {
        if (keys.isEmpty()) {
            context.fail("@PostgresChangeNotification requires a mapped primary key", element);
            return false;
        }
        for (var key : keys) {
            if (key.getAssociations().stream().anyMatch(a -> !(a instanceof Embedded))
                || key.getProperty().getAnnotationMetadata().hasStereotype(Relation.class)
                || key.getProperty().getAnnotationMetadata().stringValue(MappedProperty.class, "converter").isPresent()) {
                context.fail("@PostgresChangeNotification does not support association or converted primary keys", element);
                return false;
            }
        }
        return true;
    }

    /**
     * Generates a bound reload query and key metadata ordered to match its parameters.
     *
     * @param entity the entity mapping used to build the reload query
     * @param keys the validated primary-key property paths
     * @param resolver the resolver used by the criteria builder for entity mappings
     * @param element the listener method receiving the generated metadata
     * @param context the compilation visitor context used to report errors
     */
    private static void generateReloadQuery(SourcePersistentEntity entity, List<PersistentPropertyPath> keys,
                                            SourcePersistentEntityResolver resolver, MethodElement element, VisitorContext context) {
        var builder = new SourcePersistentEntityCriteriaBuilderImpl(resolver);
        var query = builder.createQuery();
        var root = query.from(entity);
        query.select(root);
        List<Predicate> predicates = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            Path<?> path = root;
            for (var property : keys.get(i).getPropertyPath()) {
                path = path.get(property.getName());
            }
            predicates.add(builder.equal(path, builder.parameter(Object.class, "key" + i)));
        }
        query.where(predicates.toArray(Predicate[]::new));
        var result = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.POSTGRES)));
        var bindings = result.getParameterBindings();
        if (bindings.size() != keys.size()) {
            context.fail("@PostgresChangeNotification cannot reload entities with additional query parameters; "
                + "parameterized entity @Where clauses are not supported", element);
            return;
        }
        String[] columns = new String[keys.size()];
        String[] paths = new String[keys.size()];
        for (int i = 0; i < bindings.size(); i++) {
            String[] propertyPath = bindings.get(i).getPropertyPath();
            String path = propertyPath == null ? "" : String.join(".", propertyPath);
            var key = keys.stream().filter(candidate -> candidate.getPath().equals(path)).findFirst().orElse(null);
            if (key == null) {
                context.fail("@PostgresChangeNotification requires only primary-key reload parameters", element);
                return;
            }
            String column = entity.getNamingStrategy().mappedName(key.getAssociations(), key.getProperty());
            columns[i] = PostgresIdentifiers.normalize(column,
                entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true));
            paths[i] = key.getPath();
        }
        element.annotate(PostgresChangeListenerQuery.class, annotation -> annotation.value(result.getQuery())
            .member("columns", columns).member("paths", paths));
    }
}
