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
import io.micronaut.data.processor.visitors.MethodMatchContext;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The matcher based on a simple patter.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public abstract class AbstractPrefixPatternMethodMatcher implements MethodMatcher {

    private static final Comparator<String> PREFIX_COMPARATOR = Comparator.comparingInt(String::length).thenComparing(String::compareTo).reversed();

    protected final Pattern pattern;

    protected AbstractPrefixPatternMethodMatcher(@NonNull List<String> prefixes) {
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("At least one prefix required");
        }
        this.pattern = computePattern(prefixes);
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        String methodName = matchContext.getMethodElement().getName();
        Matcher matcher = pattern.matcher(methodName);
        if (matcher.find()) {
            return match(matchContext, matcher);
        }
        return null;
    }

    /**
     * Handle the match.
     *
     * @param matchContext The match context
     * @param matcher The matcher
     * @return The method matcher
     */
    protected abstract MethodMatch match(MethodMatchContext matchContext, Matcher matcher);

    private static Pattern computePattern(List<String> prefixes) {
        String prefixPattern = prefixes.stream()
                .sorted(PREFIX_COMPARATOR)
                .collect(Collectors.joining("|"));
        return Pattern.compile("^((" + prefixPattern + ")(\\S*?))$");
    }

}
