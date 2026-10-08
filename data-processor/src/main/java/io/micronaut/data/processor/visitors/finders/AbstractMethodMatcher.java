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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern PYTHON_QUERY_NAME = Pattern.compile("^([a-z]+)(_all)?(?:_by_(.+))?$");
    private static final Pattern PYTHON_QUERY_PREFIX = Pattern.compile("^([a-z][^A-Z_]*)_");

    private final MethodNameParser parser;

    public AbstractMethodMatcher(MethodNameParser parser) {
        this.parser = parser;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        String methodName = matchContext.getMethodElement().getName();
        if (matchContext.getVisitorContext().getLanguage() == VisitorContext.Language.PYTHON) {
            Matcher prefix = PYTHON_QUERY_PREFIX.matcher(methodName);
            if (prefix.find()) {
                // Only validate names belonging to this matcher; explicit operation annotations may use arbitrary names.
                if (parser.tryMatch(prefix.group(1)).isEmpty()) {
                    return null;
                }
                methodName = pythonQueryName(methodName, matchContext);
            }
        }
        List<MethodNameParser.Match> matches = parser.tryMatch(methodName);
        if (matches.isEmpty()) {
            return null;
        }
        return match(matchContext, matches);
    }

    private static String pythonQueryName(String methodName, MethodMatchContext matchContext) {
        Matcher matcher = PYTHON_QUERY_NAME.matcher(methodName);
        if (!matcher.matches()) {
            throw unsupportedPythonQuery(methodName);
        }
        String prefix = matcher.group(1) + (matcher.group(2) == null ? "" : ALL[0]);
        String predicate = matcher.group(3);
        if (predicate == null) {
            return prefix;
        }
        // A literal property or association path takes precedence over the InList operator.
        if (!isPropertyPath(matchContext, predicate)) {
            if (!predicate.endsWith("_in_list")) {
                throw unsupportedPythonQuery(methodName);
            }
            String property = predicate.substring(0, predicate.length() - "_in_list".length());
            if (!isPropertyPath(matchContext, property)) {
                throw unsupportedPythonQuery(methodName);
            }
            predicate = property + "InList";
        }
        return prefix + BY + NameUtils.capitalize(predicate);
    }

    private static MatchFailedException unsupportedPythonQuery(String methodName) {
        return new MatchFailedException("Unsupported Python snake_case derived query '" + methodName
            + "'. Supported grammar is <prefix>[_all][_by_<property>[_in_list]] with an existing property or association path; "
            + "use the camelCase form for other operators.");
    }

    private static boolean isPropertyPath(MethodMatchContext matchContext, String predicate) {
        if (!matchContext.hasRootEntity()) {
            return false;
        }
        try {
            return PersistentEntityUtils.getPersistentPropertyPath(matchContext.getRootEntity(), predicate).isPresent();
        } catch (IllegalArgumentException _) {
            // An unresolved association path may still end in the InList operator.
            return false;
        }
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
