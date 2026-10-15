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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.annotation.PostgresChangeListenerQuery;
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the PostgreSQL runtime definition for a discovered change listener.
 *
 * <p>The factory reads the generated reload query and ordered key mappings, resolves their
 * Java and JDBC types, and creates the entity loader. Table identity is read from entity
 * metadata and normalized using PostgreSQL identifier rules.</p>
 *
 * <p>The annotation processor validates listener settings during compilation. Runtime checks
 * here identify missing or invalid generated metadata before subscription setup.</p>
 */
final class PostgresChangeListenerDefinitionFactory {
    private final JdbcRepositoryOperations operations;

    PostgresChangeListenerDefinitionFactory(JdbcRepositoryOperations operations) {
        this.operations = operations;
    }

    /**
     * Creates the table mapping and entity loader for one discovered listener method.
     *
     * @param listenerMethod the validated listener method and entity type
     * @return the listener definition
     */
    PostgresChangeListenerDefinition create(ChangeListenerMethod listenerMethod) {
        ExecutableMethod<?, ?> method = listenerMethod.method();
        AnnotationValue<PostgresChangeNotification> notification = method.getAnnotation(PostgresChangeNotification.class);
        if (notification == null) {
            throw invalidChangeListener(method, "requires @PostgresChangeNotification for a PostgreSQL datasource");
        }
        String channel = notification.stringValue("channel")
            .orElseThrow(() -> invalidChangeListener(method, "requires a PostgreSQL notification channel"));
        RuntimePersistentEntity<?> entity = operations.getEntity(listenerMethod.entityArgument().getType());
        PostgresChangeListenerEntityLoader<?> loader = createEntityLoader(method, entity);
        String schema = entity.getAnnotationMetadata().stringValue(MappedEntity.class, "schema")
            .filter(value -> !value.isEmpty()).orElse(null);
        String table = entity.getPersistedName();
        if (!entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true)) {
            schema = schema == null ? null : schema.toLowerCase(Locale.ROOT);
            table = table.toLowerCase(Locale.ROOT);
        }
        return new PostgresChangeListenerDefinition(channel, schema, table, loader);
    }

    /**
     * Validates the generated query and key mappings and builds the entity loader with ordered, typed bindings.
     *
     * @param method the listener method supplying the generated reload metadata
     * @param entity the entity mapping used to resolve primary-key property types
     * @return the entity loader
     */
    private PostgresChangeListenerEntityLoader<?> createEntityLoader(ExecutableMethod<?, ?> method, RuntimePersistentEntity<?> entity) {
        AnnotationValue<PostgresChangeListenerQuery> metadata = method.getAnnotation(PostgresChangeListenerQuery.class);
        if (metadata == null) {
            throw invalidChangeListener(method, "is missing its generated PostgreSQL primary-key reload query");
        }
        String query = metadata.stringValue().filter(value -> !value.isBlank())
            .orElseThrow(() -> invalidChangeListener(method, "has a missing or blank generated PostgreSQL reload query"));
        String[] names = metadata.stringValues("columns");
        String[] paths = metadata.stringValues("paths");
        if (names.length == 0 || names.length != paths.length) {
            throw invalidChangeListener(method, "has invalid generated PostgreSQL key mapping: expected non-empty column and property-path arrays of equal length");
        }
        Class<?> entityType = entity.getIntrospection().getBeanType();
        List<PostgresChangeListenerEntityLoader.KeyBinding> bindings = new ArrayList<>(names.length);
        Set<String> columns = new HashSet<>();
        for (int i = 0; i < names.length; i++) {
            if (names[i].isEmpty() || !columns.add(names[i])) {
                throw invalidChangeListener(method, "has an empty or duplicate generated PostgreSQL key column [" + names[i] + "]");
            }
            PersistentPropertyPath propertyPath = entity.getPropertyPath(paths[i]);
            if (propertyPath == null) {
                throw invalidChangeListener(method, "has an unknown generated PostgreSQL key property path [" + paths[i] + "] for entity [" + entityType.getName() + "]");
            }
            RuntimePersistentProperty<?> property = (RuntimePersistentProperty<?>) propertyPath.getProperty();
            bindings.add(new PostgresChangeListenerEntityLoader.KeyBinding(names[i], property.getType(), property.getDataType()));
        }
        return new PostgresChangeListenerEntityLoader<>(operations, entityType, query, bindings);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}
