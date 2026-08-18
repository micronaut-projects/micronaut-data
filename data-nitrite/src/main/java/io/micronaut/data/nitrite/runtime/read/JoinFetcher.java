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
import java.util.Set;
import java.util.function.Function;

/**
 * Fetches joined associations for query results.
 *
 * @since 5.2.0
 */
@Internal
final class JoinFetcher {

    private final NitriteEntityMapper entityMapper;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final ConversionService conversionService;

    JoinFetcher(NitriteEntityMapper entityMapper,
                Function<Class<?>, NitriteCollection> collectionFactory,
                Function<Class<?>, RuntimePersistentEntity<?>> entityFactory,
                ConversionService conversionService) {
        this.entityMapper = entityMapper;
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.conversionService = conversionService;
    }

    <R> void fetch(List<R> entities, Set<JoinPath> joinPaths, Class<?> entityType) {
        if (entities == null || entities.isEmpty() || joinPaths == null || joinPaths.isEmpty()) {
            return;
        }
        List<String> paths = new ArrayList<>(joinPaths.size());
        for (JoinPath jp : joinPaths) {
            paths.add(jp.getPath());
        }
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

        RuntimePersistentProperty<?> idProp = persistentEntity.getIdentity();
        List<Object> parentIds = new ArrayList<>();
        for (Object entity : entities) {
            Object idValue = ((BeanProperty<Object, Object>) idProp.getProperty()).get(entity);
            if (idValue != null) {
                parentIds.add(entityMapper.toFilterValue(idValue));
            }
        }
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

        Filter filter;
        if (kind == Relation.Kind.MANY_TO_MANY) {
            // For MANY_TO_MANY, backFieldName is a collection in the document.
            // Standard Nitrite 'in' does not reliably match array-valued fields; use a custom filter.
            filter = pair -> {
                Document doc = pair.getSecond();
                Object val = doc.get(finalBackFieldName);
                if (val instanceof Collection<?> coll) {
                    for (Object id : parentIds) {
                        if (coll.contains(id)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        } else {
            filter = parentIds.size() == 1
                ? NitriteFilterUtils.eq(finalBackFieldName, parentIds.getFirst())
                : NitriteFilterUtils.in(finalBackFieldName, comparableIds);
        }

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
