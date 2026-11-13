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
package io.micronaut.data.search.document.query;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders the predicates that the query DSL cannot express as Painless {@code script} queries,
 * which Elasticsearch and OpenSearch both evaluate per document from its doc values.
 *
 * <p>A comparison with a missing value is unknown, as in SQL, so the script returns {@code false}
 * for it and for its negation: a negated comparison is rendered as the opposite comparison rather than
 * a {@code must_not} around the script, which would match the documents missing a value.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
final class PainlessScripts {

    private static final String SCRIPT = "script";
    private static final Map<String, String> NEGATED_OPERATORS = Map.of(
        "==", "!=",
        "!=", "==",
        "<", ">=",
        "<=", ">",
        ">", "<=",
        ">=", "<"
    );

    private PainlessScripts() {
    }

    /**
     * The case folding of a string operand: none, or its lower or upper case form.
     */
    enum Fold {
        NONE(""),
        LOWER(".toLowerCase()"),
        UPPER(".toUpperCase()");

        private final String method;

        Fold(String method) {
            this.method = method;
        }
    }

    /**
     * @param operator A Java comparison operator
     * @return The operator of the negated comparison
     */
    static String negate(String operator) {
        String negated = NEGATED_OPERATORS.get(operator);
        if (negated == null) {
            throw new IllegalArgumentException("Not a comparison operator: " + operator);
        }
        return negated;
    }

    /**
     * Compares two properties of the same document, such as {@code numBitsRequired = floorOfSquareRoot}.
     *
     * @param left The doc values field of the left property
     * @param operator The Java comparison operator: {@code ==}, {@code !=}, {@code <}, {@code <=}, {@code >} or {@code >=}
     * @param right The doc values field of the right property
     * @param numeric Whether both properties are numbers, which compare directly rather than through {@code compareTo}
     * @return The script query, which does not match a document missing either property
     */
    static Map<String, Object> compare(String left, String operator, String right, boolean numeric) {
        // numbers and booleans compare with the operator itself; booleans only for equality
        String comparison = numeric || "==".equals(operator) || "!=".equals(operator)
            ? docValue(left) + " " + operator + " " + docValue(right)
            : docValue(left) + ".compareTo(" + docValue(right) + ") " + operator + " 0";
        return script(doc(left) + ".size() != 0 && " + doc(right) + ".size() != 0 && " + comparison, null);
    }

    /**
     * Compares the lower or upper case form of a string property with a value, such as {@code lower(code) > ?}.
     *
     * @param field The doc values field of the property
     * @param fold The case function applied to the property
     * @param operator The Java comparison operator
     * @param value The value, or a parameter placeholder
     * @param valueFold The case function applied to the value, {@link Fold#NONE} to compare it as given
     * @return The script query, which does not match a document without the property or a {@code null} value
     */
    static Map<String, Object> caseFoldedCompare(String field, Fold fold, String operator, @Nullable Object value, Fold valueFold) {
        String source = "if (" + doc(field) + ".size() == 0 || params.value == null) { return false; } "
            + "return " + docValue(field) + fold.method + ".compareTo(params.value.toString()" + valueFold.method + ") " + operator + " 0;";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("value", value);
        return script(source, params);
    }

    /**
     * A range over the lower or upper case form of a string property, such as {@code lower(hexadecimal) between ? and ?}.
     *
     * @param field The doc values field of the property
     * @param fold The case function applied to the property
     * @param from The inclusive lower bound, a value or a parameter placeholder
     * @param fromFold The case function applied to the lower bound
     * @param to The inclusive upper bound, a value or a parameter placeholder
     * @param toFold The case function applied to the upper bound
     * @param negated Whether the range is {@code NOT BETWEEN}
     * @return The script query, which does not match a document without the property or a {@code null} bound
     */
    static Map<String, Object> caseFoldedRange(String field, Fold fold,
                                               @Nullable Object from, Fold fromFold,
                                               @Nullable Object to, Fold toFold,
                                               boolean negated) {
        String inRange = "v.compareTo(params.from.toString()" + fromFold.method + ") >= 0 && v.compareTo(params.to.toString()" + toFold.method + ") <= 0";
        String source = "if (" + doc(field) + ".size() == 0 || params.from == null || params.to == null) { return false; } "
            + "String v = " + docValue(field) + fold.method + "; "
            + "return " + (negated ? "!(" + inRange + ")" : inRange) + ";";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", from);
        params.put("to", to);
        return script(source, params);
    }

    private static String doc(String field) {
        return "doc['" + field + "']";
    }

    private static String docValue(String field) {
        return doc(field) + ".value";
    }

    private static Map<String, Object> script(String source, @Nullable Map<String, Object> params) {
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("source", source);
        script.put("lang", "painless");
        if (params != null) {
            script.put("params", params);
        }
        return Map.of(SCRIPT, Map.of(SCRIPT, script));
    }
}
