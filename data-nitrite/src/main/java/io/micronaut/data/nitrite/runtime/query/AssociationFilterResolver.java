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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.mapping.CompositeJoinColumn;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder.SubQueryExecutor;
import io.micronaut.data.nitrite.runtime.query.PathResolver.PathResolution;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EQ;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.OR;

@Internal
final class AssociationFilterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AssociationFilterResolver.class);
    private static final Filter NONE = element -> false;

    private final NitriteEntityMapper entityMapper;
    private final @Nullable SubQueryExecutor subQueryExecutor;
    private final ValueResolver valueResolver;
    /** Callback into NitriteFilterBuilder for nested filter building. */
    // Follow-up: break this circular reference once the compile/build duality is resolved.
    private final FieldFilterProvider fieldFilterProvider;
    private final FieldFilterProvider operatorFiltersForPath;

    AssociationFilterResolver(
            NitriteEntityMapper entityMapper,
            @Nullable SubQueryExecutor subQueryExecutor,
            ValueResolver valueResolver,
            FieldFilterProvider fieldFilterProvider,
            FieldFilterProvider operatorFiltersForPath) {
        this.entityMapper = entityMapper;
        this.subQueryExecutor = subQueryExecutor;
        this.valueResolver = valueResolver;
        this.fieldFilterProvider = fieldFilterProvider;
        this.operatorFiltersForPath = operatorFiltersForPath;
    }

    @Nullable Filter buildAssociationFilter(
            RuntimePersistentEntity<?> entity,
            String field,
            Map<String, Object> operators,
            Object[] params,
            Map<String, Object> namedParameters) {

        if (!operators.containsKey(EQ)) {
            return null;
        }

        Object value = valueResolver.resolveValue(operators.get(EQ), params, namedParameters);
        if (value == null) {
            return null;
        }

        PathResolution resolution = PathResolver.resolve(entity, field);
        if (!resolution.isReference()) {
            return null;
        }
        if (resolution.chain().isEmpty()) {
            return null;
        }

        RuntimeAssociation<?> headAssoc = resolution.chain().getFirst();
        Relation.Kind kind = headAssoc.getKind();

        RuntimePersistentEntity<?> associatedEntity = headAssoc.getAssociatedEntity();
        if (!associatedEntity.hasIdentity()) {
            // The sub-query below filters on a single id property, so the target must have exactly
            // one. hasIdentity() is false both when the target has no identity and when it has a
            // composite one; neither can drive that filter.
            return null;
        }
        RuntimePersistentProperty<?> assocIdentity = associatedEntity.getIdentity();
        boolean useSubQuery = false;
        if (value instanceof String strValue) {
            if (assocIdentity.getType() == UUID.class) {
                useSubQuery = !looksLikeId(strValue, assocIdentity.getType());
            } else if (assocIdentity.getType() == String.class) {
                useSubQuery = strValue.contains(" ");
            }
        }

        if (kind == Relation.Kind.MANY_TO_ONE) {
            // Dotted MANY_TO_ONE paths (e.g. "author.name") go through buildNestedFilter.
            if (field.contains(".")) {
                return null;
            }
            if (!useSubQuery || subQueryExecutor == null) {
                return null;
            }
            // Use the association's persisted FK name (e.g. "widget_id"), not the raw property
            // name ("widget"), so the final where(...).in(ids) targets the stored column.
            return buildForwardLookupFilter(resolution.persistedField(), headAssoc, value, params, namedParameters);
        }

        if (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY) {
            if (!useSubQuery || subQueryExecutor == null) {
                return null;
            }
            // For dotted paths (e.g. "children.name"), extract target from the resolved terminal.
            String targetPropertyName = (field.contains(".") && resolution.terminal() != null)
                ? resolution.terminal().getName()
                : null;
            return buildReverseLookupFilter(entity, headAssoc, targetPropertyName, value, params, namedParameters);
        }

        return null;
    }

    private @Nullable Filter buildReverseLookupFilter(
            RuntimePersistentEntity<?> entity,
            RuntimeAssociation<?> association, @Nullable String targetPropertyName,
            Object value, Object[] params, Map<String, Object> namedParameters) {

        if (subQueryExecutor == null) {
            return null;
        }
        String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
        if (mappedBy == null) {
            return null;
        }
        if (targetPropertyName == null || targetPropertyName.equals(mappedBy)) {
            return null;
        }

        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();

        RuntimePersistentProperty<?> targetProperty = associatedEntity.getPropertyByName(targetPropertyName);
        if (targetProperty == null) {
            for (RuntimePersistentProperty<?> p : associatedEntity.getPersistentProperties()) {
                if (p.getPersistedName().equals(targetPropertyName)) {
                    targetProperty = p;
                    break;
                }
            }
        }
        if (targetProperty == null) {
            return null;
        }

        RuntimePersistentProperty<?> backRefProp = associatedEntity.getPropertyByName(mappedBy);
        if (backRefProp == null) {
            return null;
        }
        String backRefPersistedName = backRefProp.getPersistedName();

        Map<String, Object> subFilterMap = Collections.singletonMap(
            targetProperty.getPersistedName(), Collections.singletonMap(EQ, value));

        List<CompositeJoinColumn> joinColumns = entityMapper.getCompositeJoinColumns(
            associatedEntity.getIntrospection().getBeanType(), mappedBy);
        if (!joinColumns.isEmpty()) {
            return buildCompositeReverseLookupFilter(
                entity, associatedEntity, joinColumns, subFilterMap, params, namedParameters);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Reverse lookup sub-query: entity={}, filter={}, backRef={}",
                associatedEntity.getName(), subFilterMap, backRefPersistedName);
        }

        List<Object> matchingValues = subQueryExecutor.executeSubQuery(
            associatedEntity, subFilterMap, backRefPersistedName, false, params, namedParameters);
        if (matchingValues.isEmpty()) {
            return NONE;
        }

        // The identity is stored under the canonical document field, not the mapped identity name.
        Comparable<?>[] ids = toComparableArray(matchingValues);
        return ids.length == 0 ? NONE : NitriteFilterUtils.in(NitriteEntityMapper.ID_FIELD, ids);
    }

    private @Nullable Filter buildForwardLookupFilter(
            String field,
            RuntimeAssociation<?> association, Object value,
            Object[] params, Map<String, Object> namedParameters) {

        if (subQueryExecutor == null) {
            return null;
        }
        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        List<Map<String, Object>> orClauses = new ArrayList<>();
        for (RuntimePersistentProperty<?> p : associatedEntity.getPersistentProperties()) {
            if (p.getType().isInstance(value)) {
                orClauses.add(Collections.singletonMap(p.getPersistedName(), Collections.singletonMap(EQ, value)));
            }
        }
        if (orClauses.isEmpty()) {
            return null;
        }

        Map<String, Object> subFilterMap = Collections.singletonMap(OR, orClauses);
        List<Object> matchingIds = subQueryExecutor.executeSubQuery(
            associatedEntity, subFilterMap, null, false, params, namedParameters);
        if (matchingIds.isEmpty()) {
            return NONE;
        }

        Comparable<?>[] ids = toComparableArray(matchingIds);
        return ids.length == 0 ? NONE : NitriteFilterUtils.in(field, ids);
    }

    @Nullable Filter buildNestedFilter(
            RuntimePersistentEntity<?> entity,
            String fieldPath,
            Map<String, Object> operators,
            Object[] params,
            Map<String, Object> namedParameters) {

        int dotIdx = fieldPath.indexOf('.');
        String firstPart = fieldPath.substring(0, dotIdx);
        String remaining = fieldPath.substring(dotIdx + 1);

        // Resolve the first segment via metadata instead of name-guessing heuristics.
        PathResolution firstResolution = PathResolver.resolve(entity, firstPart);
        RuntimePersistentProperty<?> prop = firstResolution.chain().isEmpty()
            ? null
            : firstResolution.chain().getFirst();
        if (prop == null && firstResolution.terminal() != null) {
            prop = firstResolution.terminal();
        }

        String fieldName = firstResolution.persistedField();

        if (prop instanceof RuntimeAssociation<?> assoc) {
            Relation.Kind kind = assoc.getKind();
            boolean isCollection = kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY;
            boolean isManyToOne = kind == Relation.Kind.MANY_TO_ONE;
            RuntimePersistentEntity<?> associatedEntity = assoc.getAssociatedEntity();

            if (isCollection) {
                if (subQueryExecutor == null) {
                    Filter subFilter = fieldFilterProvider.build(associatedEntity, remaining, operators, params, namedParameters);
                    return NitriteFilterUtils.elemMatch(fieldName, subFilter);
                }

                Map<String, Object> resolvedOperators = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : operators.entrySet()) {
                    resolvedOperators.put(entry.getKey(), valueResolver.resolveValue(entry.getValue(), params, namedParameters));
                }
                Map<String, Object> subFilterMap = Collections.singletonMap(remaining, resolvedOperators);

                String mappedBy = assoc.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
                if (mappedBy == null) {
                    if (kind == Relation.Kind.MANY_TO_MANY) {
                        List<Object> matchingIds = subQueryExecutor.executeSubQuery(
                            associatedEntity, subFilterMap, null, false, params, namedParameters);
                        if (matchingIds.isEmpty()) {
                            return NONE;
                        }
                        return pair -> {
                            Document doc = pair.getSecond();
                            Object val = doc.get(fieldName);
                            if (val instanceof Collection<?> coll) {
                                for (Object id : matchingIds) {
                                    if (coll.contains(id)) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        };
                    }
                    return NONE;
                }

                RuntimePersistentProperty<?> backRefProp = associatedEntity.getPropertyByName(mappedBy);
                if (backRefProp == null) {
                    return NONE;
                }
                String backRefPersistedName = backRefProp.getPersistedName();

                List<CompositeJoinColumn> joinColumns = entityMapper.getCompositeJoinColumns(
                    associatedEntity.getIntrospection().getBeanType(), mappedBy);
                if (!joinColumns.isEmpty()) {
                    return buildCompositeReverseLookupFilter(
                        entity, associatedEntity, joinColumns, subFilterMap, params, namedParameters);
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Collection reverse lookup: entity={}, filter={}, backRef={}",
                        associatedEntity.getName(), subFilterMap, backRefPersistedName);
                }

                List<Object> matchingValues = subQueryExecutor.executeSubQuery(
                    associatedEntity, subFilterMap, backRefPersistedName, false, params, namedParameters);
                if (matchingValues.isEmpty()) {
                    return NONE;
                }

                // The identity is stored under the canonical document field, not under the mapped
                // identity name, so an entity whose id carries a mapped name is matched here too.
                Comparable<?>[] ids = toComparableArray(matchingValues);
                return ids.length == 0 ? NONE : NitriteFilterUtils.in(NitriteEntityMapper.ID_FIELD, ids);

            } else if (isManyToOne && subQueryExecutor != null) {
                Map<String, Object> resolvedOperators = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : operators.entrySet()) {
                    resolvedOperators.put(entry.getKey(), valueResolver.resolveValue(entry.getValue(), params, namedParameters));
                }
                Map<String, Object> subFilterMap = Collections.singletonMap(remaining, resolvedOperators);
                if (associatedEntity.hasCompositeIdentity()) {
                    return buildCompositeForwardLookupFilter(
                        entity, firstPart, associatedEntity, subFilterMap, params, namedParameters);
                }
                List<Object> matchingIds = subQueryExecutor.executeSubQuery(
                    associatedEntity, subFilterMap, null, false, params, namedParameters);
                if (matchingIds.isEmpty()) {
                    return NONE;
                }
                Comparable<?>[] ids = toComparableArray(matchingIds);
                return ids.length == 0 ? NONE : NitriteFilterUtils.in(fieldName, ids);
            } else {
                return operatorFiltersForPath.build(entity, fieldName + "." + remaining, operators, params, namedParameters);
            }
        }

        // Non-association property or unresolved path — use persisted field name for dotted access.
        return operatorFiltersForPath.build(entity, fieldName + "." + remaining, operators, params, namedParameters);
    }

    private Filter buildCompositeForwardLookupFilter(
        RuntimePersistentEntity<?> entity,
        String associationName,
        RuntimePersistentEntity<?> associatedEntity,
        Map<String, Object> subFilterMap,
        Object[] params,
        Map<String, Object> namedParameters) {
        List<CompositeJoinColumn> joinColumns = entityMapper.getCompositeJoinColumns(
            entity.getIntrospection().getBeanType(), associationName);
        if (joinColumns.isEmpty()) {
            return NONE;
        }

        SubQueryExecutor executor = Objects.requireNonNull(subQueryExecutor);
        List<Object> matchingDocuments = executor.executeSubQuery(
            associatedEntity, subFilterMap, null, true, params, namedParameters);
        List<Filter> matchingRows = new ArrayList<>(matchingDocuments.size());
        for (Object matchingDocument : matchingDocuments) {
            if (!(matchingDocument instanceof Document document)) {
                continue;
            }
            List<Filter> rowFilters = new ArrayList<>(joinColumns.size());
            boolean completeIdentity = true;
            for (CompositeJoinColumn joinColumn : joinColumns) {
                RuntimePersistentProperty<?> referenced = entityMapper.findPropertyByNameOrPersistedName(
                    associatedEntity, joinColumn.referencedProperty());
                Object value = referenced == null ? null : document.get(referenced.getPersistedName());
                if (value == null) {
                    completeIdentity = false;
                    break;
                }
                rowFilters.add(NitriteFilterUtils.eq(joinColumn.localName(), value));
            }
            if (completeIdentity) {
                matchingRows.add(rowFilters.size() == 1
                    ? rowFilters.getFirst()
                    : Filter.and(rowFilters.toArray(new Filter[0])));
            }
        }
        if (matchingRows.isEmpty()) {
            return NONE;
        }
        return matchingRows.size() == 1
            ? matchingRows.getFirst()
            : Filter.or(matchingRows.toArray(new Filter[0]));
    }

    private Filter buildCompositeReverseLookupFilter(
        RuntimePersistentEntity<?> entity,
        RuntimePersistentEntity<?> associatedEntity,
        List<CompositeJoinColumn> joinColumns,
        Map<String, Object> subFilterMap,
        Object[] params,
        Map<String, Object> namedParameters) {
        SubQueryExecutor executor = Objects.requireNonNull(subQueryExecutor);
        List<Object> matchingDocuments = executor.executeSubQuery(
            associatedEntity, subFilterMap, null, true, params, namedParameters);
        List<Filter> matchingRows = new ArrayList<>(matchingDocuments.size());
        for (Object matchingDocument : matchingDocuments) {
            if (!(matchingDocument instanceof Document document)) {
                continue;
            }
            List<Filter> rowFilters = new ArrayList<>(joinColumns.size());
            boolean completeReference = true;
            for (CompositeJoinColumn joinColumn : joinColumns) {
                RuntimePersistentProperty<?> referenced = entityMapper.findPropertyByNameOrPersistedName(
                    entity, joinColumn.referencedProperty());
                if (referenced == null) {
                    completeReference = false;
                    break;
                }
                Object value = document.get(joinColumn.localName());
                if (value == null) {
                    completeReference = false;
                    break;
                }
                rowFilters.add(NitriteFilterUtils.eq(referenced.getPersistedName(), value));
            }
            if (completeReference) {
                matchingRows.add(rowFilters.size() == 1
                    ? rowFilters.getFirst()
                    : Filter.and(rowFilters.toArray(new Filter[0])));
            }
        }
        if (matchingRows.isEmpty()) {
            return NONE;
        }
        return matchingRows.size() == 1
            ? matchingRows.getFirst()
            : Filter.or(matchingRows.toArray(new Filter[0]));
    }

    private boolean looksLikeId(String value, Class<?> idType) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (idType == UUID.class) {
            if (value.length() == 36 && value.charAt(8) == '-' && value.charAt(13) == '-'
                    && value.charAt(18) == '-' && value.charAt(23) == '-') {
                try {
                    UUID.fromString(value);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    private static Comparable<?>[] toComparableArray(List<Object> values) {
        return values.stream()
            .filter(Objects::nonNull)
            .map(id -> id instanceof Comparable<?> c ? c : id.toString())
            .toArray(Comparable<?>[]::new);
    }

    @FunctionalInterface
    interface FieldFilterProvider {
        @Nullable Filter build(RuntimePersistentEntity<?> entity, String field, Map<String, Object> ops, Object[] params, Map<String, Object> named);
    }
}
