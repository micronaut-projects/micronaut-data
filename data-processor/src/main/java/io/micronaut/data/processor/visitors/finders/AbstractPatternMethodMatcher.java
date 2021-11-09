/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.processor.visitors.MethodMatchContext;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract pattern method match that support two variations of method names.
 *
 * - With `by` syntax - projection followed by predicates
 * - Without `by` syntax - predicates only
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public abstract class AbstractPatternMethodMatcher implements MethodMatcher {

    protected final Pattern patternWithBySyntax;
    protected final Pattern patternWithoutBySyntax;

    /**
     * Default constructor.
     *
     * @param supportsProjections true of matcher supports projections
     * @param prefixes            The prefixes
     */
    protected AbstractPatternMethodMatcher(boolean supportsProjections, @NonNull String... prefixes) {
        if (ArrayUtils.isEmpty(prefixes)) {
            throw new IllegalArgumentException("At least one prefix required");
        }
        Arrays.sort(prefixes, Comparator.comparingInt(String::length).thenComparing(String::compareTo).reversed());
        this.patternWithBySyntax = compileWithProjectionSyntax(supportsProjections, prefixes);
        this.patternWithoutBySyntax = computeWithoutProjectionSyntax(prefixes);
    }

    @Override
    public final MethodMatch match(MethodMatchContext matchContext) {
        String methodName = matchContext.getMethodElement().getName();
        java.util.regex.Matcher matcher = patternWithBySyntax.matcher(methodName);
        if (matcher.find()) {
            return match(matchContext, matcher);
        }
        matcher = patternWithoutBySyntax.matcher(methodName);
        if (matcher.find()) {
            return match(matchContext, matcher);
        }
        return null;
    }

    /**
     * Handle match.
     *
     * @param matchContext The match context
     * @param matcher      The matcher
     * @return The method match
     */
    protected abstract MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher);

    private static Pattern computeWithoutProjectionSyntax(String[] prefixes) {
        String prefixPattern = String.join("|", prefixes);
        return Pattern.compile("^((" + prefixPattern + ")(\\S*?))$");
    }

    private static Pattern compileWithProjectionSyntax(boolean supportsProjections, String[] prefixes) {
        String patternStr;
        if (supportsProjections) {
            String prefixPattern = String.join("|", prefixes);
            patternStr = "((" + prefixPattern + ")([\\w\\d]*?)By)([A-Z]\\w*)";
        } else {
            String prefixPattern = Arrays.stream(prefixes).map(p -> p + "By").collect(Collectors.joining("|"));
            patternStr = "((" + prefixPattern + "))([A-Z]\\w*)";
        }
        return Pattern.compile(patternStr);
    }

}
