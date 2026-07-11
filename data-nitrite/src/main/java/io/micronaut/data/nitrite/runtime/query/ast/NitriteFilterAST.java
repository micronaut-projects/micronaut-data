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
package io.micronaut.data.nitrite.runtime.query.ast;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A structured AST for Nitrite filters.
 *
 * @since 1.0.0
 */
@Internal
public sealed interface NitriteFilterAST extends CompiledNitriteFilter {

    @Override
    default Filter bind(Object[] params, Map<String, Object> namedParameters) {
        return toFilter(params, namedParameters);
    }

    /**
     * Convert this AST node to a Nitrite Filter using the provided parameters.
     *
     * @param params positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    Filter toFilter(Object[] params, Map<String, Object> namedParameters);

    /**
     * Logical AND of multiple filters.
     *
     * @param children the child filter AST nodes
     */
    record AndNode(List<NitriteFilterAST> children) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            List<Filter> results = new ArrayList<>(children.size());
            for (NitriteFilterAST child : children) {
                Filter f = child.toFilter(params, namedParameters);
                if (f != null && f != Filter.ALL) {
                    results.add(f);
                }
            }
            return results.isEmpty() ? Filter.ALL : results.size() == 1 ? results.getFirst() : Filter.and(results.toArray(new Filter[0]));
        }
    }

    /**
     * Logical OR of multiple filters.
     *
     * @param children the child filter AST nodes
     */
    record OrNode(List<NitriteFilterAST> children) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            List<Filter> results = new ArrayList<>(children.size());
            for (NitriteFilterAST child : children) {
                Filter f = child.toFilter(params, namedParameters);
                if (f != null && f != Filter.ALL) {
                    results.add(f);
                }
            }
            return results.isEmpty() ? Filter.ALL : results.size() == 1 ? results.getFirst() : Filter.or(results.toArray(new Filter[0]));
        }
    }

    /**
     * A highly optimized node for standard property equality.
     * Bypasses all metadata lookups and dynamic strategy detection.
     *
     * @param preparer prepares the resolved value for filtering
     * @param evaluator builds the operator filter
     * @param entity the runtime persistent entity
     * @param persistedName the persisted field name
     * @param rawField the raw field name
     * @param valueExpression the compiled value expression
     */
    record SimpleEqualityNode(
        FieldValuePreparer preparer,
        OperatorFilterEvaluator evaluator,
        RuntimePersistentEntity<?> entity,
        String persistedName,
        String rawField,
        CompiledValue valueExpression
    ) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            Object resolvedValue = valueExpression.resolve(params, namedParameters);
            Object finalValue = preparer.prepare(persistedName, resolvedValue);
            return evaluator.evaluate(entity, persistedName, "$eq", finalValue, params, namedParameters);
        }
    }

    /**
     * A specialized node for range and other standard operators on simple fields.
     *
     * @param preparer prepares the resolved value for filtering
     * @param evaluator builds the operator filter
     * @param entity the runtime persistent entity
     * @param persistedName the persisted field name
     * @param rawField the raw field name
     * @param operators the list of operator bindings
     */
    record SimpleOperatorNode(
        FieldValuePreparer preparer,
        OperatorFilterEvaluator evaluator,
        RuntimePersistentEntity<?> entity,
        String persistedName,
        String rawField,
        List<OperatorBinding> operators
    ) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            if (operators.size() == 1) {
                return operators.getFirst().toFilter(preparer, evaluator, entity, persistedName, rawField, params, namedParameters);
            }
            List<Filter> results = new ArrayList<>(operators.size());
            for (OperatorBinding op : operators) {
                Filter f = op.toFilter(preparer, evaluator, entity, persistedName, rawField, params, namedParameters);
                if (f != null && f != Filter.ALL) {
                    results.add(f);
                }
            }
            return results.isEmpty() ? Filter.ALL : results.size() == 1 ? results.getFirst() : Filter.and(results.toArray(new Filter[0]));
        }
    }

    /**
     * A binding for a single operator.
     *
     * @param op the operator name
     * @param valueExpression the compiled value expression
     */
    record OperatorBinding(String op, CompiledValue valueExpression) {
        /**
         * Converts this operator binding into a functional Nitrite Filter by resolving and preparing the value.
         * @param preparer the preparer to coerce the resolved value
         * @param evaluator the evaluator to build the final operator filter
         * @param entity the runtime persistent entity being queried
         * @param persistedName the persisted name of the field
         * @param rawField the raw property path of the field
         * @param params the positional parameters array
         * @param namedParameters the named parameters map
         * @return the constructed Nitrite Filter representing this binding
         */
        public Filter toFilter(FieldValuePreparer preparer, OperatorFilterEvaluator evaluator, RuntimePersistentEntity<?> entity, String persistedName, String rawField, Object[] params, Map<String, Object> namedParameters) {
            Object resolvedValue = valueExpression.resolve(params, namedParameters);
            Object finalValue = preparer.prepare(persistedName, resolvedValue);
            return evaluator.evaluate(entity, persistedName, op, finalValue, params, namedParameters);
        }
    }

    /**
     * Prepares a raw value for use in a filter (coercion, UUID handling, type conversion).
     */
    @FunctionalInterface
    interface FieldValuePreparer {
        /**
         * Prepares a raw value for use in a Nitrite filter (e.g., type coercion, UUID string conversion).
         * @param field the name of the field the value belongs to
         * @param value the raw value to prepare
         * @return the prepared value ready for Nitrite filter matching
         */
        Object prepare(String field, Object value);
    }

    /**
     * Builds a Nitrite Filter for a single operator on a resolved field value.
     */
    @FunctionalInterface
    interface OperatorFilterEvaluator {
        /**
         * Evaluates and builds a Nitrite filter for a specific operator on a single field.
         * @param entity the runtime persistent entity being queried
         * @param field the persisted name of the field
         * @param op the operator string (e.g., "$eq", "$gt")
         * @param value the value to compare against
         * @param params the positional parameters array
         * @param namedParameters the named parameters map
         * @return the constructed Nitrite Filter for the given operator
         */
        Filter evaluate(RuntimePersistentEntity<?> entity, String field, String op, Object value,
                        Object[] params, Map<String, Object> namedParameters);
    }

    /**
     * Evaluates association and nested-path fields at bind time.
     */
    @FunctionalInterface
    interface AssociationFieldEvaluator {
        /**
         * Evaluates and builds a Nitrite filter for an association field (e.g., a joined collection).
         * @param entity the runtime persistent entity being queried
         * @param rawField the raw property path of the association
         * @param persistedName the persisted name of the association
         * @param operators the map of nested operators for this association
         * @param params the positional parameters array
         * @param namedParameters the named parameters map
         * @return the constructed Nitrite Filter for the association
         */
        Filter evaluate(RuntimePersistentEntity<?> entity, String rawField, String persistedName,
                        Map<String, Object> operators, Object[] params, Map<String, Object> namedParameters);
    }

    /**
     * A node for association and nested-path fields (dot-notation, {@code ONE_TO_MANY}, etc.).
     * Resolution is fully deferred to bind time via the evaluator.
     *
     * @param evaluator the bind-time evaluator
     * @param entity the runtime persistent entity
     * @param rawField the raw field name
     * @param persistedName the resolved persisted field name (used as fallback when no association matches)
     * @param operators the map of operators (raw, unresolved)
     */
    record AssociationFieldNode(
        AssociationFieldEvaluator evaluator,
        RuntimePersistentEntity<?> entity,
        String rawField,
        String persistedName,
        Map<String, Object> operators
    ) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            return evaluator.evaluate(entity, rawField, persistedName, operators, params, namedParameters);
        }
    }

    /**
     * A filter that matches everything.
     */
    record AllNode() implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            return Filter.ALL;
        }
    }
}
