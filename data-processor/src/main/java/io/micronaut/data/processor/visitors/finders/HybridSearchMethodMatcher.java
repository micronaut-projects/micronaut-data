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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.search.HybridSearchMethod;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches the derived hybrid finder {@code hybridSearchBy[Property]SearchAnd<Vector>Near(String, Vector[, Pageable])}
 * returning {@link SearchResults}. No store runs a text match and a nearest-neighbour ranking in one query,
 * so the method is executed by the {@code search(SearchQuery)} interceptors, which fuse the two rankings;
 * the processor records the search fields and the vector property on the method.
 *
 * <p>The {@code hybridSearch} prefix sets the method apart from {@code searchBy[Property]SearchAnd<Vector>Near},
 * which stays a vector search filtered by a text match whatever its shape. Every abstract method whose name starts
 * with {@code hybridSearch} is claimed, and one that does not have the exact hybrid shape fails the compilation
 * instead of falling back to another meaning.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class HybridSearchMethodMatcher implements MethodMatcher {

    /**
     * The prefix of a derived hybrid finder.
     */
    public static final String PREFIX = "hybridSearch";

    private static final Pattern NAME = Pattern.compile("^hybridSearchBy(\\w*?)SearchAnd(\\w+)Near$");
    private static final String FORM = "A hybrid search method has the form "
        + "SearchResults<E> hybridSearchBy[Property]SearchAnd<Vector>Near(String query, Vector vector[, Pageable pageable])";
    private static final String SEARCH_QUERY_INTERCEPTOR = "io.micronaut.data.runtime.intercept.criteria.SearchQueryInterceptor";
    private static final String SEARCH_QUERY_REACTIVE_INTERCEPTOR = "io.micronaut.data.runtime.intercept.criteria.reactive.SearchQueryReactiveInterceptor";
    private static final String SEARCH_QUERY_ASYNC_INTERCEPTOR = "io.micronaut.data.runtime.intercept.criteria.async.SearchQueryAsyncInterceptor";

    @Override
    public int getOrder() {
        // before the search repository and criteria matchers
        return DEFAULT_POSITION - 300;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        MethodElement method = matchContext.getMethodElement();
        if (!method.getName().startsWith(PREFIX)) {
            return null;
        }
        Matcher matcher = NAME.matcher(method.getName());
        if (!matcher.matches()) {
            throw new ProcessingException(method, FORM + "; a limit such as Top5 or another predicate is not supported, pass a Pageable to limit the results.");
        }
        if (!matchContext.hasRootEntity()) {
            throw new ProcessingException(method, "Repository does not have a well-defined primary entity type");
        }
        ClassElement resultType = producedResultType(matchContext);
        if (resultType == null || !resultType.getName().equals(SearchResults.class.getName())) {
            throw new ProcessingException(method, FORM + "; it must return SearchResults, or a reactive, future or suspend result of SearchResults.");
        }
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        if (parameters.size() != 2 || !parameters.get(0).getType().isAssignable(String.class) || !parameters.get(1).getType().isAssignable(Vector.class)) {
            throw new ProcessingException(method, FORM + "; it takes the search string and the query vector, and optionally a Pageable.");
        }
        SourcePersistentEntity entity = matchContext.getRootEntity();
        String vectorProperty = vectorProperty(method, entity, NameUtils.decapitalize(matcher.group(2)));
        String[] fields = searchFields(method, entity, matcher.group(1));
        String queryParameter = parameters.get(0).getName();
        method.annotate(HybridSearchMethod.class, builder -> builder.member("fields", fields).member("vectorProperty", vectorProperty).member("queryParameter", queryParameter));
        String interceptor = searchQueryInterceptor(matchContext);
        return mc -> new MethodMatchInfo(
            DataMethod.OperationType.QUERY,
            resultType,
            FindersUtils.getInterceptorElement(matchContext, interceptor)
        );
    }

    private static String vectorProperty(MethodElement method, SourcePersistentEntity entity, String name) {
        SourcePersistentProperty vector = entity.getPropertyByName(name);
        if (vector == null || !vector.getType().isAssignable(Vector.class)) {
            throw new ProcessingException(method, FORM + "; " + name + " is not a Vector property of " + entity.getSimpleName()
                + ", and a hybrid search method cannot have other predicates.");
        }
        return name;
    }

    private static String[] searchFields(MethodElement method, SourcePersistentEntity entity, String textProperty) {
        if (textProperty.isEmpty()) {
            return new String[0];
        }
        String property = NameUtils.decapitalize(textProperty);
        SourcePersistentProperty field = entity.getPropertyByName(property);
        if (field == null || !field.hasAnnotation(SearchField.class)) {
            throw new ProcessingException(method, FORM + "; " + property + " is not a @SearchField property of " + entity.getSimpleName() + ".");
        }
        return new String[]{property};
    }

    @Nullable
    private static ClassElement producedResultType(MethodMatchContext matchContext) {
        ClassElement returnType = matchContext.getReturnType();
        if (matchContext.getMethodElement().isSuspend()) {
            return TypeUtils.getKotlinCoroutineProducedType(matchContext.getMethodElement());
        }
        if (TypeUtils.isReactiveOrFuture(returnType)) {
            return returnType.getFirstTypeArgument().orElse(returnType);
        }
        return returnType;
    }

    private static String searchQueryInterceptor(MethodMatchContext matchContext) {
        if (FindersUtils.isFutureType(matchContext.getMethodElement(), matchContext.getReturnType())) {
            return SEARCH_QUERY_ASYNC_INTERCEPTOR;
        }
        if (FindersUtils.isReactiveType(matchContext.getReturnType())) {
            return SEARCH_QUERY_REACTIVE_INTERCEPTOR;
        }
        return SEARCH_QUERY_INTERCEPTOR;
    }
}
