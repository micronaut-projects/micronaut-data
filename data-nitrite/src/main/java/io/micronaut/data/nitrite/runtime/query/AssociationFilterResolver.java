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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder.SubQueryExecutor;
import io.micronaut.data.nitrite.runtime.query.PathResolver.PathResolution;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
final class AssociationFilterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AssociationFilterResolver.class);
    private static final Filter NONE = element -> false;

    private final SubQueryExecutor subQueryExecutor;
    private final ValueResolver valueResolver;
    /** Callback into NitriteFilterBuilder for nested filter building. */
    // TODO: break this circular reference once the compile/build duality is resolved
    private final FieldFilterProvider fieldFilterProvider;
    private final FieldFilterProvider operatorFiltersForPath;

    @FunctionalInterface
    interface FieldFilterProvider {
        Filter build(RuntimePersistentEntity<?> entity, String field, Map<String, Object> ops, Object[] params, Map<String, Object> named);
    }

    AssociationFilterResolver(
            SubQueryExecutor subQueryExecutor,
            ValueResolver valueResolver,
            FieldFilterProvider fieldFilterProvider,
            FieldFilterProvider operatorFiltersForPath) {
        this.subQueryExecutor = subQueryExecutor;
        this.valueResolver = valueResolver;
        this.fieldFilterProvider = fieldFilterProvider;
        this.operatorFiltersForPath = operatorFiltersForPath;
    }

    Filter buildAssociationFilter(
            RuntimePersistentEntity<?> entity,
            String field,
            Map<String, Object> operators,
            Object[] params,
            Map<String, Object> namedParameters) {

        if (!operators.containsKey("$eq")) return null;

        Object value = valueResolver.resolveValue(operators.get("$eq"), params, namedParameters);
        if (value == null) return null;

        PathResolution resolution = PathResolver.resolve(entity, field);
        if (!resolution.isReference()) return null;
        if (resolution.chain().isEmpty()) return null;

        RuntimeAssociation<?> headAssoc = resolution.chain().get(0);
        Relation.Kind kind = headAssoc.getKind();

        RuntimePersistentProperty<?> assocIdentity = headAssoc.getAssociatedEntity().getIdentity();
        boolean useSubQuery = false;
        if (value instanceof String strValue) {
            if (assocIdentity.getType() == java.util.UUID.class) {
                useSubQuery = !looksLikeId(strValue, assocIdentity.getType());
            } else if (assocIdentity.getType() == String.class) {
                useSubQuery = strValue.contains(" ");
            }
        }

        if (kind == Relation.Kind.MANY_TO_ONE) {
            // Dotted MANY_TO_ONE paths (e.g. "author.name") go through buildNestedFilter.
            if (field.contains(".")) return null;
            if (!useSubQuery || subQueryExecutor == null) return null;
            return buildForwardLookupFilter(field, headAssoc, value, params, namedParameters);
        }

        if (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY) {
            if (!useSubQuery || subQueryExecutor == null) return null;
            // For dotted paths (e.g. "children.name"), extract target from the resolved terminal.
            String targetPropertyName = (field.contains(".") && resolution.terminal() != null)
                ? resolution.terminal().getName()
                : null;
            return buildReverseLookupFilter(entity, headAssoc, targetPropertyName, value, params, namedParameters);
        }

        return null;
    }

    private Filter buildReverseLookupFilter(
            RuntimePersistentEntity<?> entity,
            RuntimeAssociation<?> association, String targetPropertyName,
            Object value, Object[] params, Map<String, Object> namedParameters) {

        String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
        if (mappedBy == null) return null;
        if (targetPropertyName == null || targetPropertyName.equals(mappedBy)) return null;

        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();

        RuntimePersistentProperty<?> targetProperty = associatedEntity.getPropertyByName(targetPropertyName);
        if (targetProperty == null) {
            for (RuntimePersistentProperty<?> p : associatedEntity.getPersistentProperties()) {
                if (p.getPersistedName().equals(targetPropertyName)) { targetProperty = p; break; }
            }
        }
        if (targetProperty == null) return null;

        RuntimePersistentProperty<?> backRefProp = associatedEntity.getPropertyByName(mappedBy);
        if (backRefProp == null) return null;
        String backRefPersistedName = backRefProp.getPersistedName();

        Map<String, Object> subFilterMap = Collections.singletonMap(
            targetProperty.getPersistedName(), Collections.singletonMap("$eq", value));

        LOG.debug("Reverse lookup sub-query: entity={}, filter={}, backRef={}",
            associatedEntity.getName(), subFilterMap, backRefPersistedName);

        List<Object> matchingValues = subQueryExecutor.executeSubQuery(
            associatedEntity, subFilterMap, backRefPersistedName, params, namedParameters);
        if (matchingValues.isEmpty()) return NONE;

        String idField = entity.getIdentity().getPersistedName();
        Comparable<?>[] ids = toComparableArray(matchingValues);
        return ids.length == 0 ? NONE : FluentFilter.where(idField).in(ids);
    }

    private Filter buildForwardLookupFilter(
            String field,
            RuntimeAssociation<?> association, Object value,
            Object[] params, Map<String, Object> namedParameters) {

        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        List<Map<String, Object>> orClauses = new ArrayList<>();
        for (RuntimePersistentProperty<?> p : associatedEntity.getPersistentProperties()) {
            if (p.getType().isInstance(value)) {
                orClauses.add(Collections.singletonMap(p.getPersistedName(), Collections.singletonMap("$eq", value)));
            }
        }
        if (orClauses.isEmpty()) return null;

        Map<String, Object> subFilterMap = Collections.singletonMap("$or", orClauses);
        List<Object> matchingIds = subQueryExecutor.executeSubQuery(
            associatedEntity, subFilterMap, null, params, namedParameters);
        if (matchingIds.isEmpty()) return NONE;

        Comparable<?>[] ids = toComparableArray(matchingIds);
        return ids.length == 0 ? NONE : FluentFilter.where(field).in(ids);
    }

    Filter buildNestedFilter(
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
            : firstResolution.chain().get(0);
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
                    return FluentFilter.where(fieldName).elemMatch(subFilter);
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
                            associatedEntity, subFilterMap, null, params, namedParameters);
                        if (matchingIds.isEmpty()) return NONE;
                        return pair -> {
                            org.dizitart.no2.collection.Document doc = pair.getSecond();
                            Object val = doc.get(fieldName);
                            if (val instanceof java.util.Collection<?> coll) {
                                for (Object id : matchingIds) { if (coll.contains(id)) return true; }
                            }
                            return false;
                        };
                    }
                    return NONE;
                }

                RuntimePersistentProperty<?> backRefProp = associatedEntity.getPropertyByName(mappedBy);
                if (backRefProp == null) return NONE;
                String backRefPersistedName = backRefProp.getPersistedName();

                LOG.debug("Collection reverse lookup: entity={}, filter={}, backRef={}",
                    associatedEntity.getName(), subFilterMap, backRefPersistedName);

                List<Object> matchingValues = subQueryExecutor.executeSubQuery(
                    associatedEntity, subFilterMap, backRefPersistedName, params, namedParameters);
                if (matchingValues.isEmpty()) return NONE;

                String idField = entity.getIdentity().getPersistedName();
                Comparable<?>[] ids = toComparableArray(matchingValues);
                return ids.length == 0 ? NONE : FluentFilter.where(idField).in(ids);

            } else if (isManyToOne && subQueryExecutor != null) {
                Map<String, Object> resolvedOperators = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : operators.entrySet()) {
                    resolvedOperators.put(entry.getKey(), valueResolver.resolveValue(entry.getValue(), params, namedParameters));
                }
                Map<String, Object> subFilterMap = Collections.singletonMap(remaining, resolvedOperators);
                List<Object> matchingIds = subQueryExecutor.executeSubQuery(
                    associatedEntity, subFilterMap, null, params, namedParameters);
                if (matchingIds.isEmpty()) return NONE;
                Comparable<?>[] ids = toComparableArray(matchingIds);
                return ids.length == 0 ? NONE : FluentFilter.where(fieldName).in(ids);
            } else {
                return operatorFiltersForPath.build(entity, fieldName + "." + remaining, operators, params, namedParameters);
            }
        }

        // Non-association property or unresolved path — use persisted field name for dotted access.
        return operatorFiltersForPath.build(entity, fieldName + "." + remaining, operators, params, namedParameters);
    }

    private boolean looksLikeId(String value, Class<?> idType) {
        if (value == null || value.isEmpty()) return false;
        if (idType == java.util.UUID.class) {
            if (value.length() == 36 && value.charAt(8) == '-' && value.charAt(13) == '-'
                    && value.charAt(18) == '-' && value.charAt(23) == '-') {
                try { java.util.UUID.fromString(value); return true; } catch (IllegalArgumentException e) { return false; }
            }
            return false;
        }
        return true;
    }

    private static Comparable<?>[] toComparableArray(List<Object> values) {
        return values.stream()
            .filter(java.util.Objects::nonNull)
            .map(id -> id instanceof Comparable<?> c ? c : id.toString())
            .toArray(Comparable<?>[]::new);
    }
}
