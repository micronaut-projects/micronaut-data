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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.mapping.CompositeJoinColumn;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Fetches joined associations for query results.
 *
 * @since 5.2.0
 */
@Internal
public final class JoinFetcher {

    private final NitriteEntityMapper entityMapper;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final ConversionService conversionService;

    /**
     * Creates a join fetcher.
     *
     * @param entityMapper The entity mapper
     * @param collectionFactory The collection factory
     * @param entityFactory The persistent entity factory
     * @param conversionService The conversion service
     */
    public JoinFetcher(NitriteEntityMapper entityMapper,
                       Function<Class<?>, NitriteCollection> collectionFactory,
                       Function<Class<?>, RuntimePersistentEntity<?>> entityFactory,
                       ConversionService conversionService) {
        this.entityMapper = entityMapper;
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.conversionService = conversionService;
    }

    /**
     * Fetches the requested associations for the given entities.
     *
     * @param entities The entities to populate
     * @param joinPaths The association paths to fetch
     * @param entityType The root entity type
     * @param <R> The entity type
     */
    public <R> void fetch(List<R> entities, Set<JoinPath> joinPaths, Class<?> entityType) {
        if (entities == null || entities.isEmpty() || joinPaths == null || joinPaths.isEmpty()) {
            return;
        }
        List<String> paths = joinPaths.stream()
            .map(JoinPath::getPath)
            .toList();
        fetchForEntity(entities, entityFactory.apply(entityType), paths);
    }

    private void fetchForEntity(List<?> entities, RuntimePersistentEntity<?> persistentEntity, List<String> paths) {
        if (entities == null || entities.isEmpty() || paths == null || paths.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : splitPaths(paths).entrySet()) {
            List<Object> children = fetchSingleLevel(entities, persistentEntity, entry.getKey());
            if (!entry.getValue().isEmpty() && !children.isEmpty()) {
                var assocProp = persistentEntity.getPropertyByName(entry.getKey());
                if (assocProp instanceof RuntimeAssociation<?> association) {
                    fetchForEntity(children, association.getAssociatedEntity(), entry.getValue());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> fetchSingleLevel(List<?> entities, RuntimePersistentEntity<?> persistentEntity, String associationName) {
        var assocProp = persistentEntity.getPropertyByName(associationName);
        if (!(assocProp instanceof RuntimeAssociation<?> association)) {
            return List.of();
        }

        var kind = association.getKind();
        if (kind != Relation.Kind.ONE_TO_MANY && kind != Relation.Kind.MANY_TO_MANY) {
            return List.of();
        }

        String mappedBy = association.getAnnotationMetadata()
            .stringValue(Relation.class, "mappedBy").orElse(null);
        if (mappedBy == null) {
            return List.of();
        }

        if (persistentEntity.hasCompositeIdentity()) {
            return fetchCompositeIdentityCollection(entities, persistentEntity, association, mappedBy);
        }

        RuntimePersistentProperty<?> idProp = persistentEntity.getIdentity();
        List<Object> parentIds = entities.stream()
            .map(entity -> ((BeanProperty<Object, Object>) idProp.getProperty()).get(entity))
            .filter(Objects::nonNull)
            .map(entityMapper::toFilterValue)
            .toList();
        if (parentIds.isEmpty()) {
            return List.of();
        }

        Class<?> associatedType = association.getAssociatedEntity().getIntrospection().getBeanType();
        NitriteCollection assocCollection = collectionFactory.apply(associatedType);
        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        RuntimePersistentProperty<?> backProp = associatedEntity.getPropertyByName(mappedBy);
        if (backProp == null) {
            return List.of();
        }

        String backFieldName = backProp.getPersistedName();
        if (associatedEntity.getIdentity().equals(backProp)) {
            backFieldName = "id";
        }
        final String finalBackFieldName = backFieldName;

        Comparable<?>[] comparableIds = parentIds.stream()
            .map(id -> id instanceof Comparable<?> c ? c : id.toString())
            .toArray(Comparable<?>[]::new);

        /*
         * A MANY_TO_MANY back-reference is a collection in the document; both arms handle that,
         * because EqualsFilter and InFilter each fall through to element containment for an
         * Iterable field, mirroring the index path, which treats arrays element-wise.
         */
        Filter filter = parentIds.size() == 1
            ? NitriteFilterUtils.eq(finalBackFieldName, parentIds.getFirst())
            : NitriteFilterUtils.in(finalBackFieldName, comparableIds);

        Map<Object, List<Object>> resultsByParentId = new HashMap<>();
        List<Object> fetchedChildren = new ArrayList<>();
        for (Document doc : assocCollection.find(filter)) {
            Object backRefValue = doc.get(finalBackFieldName);
            if (backRefValue != null) {
                Object assocEntity = entityMapper.fromDocument(doc, associatedType);
                fetchedChildren.add(assocEntity);
                if (backRefValue instanceof Collection<?> collection) {
                    for (Object parentId : collection) {
                        resultsByParentId
                            .computeIfAbsent(entityMapper.toFilterValue(parentId), k -> new ArrayList<>())
                            .add(assocEntity);
                    }
                } else {
                    resultsByParentId
                        .computeIfAbsent(entityMapper.toFilterValue(backRefValue), k -> new ArrayList<>())
                        .add(assocEntity);
                }
            }
        }

        var beanProperty = (BeanProperty<Object, Object>) association.getProperty();
        for (Object entity : entities) {
            Object parentId = ((BeanProperty<Object, Object>) idProp.getProperty()).get(entity);
            if (parentId != null) {
                List<Object> children = resultsByParentId.getOrDefault(entityMapper.toFilterValue(parentId), new ArrayList<>());
                beanProperty.set(entity, conversionService.convert(children, beanProperty.asArgument()).orElse(null));
            }
        }
        return fetchedChildren;
    }

    @SuppressWarnings("unchecked")
    private List<Object> fetchCompositeIdentityCollection(
        List<?> entities,
        RuntimePersistentEntity<?> persistentEntity,
        RuntimeAssociation<?> association,
        String mappedBy) {
        Class<?> associatedType = association.getAssociatedEntity().getIntrospection().getBeanType();
        NitriteCollection assocCollection = collectionFactory.apply(associatedType);
        List<CompositeJoinColumn> joinColumns = entityMapper.getCompositeJoinColumns(associatedType, mappedBy);
        if (joinColumns.isEmpty()) {
            return List.of();
        }

        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        var beanProperty = (BeanProperty<Object, Object>) association.getProperty();
        List<Object> fetchedChildren = new ArrayList<>();

        for (Object entity : entities) {
            List<Filter> filters = new ArrayList<>(joinColumns.size());
            boolean completeIdentity = true;
            for (CompositeJoinColumn joinColumn : joinColumns) {
                RuntimePersistentProperty<?> referenced = entityMapper.findPropertyByNameOrPersistedName(
                    persistentEntity, joinColumn.referencedProperty());
                if (referenced == null) {
                    completeIdentity = false;
                    break;
                }
                Object value = ((BeanProperty) referenced.getProperty()).get(entity);
                if (value == null) {
                    completeIdentity = false;
                    break;
                }
                filters.add(entityMapper.eqWithNumericCoercion(
                    associatedEntity,
                    joinColumn.localName(),
                    entityMapper.toFilterValue(value),
                    joinColumn.localName()));
            }
            if (!completeIdentity) {
                // Matches the single-identity path: a parent with no identity to match on keeps
                // whatever the property already holds rather than being handed a foreign type.
                continue;
            }

            Filter filter = filters.size() == 1
                ? filters.getFirst()
                : Filter.and(filters.toArray(new Filter[0]));
            List<Object> children = new ArrayList<>();
            for (Document doc : assocCollection.find(filter)) {
                Object child = entityMapper.fromDocument(doc, associatedType);
                children.add(child);
                fetchedChildren.add(child);
            }
            beanProperty.set(entity, conversionService.convert(children, beanProperty.asArgument()).orElse(null));
        }
        return fetchedChildren;
    }

    private static Map<String, List<String>> splitPaths(List<String> paths) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String path : paths) {
            int dot = path.indexOf('.');
            if (dot == -1) {
                result.computeIfAbsent(path, k -> new ArrayList<>());
            } else {
                result.computeIfAbsent(path.substring(0, dot), k -> new ArrayList<>())
                      .add(path.substring(dot + 1));
            }
        }
        return result;
    }
}
