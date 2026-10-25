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
 * @author Graeme Rocher
 * @since 5.4.0
 */
@Internal
final class PainlessScripts {

    private static final String SCRIPT = "script";

    private PainlessScripts() {
    }

    /**
     * Compares two properties of the same document, such as {@code numBitsRequired = floorOfSquareRoot}.
     *
     * @param left The doc values field of the left property
     * @param operator The Java comparison operator: {@code ==}, {@code !=}, {@code <}, {@code <=}, {@code >} or {@code >=}
     * @param right The doc values field of the right property
     * @param numeric Whether both properties are numbers, which compare directly rather than through {@code compareTo}
     * @return The script query
     */
    static Map<String, Object> compare(String left, String operator, String right, boolean numeric) {
        String comparison = numeric
            ? doc(left) + ".value " + operator + " " + doc(right) + ".value"
            : doc(left) + ".value.compareTo(" + doc(right) + ".value) " + operator + " 0";
        return script(doc(left) + ".size() != 0 && " + doc(right) + ".size() != 0 && " + comparison, null);
    }

    /**
     * A range over the lower or upper case form of a string property, such as {@code lower(hexadecimal) between ? and ?}.
     *
     * @param field The doc values field of the property
     * @param upper Whether the property is upper cased rather than lower cased
     * @param from The inclusive lower bound, a value or a parameter placeholder
     * @param to The inclusive upper bound, a value or a parameter placeholder
     * @return The script query
     */
    static Map<String, Object> caseFoldedRange(String field, boolean upper, @Nullable Object from, @Nullable Object to) {
        String fold = upper ? ".toUpperCase()" : ".toLowerCase()";
        String source = "if (" + doc(field) + ".size() == 0) { return false; } "
            + "String v = " + doc(field) + ".value" + fold + "; "
            + "return v.compareTo(params.from" + fold + ") >= 0 && v.compareTo(params.to" + fold + ") <= 0;";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", from);
        params.put("to", to);
        return script(source, params);
    }

    private static String doc(String field) {
        return "doc['" + field + "']";
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
