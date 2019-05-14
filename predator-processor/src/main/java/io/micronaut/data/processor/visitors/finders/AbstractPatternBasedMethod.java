/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.intercept.*;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A method candidate based on pattern matching.
 *
 * @author graemerocher
 * @since 1.1.0
 */
public abstract class AbstractPatternBasedMethod implements MethodCandidate {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy([\\w\\d]+)");
    protected final Pattern pattern;

    /**
     * Default constructor.
     * @param pattern The pattern to match
     */
    protected AbstractPatternBasedMethod(@NonNull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return pattern.matcher(methodElement.getName()).find();
    }

    /**
     * Matches order by definitions in the query sequence.
     * @param querySequence The query sequence
     * @param orders A list or orders to populate
     * @return The new query sequence minus any order by definitions
     */
    protected String matchOrder(String querySequence, List<Sort.Order> orders) {
        if (ORDER_BY_PATTERN.matcher(querySequence).matches()) {

            Matcher matcher = ORDER_BY_PATTERN.matcher(querySequence);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, "$1");
                String orderDef = matcher.group(2);
                if (StringUtils.isNotEmpty(orderDef)) {
                    String prop = NameUtils.decapitalize(orderDef);
                    if (prop.endsWith("Desc")) {
                        orders.add(Sort.Order.desc(prop.substring(0, prop.length() - 4)));
                    } else if (prop.equalsIgnoreCase("Asc")) {
                        orders.add(Sort.Order.asc(prop.substring(0, prop.length() - 3)));
                    } else {
                        orders.add(Sort.Order.asc(prop));
                    }
                }
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        return querySequence;
    }

    /**
     * Matches projections.
     * @param matchContext The match context
     * @param projectionExpressions the projection expressions
     * @param projectionSequence The sequence
     */
    protected void matchProjections(@NonNull MethodMatchContext matchContext, List<ProjectionMethodExpression> projectionExpressions, String projectionSequence) {
        ProjectionMethodExpression currentExpression = ProjectionMethodExpression.matchProjection(
                matchContext,
                projectionSequence
        );

        if (currentExpression != null) {
            // add to list of expressions
            projectionExpressions.add(currentExpression);
        }
    }

    /**
     * Build the {@link MethodMatchInfo}.
     * @param matchContext The match context
     * @param queryResultType The query result type
     * @param query The query
     * @return The info or null if it can't be built
     */
    @Nullable
    protected MethodMatchInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable Query query) {
        ClassElement returnType = matchContext.getReturnType();
        ClassElement typeArgument = returnType.getFirstTypeArgument().orElse(null);
        if (!returnType.getName().equals("void")) {
            if (returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive()) {
                if (areTypesCompatible(returnType, queryResultType)) {
                    if (query != null && queryResultType.getName().equals(matchContext.getRootEntity().getName())) {
                        List<Query.Criterion> criterionList = query.getCriteria().getCriteria();
                        if (criterionList.size() == 1 && criterionList.get(0) instanceof Query.IdEquals) {
                            return new MethodMatchInfo(
                                    matchContext.getReturnType(),
                                    query,
                                    FindByIdInterceptor.class
                            );
                        } else {
                            return new MethodMatchInfo(queryResultType, query, FindOneInterceptor.class);
                        }
                    } else {
                        return new MethodMatchInfo(queryResultType, query, FindOneInterceptor.class);
                    }
                } else {
                    matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
                    return null;
                }
            } else if (typeArgument != null) {
                if (!areTypesCompatible(typeArgument, queryResultType)) {
                    matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
                    return null;
                }

                if (returnType.isAssignable(Iterable.class)) {
                    return new MethodMatchInfo(typeArgument, query, FindAllInterceptor.class);
                } else if (returnType.isAssignable(Stream.class)) {
                    return new MethodMatchInfo(typeArgument, query, FindStreamInterceptor.class);
                } else if (returnType.isAssignable(Optional.class)) {
                    return new MethodMatchInfo(typeArgument, query, FindOptionalInterceptor.class);
                } else if (returnType.isAssignable(Publisher.class)) {
                    return new MethodMatchInfo(typeArgument, query, FindReactivePublisherInterceptor.class);
                }
            }
        }

        matchContext.fail("Unsupported Repository method return type");
        return null;
    }

    private boolean areTypesCompatible(ClassElement returnType, ClassElement queryResultType) {
        if (returnType.isAssignable(queryResultType.getName())) {
            return true;
        } else {
            if (TypeUtils.isNumber(returnType) && TypeUtils.isNumber(queryResultType)) {
                return true;
            } else if (TypeUtils.isBoolean(returnType) && TypeUtils.isBoolean(queryResultType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Apply ordering.
     * @param context The context
     * @param query The query
     * @param orderList The list mutate
     * @return If ordering was applied or if an error occurred false
     */
    protected boolean applyOrderBy(@NonNull MethodMatchContext context, @NonNull Query query, @NonNull List<Sort.Order> orderList) {
        if (CollectionUtils.isNotEmpty(orderList)) {
            SourcePersistentEntity entity = context.getRootEntity();
            for (Sort.Order order : orderList) {
                String prop = order.getProperty();
                if (!entity.getPath(prop).isPresent()) {
                    context.fail("Cannot order by non-existent property: " + prop);
                    return true;
                }
            }
            query.sort(Sort.of(orderList));
        }
        return false;
    }
}
