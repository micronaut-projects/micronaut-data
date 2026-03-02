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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
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
        if (isSnakeCase(methodName)) {
            parseInput = NameUtils.camelCase(methodName);
        }
        List<MethodNameParser.Match> matches = parser.tryMatch(parseInput);
        if (matches.isEmpty()) {
            return null;
        }
        return match(matchContext, matches);
    }

    private static boolean isSnakeCase(String s) {
        if (StringUtils.isEmpty(s)) {
            return false;
        }
        int n = s.length();
        char first = s.charAt(0);
        if (first < 'a' || first > 'z') {
            return false;
        }
        boolean seenUnderscore = false;
        boolean prevUnderscore = false;
        for (int i = 1; i < n; i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore) {
                    return false;
                }
                seenUnderscore = true;
                prevUnderscore = true;
            } else if (!isAsciiLowerAlnum(c)) {
                return false;
            } else {
                prevUnderscore = false;
            }
        }
        return seenUnderscore && !prevUnderscore;
    }

    private static boolean isAsciiLowerAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
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
