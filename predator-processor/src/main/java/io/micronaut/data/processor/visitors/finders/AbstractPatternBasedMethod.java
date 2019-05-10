package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.intercept.*;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    protected AbstractPatternBasedMethod(@Nonnull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
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
            while(matcher.find()) {
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
    protected void matchProjections(@Nonnull MethodMatchContext matchContext, List<ProjectionMethodExpression> projectionExpressions, String projectionSequence) {
        ProjectionMethodExpression currentExpression = ProjectionMethodExpression.matchProjection(
                matchContext,
                projectionSequence
        );

        if (currentExpression != null) {
            // add to list of expressions
            projectionExpressions.add(currentExpression);
        }
    }

    @Nullable
    protected MethodMatchInfo buildInfo(MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        ClassElement returnType = matchContext.getReturnType();
        ClassElement typeArgument = returnType.getFirstTypeArgument().orElse(null);
        if (!returnType.getName().equals("void")) {
            if (returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive()) {
                if (areTypesCompatible(returnType, queryResultType)) {
                    return new MethodMatchInfo(queryResultType, query, FindOneInterceptor.class);
                } else {
                    matchContext.fail("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + returnType.getName());
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

}
