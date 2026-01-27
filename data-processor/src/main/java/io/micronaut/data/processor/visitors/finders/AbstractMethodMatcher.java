/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The method matcher that is using {@link MethodNameParser}.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public abstract class AbstractMethodMatcher implements MethodMatcher {

    protected static final String[] ALL = {"All"};
    protected static final String[] ALL_OR_ONE = {"All", "One"};
    protected static final String[] TOP_OR_FIRST = {"Top", "First"};
    protected static final String FIRST = "First";
    protected static final String[] ORDER_VARIATIONS = {"OrderBy", "SortBy"};
    protected static final String BY = "By";
    protected static final String DISTINCT = "Distinct";
    protected static final String FOR_UPDATE = "ForUpdate";
    protected static final String RETURNING = "Returning";

    private final MethodNameParser parser;

    public AbstractMethodMatcher(MethodNameParser parser) {
        this.parser = parser;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        String methodName = matchContext.getMethodElement().getName();
        String parseInput = methodName;
        if (isStrictSnakeCase(methodName)) {
            parseInput = normalizeSnakeCase(methodName);
        }
        List<MethodNameParser.Match> matches = parser.tryMatch(parseInput);
        if (matches.isEmpty()) {
            return null;
        }
        return match(matchContext, matches);
    }

    private static boolean isStrictSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        boolean prevUnderscore = false;
        boolean seenUnderscore = false;
        boolean seenLetter = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                if (!seenLetter || prevUnderscore) {
                    return false;
                }
                prevUnderscore = true;
                seenUnderscore = true;
                continue;
            }
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) {
                return false;
            }
            seenLetter = true;
            prevUnderscore = false;
        }
        // require at least one underscore (snake_case) and not end with underscore
        return seenUnderscore && !prevUnderscore;
    }

    /**
     * Convert snake_case repository method names to camelCase.
     * Only applies when underscores are present. The first token is lower-cased
     * and subsequent tokens are capitalized.
     *
     * Examples:
     *  - find_by_title -> findByTitle
     *  - count_distinct_by_name -> countDistinctByName
     *  - find_first_10_by_name -> findFirst10ByName
     */
    @Internal
    private static String normalizeSnakeCase(String name) {
        if (name == null || name.indexOf('_') < 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        String[] parts = name.split("_+");
        int outIndex = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (outIndex == 0) {
                sb.append(part.toLowerCase());
            } else {
                char first = part.charAt(0);
                sb.append(Character.toUpperCase(first));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
            outIndex++;
        }
        return sb.toString();
    }

    /**
     * Matched the method.
     *
     * @param matchContext The match context
     * @param matches      The matches
     * @return The method match
     */
    @Nullable
    protected abstract MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches);

}
