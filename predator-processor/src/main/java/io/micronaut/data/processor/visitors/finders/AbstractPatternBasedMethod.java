package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.Sort;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractPatternBasedMethod implements PredatorMethodCandidate {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy([\\w\\d]+)");
    protected final Pattern pattern;

    public AbstractPatternBasedMethod(@Nonnull Pattern pattern) {
        this.pattern = pattern;
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

    /**
     * Build the method info
     *
     * @param matchContext    The method match context
     * @param queryResultType The query result type
     * @param query           The query
     * @return The method info
     */
    protected abstract @Nullable PredatorMethodInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable Query query
    );

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return pattern.matcher(methodElement.getName()).find();
    }

}
