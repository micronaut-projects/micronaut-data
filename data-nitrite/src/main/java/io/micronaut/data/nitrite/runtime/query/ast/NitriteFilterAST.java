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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;

import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EQ;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.IN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NIN;

/**
 * A structured AST for Nitrite filters.
 *
 * @since 5.2.0
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
            List<Filter> results = children.stream()
                .map(child -> child.toFilter(params, namedParameters))
                .filter(f -> f != null && !f.equals(Filter.ALL))
                .toList();
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
            List<Filter> results = children.stream()
                .map(child -> child.toFilter(params, namedParameters))
                .filter(f -> f != null && !f.equals(Filter.ALL))
                .toList();
            return results.isEmpty() ? Filter.ALL : results.size() == 1 ? results.getFirst() : Filter.or(results.toArray(new Filter[0]));
        }
    }

    /**
     * Logical NOT of a filter.
     *
     * @param child the child filter AST node
     */
    record NotNode(NitriteFilterAST child) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            return child.toFilter(params, namedParameters).not();
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
            Filter f = evaluator.evaluate(entity, persistedName, EQ, finalValue, params, namedParameters);
            return f != null ? f : Filter.ALL;
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
            List<Filter> results = operators.stream()
                .map(op -> op.toFilter(preparer, evaluator, entity, persistedName, rawField, params, namedParameters))
                .filter(f -> f != null && !f.equals(Filter.ALL))
                .toList();
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
            Filter f = evaluator.evaluate(entity, persistedName, op, finalValue, params, namedParameters);
            return f != null ? f : Filter.ALL;
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
        @Nullable Object prepare(String field, @Nullable Object value);
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
        @Nullable Filter evaluate(RuntimePersistentEntity<?> entity, String field, String op, @Nullable Object value,
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
        @Nullable Filter evaluate(RuntimePersistentEntity<?> entity, String rawField, String persistedName,
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
            Filter f = evaluator.evaluate(entity, rawField, persistedName, operators, params, namedParameters);
            return f != null ? f : Filter.ALL;
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

    /**
     * A filter that matches nothing.
     */
    record NoneNode() implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            return element -> false;
        }
    }

    /**
     * A computed-expression comparison (e.g. {@code cb.length(...)}, {@code cb.prod(...)}),
     * evaluated in application code against each candidate document since Nitrite has no
     * {@code $expr}/aggregation-expression support of its own.
     *
     * @param op the comparison operator ({@code $eq}, {@code $ne}, {@code $gt}, {@code $gte}, {@code $lt}, {@code $lte})
     * @param left the left-hand computed value
     * @param right the right-hand value to compare against
     */
    record ExprNode(String op, ExprValueNode left, ExprValueNode right) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            return pair -> {
                Document doc = pair.getSecond();
                Object lhs = left.evaluate(doc, params, namedParameters);
                Object rhs = right.evaluate(doc, params, namedParameters);
                return compare(op, lhs, rhs);
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static boolean compare(String op, @Nullable Object lhs, @Nullable Object rhs) {
            if (IN.equals(op) || NIN.equals(op)) {
                boolean contains = rhs instanceof Iterable<?> iterable && contains(iterable, lhs);
                return IN.equals(op) == contains;
            }
            if (lhs == null || rhs == null) {
                boolean eq = Objects.equals(lhs, rhs);
                return NE.equals(op) ? !eq : EQ.equals(op) && eq;
            }
            int cmp;
            if (lhs instanceof Number ln && rhs instanceof Number rn) {
                cmp = Double.compare(ln.doubleValue(), rn.doubleValue());
            } else if (lhs instanceof Comparable lc && lhs.getClass().isInstance(rhs)) {
                cmp = lc.compareTo(rhs);
            } else {
                cmp = lhs.equals(rhs) ? 0 : 1;
            }
            return switch (op) {
                case EQ -> cmp == 0;
                case NE -> cmp != 0;
                case GT -> cmp > 0;
                case GTE -> cmp >= 0;
                case LT -> cmp < 0;
                case LTE -> cmp <= 0;
                default -> false;
            };
        }

        private static boolean contains(Iterable<?> iterable, @Nullable Object value) {
            for (Object item : iterable) {
                if (Objects.equals(item, value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A node in a small computed-value tree used by {@link ExprNode}, evaluated directly against a document.
     */
    sealed interface ExprValueNode {

        /**
         * Evaluate this node against a document.
         *
         * @param doc the candidate document
         * @param params positional parameters
         * @param namedParameters named parameters
         * @return the computed value
         */
        @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters);

        private static @Nullable Integer toInt(@Nullable Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value == null) {
                return null;
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * A reference to a persisted document field.
         *
         * @param persistedName the persisted field name
         */
        record FieldRef(String persistedName) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                return doc.get(persistedName);
            }
        }

        /**
         * A literal or bound-parameter value.
         *
         * @param value the compiled value
         */
        record Literal(CompiledValue value) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                return value.resolve(params, namedParameters);
            }
        }

        /**
         * A list of expression values, used for computed {@code IN} comparisons.
         *
         * @param values the list element expressions
         */
        record ListValue(List<ExprValueNode> values) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                return values.stream()
                    .map(value -> value.evaluate(doc, params, namedParameters))
                    .toList();
            }
        }

        /**
         * The character length of a string value, mirroring Mongo's {@code $strLenCP}.
         *
         * @param inner the value to measure
         */
        record StrLen(ExprValueNode inner) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object v = inner.evaluate(doc, params, namedParameters);
                return v == null ? null : v.toString().length();
            }
        }

        /**
         * Lowercase string conversion, mirroring Mongo's {@code $toLower}.
         *
         * @param inner the value to convert
         */
        record ToLower(ExprValueNode inner) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object v = inner.evaluate(doc, params, namedParameters);
                return v == null ? null : v.toString().toLowerCase(Locale.ROOT);
            }
        }

        /**
         * Uppercase string conversion, mirroring Mongo's {@code $toUpper}.
         *
         * @param inner the value to convert
         */
        record ToUpper(ExprValueNode inner) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object v = inner.evaluate(doc, params, namedParameters);
                return v == null ? null : v.toString().toUpperCase(Locale.ROOT);
            }
        }

        /**
         * The product of two or more values, mirroring Mongo's {@code $multiply}.
         *
         * @param operands the values to multiply
         */
        record Multiply(List<ExprValueNode> operands) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                double result = 1;
                for (ExprValueNode operand : operands) {
                    Object v = operand.evaluate(doc, params, namedParameters);
                    if (!(v instanceof Number num)) {
                        return null;
                    }
                    result *= num.doubleValue();
                }
                return result;
            }
        }

        /**
         * String concatenation, mirroring Mongo's {@code $concat}.
         *
         * @param operands the values to concatenate
         */
        record Concat(List<ExprValueNode> operands) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                StringBuilder result = new StringBuilder();
                for (ExprValueNode operand : operands) {
                    Object v = operand.evaluate(doc, params, namedParameters);
                    if (v == null) {
                        return null;
                    }
                    result.append(v);
                }
                return result.toString();
            }
        }

        /**
         * Code-point substring, mirroring Mongo's {@code $substrCP}.
         *
         * @param value the source string
         * @param start the start offset
         * @param length the length
         */
        record Substr(ExprValueNode value, ExprValueNode start, ExprValueNode length) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object v = value.evaluate(doc, params, namedParameters);
                Integer startValue = toInt(start.evaluate(doc, params, namedParameters));
                Integer lengthValue = toInt(length.evaluate(doc, params, namedParameters));
                if (v == null || startValue == null || lengthValue == null) {
                    return null;
                }
                String s = v.toString();
                int codePoints = s.codePointCount(0, s.length());
                int from = Math.clamp(startValue, 0, codePoints);
                int to = Math.clamp(from + Math.max(0, lengthValue), from, codePoints);
                return s.substring(s.offsetByCodePoints(0, from), s.offsetByCodePoints(0, to));
            }
        }

        /**
         * Rightmost code points of a string.
         *
         * @param value the source string
         * @param length the length
         */
        record Right(ExprValueNode value, ExprValueNode length) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object v = value.evaluate(doc, params, namedParameters);
                Integer lengthValue = toInt(length.evaluate(doc, params, namedParameters));
                if (v == null || lengthValue == null) {
                    return null;
                }
                String s = v.toString();
                int codePoints = s.codePointCount(0, s.length());
                int from = Math.max(0, codePoints - Math.max(0, lengthValue));
                return s.substring(s.offsetByCodePoints(0, from));
            }
        }

        /**
         * Numeric division, mirroring Mongo's {@code $divide}.
         *
         * @param left the dividend
         * @param right the divisor
         */
        record Divide(ExprValueNode left, ExprValueNode right) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object lhs = left.evaluate(doc, params, namedParameters);
                Object rhs = right.evaluate(doc, params, namedParameters);
                if (!(lhs instanceof Number ln) || !(rhs instanceof Number rn) || rn.doubleValue() == 0) {
                    return null;
                }
                return ln.doubleValue() / rn.doubleValue();
            }
        }

        /**
         * Numeric conversion, mirroring Mongo's {@code $toDouble}.
         *
         * @param inner the value to convert
         */
        record ToDouble(ExprValueNode inner) implements ExprValueNode {
            @Override
            public @Nullable Object evaluate(Document doc, Object[] params, Map<String, Object> namedParameters) {
                Object v = inner.evaluate(doc, params, namedParameters);
                if (v instanceof Number number) {
                    return number.doubleValue();
                }
                if (v == null) {
                    return null;
                }
                try {
                    return Double.parseDouble(v.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

    }
}
