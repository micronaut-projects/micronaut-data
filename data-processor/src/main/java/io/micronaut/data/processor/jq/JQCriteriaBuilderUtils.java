/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.processor.jq;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.jq.JQBaseListener;
import io.micronaut.data.jq.JQParser;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCommonAbstractCriteria;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The utils to generate Criteria queries from Jakarta Query statements.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class JQCriteriaBuilderUtils {

    private JQCriteriaBuilderUtils() {
    }

    public static PersistentEntityCommonAbstractCriteria build(String query,
                                                               @Nullable PersistentEntity rootPersistentEntity,
                                                               MethodElement methodElement,
                                                               Function<String, ClassElement> classElementResolver,
                                                               SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        ParseTree child = parse(query, methodElement);
        if (child instanceof JQParser.Delete_statementContext deleteStatementContext) {
            return JQCriteriaBuilderUtils.buildDelete(deleteStatementContext, classElementResolver, criteriaBuilder);
        }
        if (child instanceof JQParser.Update_statementContext updateStatementContext) {
            return JQCriteriaBuilderUtils.buildUpdate(updateStatementContext, classElementResolver, criteriaBuilder);
        }
        if (child instanceof JQParser.Select_statementContext select_clauseContext) {
            return JQCriteriaBuilderUtils.buildSelect(rootPersistentEntity, select_clauseContext, classElementResolver, criteriaBuilder, methodElement, query);
        }

        throw new MatchFailedException("Unrecognized query: " + child.getParent(), methodElement);
    }

    public static PersistentEntityCriteriaQuery<?> buildCount(String query,
                                                                    PersistentEntity rootPersistentEntity,
                                                                    MethodElement methodElement,
                                                                    Function<String, ClassElement> classElementResolver,
                                                                    SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        ParseTree child = parse(query, methodElement);
        if (child instanceof JQParser.Select_statementContext select_clauseContext) {
            return JQCriteriaBuilderUtils.buildCount(rootPersistentEntity, select_clauseContext, classElementResolver, criteriaBuilder);
        }

        throw new MatchFailedException("Unrecognized count query: " + child.getParent(), methodElement);
    }

    private static ParseTree parse(String query, Element originatingElement) {
        var inputStream = CharStreams.fromString(query);
        var lexer = new io.micronaut.data.jq.JQLexer(inputStream);
        ANTLRErrorListener errorListener = new ANTLRErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new MatchFailedException("Failed to parse Jakarta Query: " + prettifyAntlrError(offendingSymbol, line, charPositionInLine, msg, e, query), originatingElement);
            }

            @Override
            public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {
            }

            @Override
            public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {
            }

            @Override
            public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {
            }
        };
        var tokenStream = new CommonTokenStream(lexer);
        var parser = new JQParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);
        JQParser.StatementContext statement = parser.statement();
        return statement.getChild(0);
    }

    private static String prettifyAntlrError(Object offendingSymbol,
                                             int line,
                                             int charPositionInLine,
                                             String message,
                                             RecognitionException e,
                                             String query) {
        String errorText = "At " + line + ":" + charPositionInLine;
        if (offendingSymbol instanceof CommonToken commonToken) {
            String token = commonToken.getText();
            if (token != null && !token.isEmpty()) {
                errorText += " and token '" + token + "'";
            }
        }
        errorText += ", ";
        if (e instanceof NoViableAltException) {
            errorText += message.substring(0, message.indexOf('\''));
            if (query.isEmpty()) {
                errorText += "'*' (empty query string)";
            } else {
                String lineText = query.lines().toList().get(line - 1);
                String text = lineText.substring(0, charPositionInLine) + "*" + lineText.substring(charPositionInLine);
                errorText += "'" + text + "'";
            }
        } else if (e instanceof InputMismatchException) {
            errorText += message.substring(0, message.length() - 1)
                .replace(" expecting {", ", expecting one of the following tokens: ");
        } else {
            errorText += message;
        }
        return errorText;
    }

    public static PersistentEntityCriteriaQuery<?> buildSelect(@Nullable PersistentEntity rootPersistentEntity,
                                                               JQParser.Select_statementContext selectStatementContext,
                                                               Function<String, ClassElement> classElementResolver,
                                                               SourcePersistentEntityCriteriaBuilder criteriaBuilder,
                                                               @Nullable MethodElement methodElement,
                                                               String q) {

        SourcePersistentEntityCriteriaQuery<Object> query = criteriaBuilder
            .createQuery(null);
        PersistentEntityRoot<Object> root;
        JQParser.From_clauseContext fromClauseContext = selectStatementContext.from_clause();
        if (fromClauseContext != null) {
            String entityName = fromClauseContext.entity_name().getText();
            root = query.from(classElementResolver.apply(entityName));
        } else {
            Objects.requireNonNull(rootPersistentEntity, "Root persistent entity is not specified in the Jakarta Query: " + q);
            root = query.from(rootPersistentEntity);
        }
        RootContext rootContext = new RootContext(root, getIdentificationVariable(fromClauseContext));
        Predicate predicate = getPredicate(selectStatementContext.where_clause(), rootContext, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.orderBy(
            getOrders(selectStatementContext.orderby_clause(), rootContext, criteriaBuilder, methodElement)
        );
        JQParser.Select_clauseContext selectClauseContext = selectStatementContext.select_clause();
        if (selectClauseContext != null) {
            if (selectClauseContext.DISTINCT() != null) {
                query.distinct(true);
            }
            List<JQParser.Select_itemContext> selectItems = selectClauseContext.select_item();
            if (selectItems.size() > 1) {
                query.multiselect(
                    selectItems
                        .stream()
                        .map(selectItem -> {
                            Selection<?> selection = getSelection(selectItem, true, rootContext, criteriaBuilder);
                            annotateProjection(methodElement, selection);
                            return selection;
                        })
                        .collect(Collectors.toUnmodifiableList())
                );
            }
            if (selectItems.size() == 1) {
                query.select(getSelection(selectItems.get(0), false, rootContext, criteriaBuilder));
            }
        }
        return query;
    }

    public static PersistentEntityCriteriaQuery<?> buildCount(PersistentEntity rootPersistentEntity,
                                                              JQParser.Select_statementContext selectStatementContext,
                                                              Function<String, ClassElement> classElementResolver,
                                                              SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        SourcePersistentEntityCriteriaQuery<Object> query = criteriaBuilder
            .createQuery(null);
        PersistentEntityRoot<Object> root;
        JQParser.From_clauseContext fromClauseContext = selectStatementContext.from_clause();
        if (fromClauseContext != null) {
            String entityName = fromClauseContext.entity_name().getText();
            root = query.from(classElementResolver.apply(entityName));
        } else {
            root = query.from(rootPersistentEntity);
        }
        RootContext rootContext = new RootContext(root, getIdentificationVariable(fromClauseContext));
        Predicate predicate = getPredicate(selectStatementContext.where_clause(), rootContext, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.select(criteriaBuilder.count(root));
        return query;
    }

    public static PersistentEntityCriteriaUpdate<?> buildUpdate(JQParser.Update_statementContext updateStatementContext,
                                                                Function<String, ClassElement> classElementResolver,
                                                                SourcePersistentEntityCriteriaBuilder criteriaBuilder) {
        String entityName = updateStatementContext.update_clause().entity_name().getText();

        JQParser.Where_clauseContext whereClauseContext = updateStatementContext.where_clause();

        SourcePersistentEntityCriteriaUpdate<Object> updateQuery = criteriaBuilder
            .createCriteriaUpdate(null);
        PersistentEntityRoot<Object> root = updateQuery.from(classElementResolver.apply(entityName));
        RootContext rootContext = new RootContext(root, getIdentificationVariable(updateStatementContext.update_clause()));
        Predicate predicate = getPredicate(whereClauseContext, rootContext, criteriaBuilder);
        if (predicate != null) {
            updateQuery.where(predicate);
        }

        ParseTreeWalker.DEFAULT.walk(new JQBaseListener() {

            @Override
            public void exitUpdate_item(JQParser.Update_itemContext ctx) {
                String name = rootContext.stripIdentificationVariable(ctx.simple_path_expression().getText());
                JQParser.New_valueContext newValue = ctx.new_value();
                if (newValue.NULL() != null) {
                    updateQuery.set(name, null);
                } else {
                    updateQuery.set(name, getExpression(newValue.scalar_expression(), rootContext, criteriaBuilder));
                }
            }

        }, updateStatementContext);

        return updateQuery;
    }

    public static PersistentEntityCriteriaDelete<?> buildDelete(JQParser.Delete_statementContext deleteStatementContext,
                                                                Function<String, ClassElement> classElementResolver,
                                                                SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        String entityName = deleteStatementContext.delete_clause().entity_name().getText();
        JQParser.Where_clauseContext whereClauseContext = deleteStatementContext.where_clause();

        SourcePersistentEntityCriteriaDelete<Object> deleteQuery = criteriaBuilder
            .createCriteriaDelete(null);
        PersistentEntityRoot<Object> root = deleteQuery.from(classElementResolver.apply(entityName));
        RootContext rootContext = new RootContext(root, getIdentificationVariable(deleteStatementContext.delete_clause()));
        Predicate predicate = getPredicate(whereClauseContext, rootContext, criteriaBuilder);
        if (predicate != null) {
            deleteQuery.where(predicate);
        }
        return deleteQuery;
    }

    @Nullable
    private static String getIdentificationVariable(JQParser.@Nullable From_clauseContext fromClause) {
        if (fromClause == null || fromClause.identification_variable() == null) {
            return null;
        }
        return fromClause.identification_variable().getText();
    }

    @Nullable
    private static String getIdentificationVariable(JQParser.Update_clauseContext updateClause) {
        if (updateClause.identification_variable() == null) {
            return null;
        }
        return updateClause.identification_variable().getText();
    }

    @Nullable
    private static String getIdentificationVariable(JQParser.Delete_clauseContext deleteClause) {
        if (deleteClause.identification_variable() == null) {
            return null;
        }
        return deleteClause.identification_variable().getText();
    }

    private static Selection<?> getSelection(JQParser.Select_itemContext selectItem,
                                             boolean autoAliasSimplePath,
                                             RootContext rootContext,
                                             PersistentEntityCriteriaBuilder criteriaBuilder) {
        Expression<?> expression = getExpression(selectItem.select_expression(), rootContext, criteriaBuilder);
        JQParser.Result_variableContext resultVariable = selectItem.result_variable();
        if (resultVariable != null) {
            return expression.alias(resultVariable.getText());
        }
        Optional<JQParser.Simple_path_expressionContext> simplePathExpression = getSimplePathExpression(selectItem.select_expression());
        if (autoAliasSimplePath && simplePathExpression.isPresent()) {
            return expression.alias(rootContext.stripIdentificationVariable(simplePathExpression.get().getText()));
        }
        return expression;
    }

    private static void annotateProjection(@Nullable MethodElement methodElement, Selection<?> selection) {
        if (methodElement == null) {
            return;
        }
        if (selection instanceof Path<?> path) {
            methodElement.annotate(Projection.class, b -> b.value(((PersistentPropertyPath<Object>) path).getProperty().getName()));
        } else if (selection instanceof AliasedSelection<?> aliasedSelection && aliasedSelection.getSelection() instanceof Path<?> path) {
            methodElement.annotate(Projection.class, b -> b.value(((PersistentPropertyPath<Object>) path).getProperty().getName()));
        }
    }

    private static Optional<JQParser.Simple_path_expressionContext> getSimplePathExpression(JQParser.Select_expressionContext selectExpression) {
        if (selectExpression.scalar_expression() == null) {
            return Optional.empty();
        }
        return getSimplePathExpression(selectExpression.scalar_expression());
    }

    private static Optional<JQParser.Simple_path_expressionContext> getSimplePathExpression(JQParser.Scalar_expressionContext scalarExpression) {
        JQParser.Primary_expressionContext primaryExpression = scalarExpression.primary_expression();
        if (primaryExpression != null) {
            return Optional.ofNullable(primaryExpression.simple_path_expression());
        }
        return Optional.empty();
    }

    @Nullable
    private static Predicate getPredicate(JQParser. @Nullable Where_clauseContext whereClause,
                                          RootContext rootContext,
                                          PersistentEntityCriteriaBuilder criteriaBuilder) {
        if (whereClause == null) {
            return null;
        }
        JQParser.Conditional_expressionContext conditionalExpression = whereClause.conditional_expression();
        return getPredicate(conditionalExpression, rootContext, criteriaBuilder);
    }

    private static List<Order> getOrders(JQParser. @Nullable Orderby_clauseContext orderByClause,
                                         RootContext rootContext,
                                         PersistentEntityCriteriaBuilder criteriaBuilder,
                                         @Nullable
                                         MethodElement methodElement) {
        List<Order> orders = new ArrayList<>();
        if (orderByClause != null) {
            List<JQParser.Orderby_itemContext> orderbyItemContexts = orderByClause.orderby_item();
            for (JQParser.Orderby_itemContext orderbyItemContext : orderbyItemContexts) {
                Expression<?> expression = getExpression(orderbyItemContext.orderby_expression(), rootContext);
                Nulls nullPrecedence = getNullPrecedence(orderbyItemContext);
                if (nullPrecedence == Nulls.NONE) {
                    orders.add(
                        orderbyItemContext.DESC() == null ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(expression)
                    );
                } else {
                    orders.add(
                        orderbyItemContext.DESC() == null ? criteriaBuilder.asc(expression, nullPrecedence) : criteriaBuilder.desc(expression, nullPrecedence)
                    );
                }
            }
        }
        if (methodElement != null) {
            for (AnnotationValue<?> av : methodElement.getAnnotationValuesByStereotype(OrderBy.class.getName())) {
                orders.add(criteriaBuilder.sort(
                    rootContext.root().get(av.stringValue().orElseThrow()),
                    !av.booleanValue("descending").orElse(false),
                    av.booleanValue("ignoreCase").orElse(false)
                ));
            }
        }
        return orders;
    }

    private static Nulls getNullPrecedence(JQParser.Orderby_itemContext orderbyItemContext) {
        if (orderbyItemContext.NULLS() == null) {
            return Nulls.NONE;
        }
        if (orderbyItemContext.FIRST() != null) {
            return Nulls.FIRST;
        }
        if (orderbyItemContext.LAST() != null) {
            return Nulls.LAST;
        }
        throw new IllegalStateException("Unsupported null precedence: " + orderbyItemContext.getText());
    }

    private static Expression<?> getExpression(JQParser.Orderby_expressionContext orderbyExpression,
                                               RootContext rootContext) {
        if (orderbyExpression.id_expression() != null) {
            return getExpression(orderbyExpression.id_expression(), rootContext);
        }
        return (Expression<?>) getExpression(orderbyExpression.simple_path_expression(), false, rootContext);
    }

    private static Predicate getPredicate(JQParser.Conditional_expressionContext conditionalExpression,
                                          RootContext rootContext,
                                          PersistentEntityCriteriaBuilder criteriaBuilder) {
        if (conditionalExpression.LPAREN() != null) {
            return getPredicate(conditionalExpression.conditional_expression(0), rootContext, criteriaBuilder);
        }
        if (conditionalExpression.AND() != null) {
            return criteriaBuilder.and(
                getPredicate(conditionalExpression.conditional_expression(0), rootContext, criteriaBuilder),
                getPredicate(conditionalExpression.conditional_expression(1), rootContext, criteriaBuilder)
            );
        }
        if (conditionalExpression.OR() != null) {
            return criteriaBuilder.or(
                getPredicate(conditionalExpression.conditional_expression(0), rootContext, criteriaBuilder),
                getPredicate(conditionalExpression.conditional_expression(1), rootContext, criteriaBuilder)
            );
        }
        if (conditionalExpression.NOT() != null) {
            return criteriaBuilder.not(
                getPredicate(conditionalExpression.conditional_expression(0), rootContext, criteriaBuilder)
            );
        }
        JQParser.Comparison_expressionContext comparisonExpression = conditionalExpression.comparison_expression();
        if (comparisonExpression != null) {
            Expression<?> firstExp = getExpression(
                comparisonExpression.scalar_expression(0),
                rootContext,
                criteriaBuilder
            );
            Expression<?> secondExp = getExpression(
                comparisonExpression.scalar_expression(1),
                rootContext,
                criteriaBuilder
            );
            JQParser.Comparison_operatorContext comparisonOperator = comparisonExpression.comparison_operator();
            if (comparisonOperator.EQ() != null) {
                return criteriaBuilder.equal(firstExp, secondExp);
            }
            if (comparisonOperator.NEQ() != null) {
                return criteriaBuilder.notEqual(firstExp, secondExp);
            }
            if (comparisonOperator.GT() != null) {
                return criteriaBuilder.greaterThan((Expression) firstExp, (Expression) secondExp);
            }
            if (comparisonOperator.GTEQ() != null) {
                return criteriaBuilder.greaterThanOrEqualTo((Expression) firstExp, (Expression) secondExp);
            }
            if (comparisonOperator.LT() != null) {
                return criteriaBuilder.lessThan((Expression) firstExp, (Expression) secondExp);
            }
            if (comparisonOperator.LTEQ() != null) {
                return criteriaBuilder.lessThanOrEqualTo((Expression) firstExp, (Expression) secondExp);
            }
            throw new IllegalStateException("Unsupported comparison operator: " + comparisonOperator);
        }
        JQParser.Like_expressionContext likeExpression = conditionalExpression.like_expression();
        if (likeExpression != null) {
            JQParser.Escaped_patternContext escapedPattern = likeExpression.escaped_pattern();
            Expression<String> pattern = getPattern(escapedPattern, criteriaBuilder);
            Expression<Character> escapeCharacter = getEscapeCharacter(escapedPattern, criteriaBuilder);
            Expression<String> expression = (Expression<String>) getExpression(likeExpression.scalar_expression(), rootContext, criteriaBuilder);
            if (likeExpression.NOT() != null) {
                if (escapeCharacter != null) {
                    return criteriaBuilder.notLike(expression, pattern, escapeCharacter);
                }
                return criteriaBuilder.notLike(expression, pattern);
            }
            if (escapeCharacter != null) {
                return criteriaBuilder.like(expression, pattern, escapeCharacter);
            }
            return criteriaBuilder.like(expression, pattern);
        }
        JQParser.In_expressionContext inExpression = conditionalExpression.in_expression();
        if (inExpression != null) {
            Expression<?> expression = getExpression(inExpression.scalar_expression(), rootContext, criteriaBuilder);
            CriteriaBuilder.In<?> in = criteriaBuilder.in(expression);
            JQParser.In_item_listContext inItemListContext = inExpression.in_item_list();
            JQParser.Input_parameterContext inputParameterContext = inItemListContext.input_parameter();
            if (inputParameterContext != null) {
                Expression e = getExpression(inputParameterContext, criteriaBuilder);
                in.value(e);
            } else {
                JQParser.In_item_list_manyContext inItemListManyContext = inItemListContext.in_item_list_many();
                if (inItemListManyContext != null) {
                    for (JQParser.In_itemContext item : inItemListManyContext.in_item()) {
                        Expression e = getExpression(item, criteriaBuilder);
                        in.value(e);
                    }
                }
            }
            if (inExpression.NOT() != null) {
                return in.not();
            }
            return in;
        }
        JQParser.Between_expressionContext betweenExpression = conditionalExpression.between_expression();
        if (betweenExpression != null) {
            Predicate between = criteriaBuilder.between(
                (Expression<String>) getExpression(betweenExpression.scalar_expression(0), rootContext, criteriaBuilder),
                (Expression<String>) getExpression(betweenExpression.scalar_expression(1), rootContext, criteriaBuilder),
                (Expression<String>) getExpression(betweenExpression.scalar_expression(2), rootContext, criteriaBuilder)
            );
            if (betweenExpression.NOT() != null) {
                return between.not();
            }
            return between;
        }
        JQParser.Null_comparison_expressionContext nullComparisonExpression = conditionalExpression.null_comparison_expression();
        if (nullComparisonExpression != null) {
            Expression<?> expression = getExpression(nullComparisonExpression.scalar_expression(), rootContext, criteriaBuilder);
            if (nullComparisonExpression.NOT() != null) {
                return criteriaBuilder.isNotNull(expression);
            }
            return criteriaBuilder.isNull(expression);
        }
        throw new IllegalStateException("Unsupported conditional expression: " + conditionalExpression);
    }

    private static Expression<String> getPattern(JQParser.Escaped_patternContext escapedPattern,
                                                 CriteriaBuilder criteriaBuilder) {
        if (escapedPattern.input_parameter() != null) {
            return (Expression<String>) getExpression(escapedPattern.input_parameter(), criteriaBuilder);
        }
        return criteriaBuilder.literal(getString(escapedPattern.literal_pattern().STRING().getText()));
    }

    @Nullable
    private static Expression<Character> getEscapeCharacter(JQParser.Escaped_patternContext escapedPattern,
                                                            CriteriaBuilder criteriaBuilder) {
        JQParser.Escape_characterContext escapeCharacter = escapedPattern.escape_character();
        if (escapeCharacter == null) {
            return null;
        }
        String value = getString(escapeCharacter.STRING().getText());
        if (value.length() != 1) {
            throw new IllegalStateException("LIKE ESCAPE must be a single character: " + escapeCharacter.getText());
        }
        return criteriaBuilder.literal(value.charAt(0));
    }

    private static String getString(String text) {
        String value = text.substring(1, text.length() - 1);
        if (text.startsWith("'")) {
            return value.replace("''", "'");
        }
        return value;
    }

    private static Expression<?> getExpression(JQParser.Select_expressionContext selectExpression,
                                               RootContext rootContext,
                                               CriteriaBuilder criteriaBuilder) {
        JQParser.Aggregate_expressionContext aggregateExpression = selectExpression.aggregate_expression();
        if (aggregateExpression != null) {
            return getExpression(aggregateExpression, rootContext, criteriaBuilder);
        }
        return getExpression(selectExpression.scalar_expression(), rootContext, criteriaBuilder);
    }

    private static Expression<?> getExpression(JQParser.Aggregate_expressionContext aggregateExpression,
                                               RootContext rootContext,
                                               CriteriaBuilder criteriaBuilder) {
        Expression<?> expression = getExpression(aggregateExpression.aggregate_argument(), rootContext);
        boolean distinct = aggregateExpression.DISTINCT() != null;
        if (aggregateExpression.COUNT() != null) {
            if (distinct) {
                return criteriaBuilder.countDistinct(expression);
            }
            return criteriaBuilder.count(expression);
        }
        if (aggregateExpression.aggregate_function().AVG() != null) {
            if (distinct) {
                return distinctAggregate("AVG", Double.class, expression, criteriaBuilder);
            }
            return criteriaBuilder.avg((Expression) expression);
        }
        if (aggregateExpression.aggregate_function().MAX() != null) {
            if (distinct) {
                return distinctAggregate("MAX", Comparable.class, expression, criteriaBuilder);
            }
            return criteriaBuilder.max((Expression) expression);
        }
        if (aggregateExpression.aggregate_function().MIN() != null) {
            if (distinct) {
                return distinctAggregate("MIN", Comparable.class, expression, criteriaBuilder);
            }
            return criteriaBuilder.min((Expression) expression);
        }
        if (aggregateExpression.aggregate_function().SUM() != null) {
            if (distinct) {
                return distinctAggregate("SUM", Number.class, expression, criteriaBuilder);
            }
            return criteriaBuilder.sum((Expression) expression);
        }
        throw new IllegalStateException("Unsupported aggregate expression: " + aggregateExpression.getText());
    }

    private static <T> Expression<T> distinctAggregate(String functionName,
                                                       Class<T> resultType,
                                                       Expression<?> expression,
                                                       CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.function(functionName + "_DISTINCT", resultType, expression);
    }

    private static Expression<?> getExpression(JQParser.Aggregate_argumentContext aggregateArgument,
                                               RootContext rootContext) {
        if (aggregateArgument.THIS() != null) {
            return (Expression<?>) rootContext.root();
        }
        if (aggregateArgument.id_expression() != null) {
            return getExpression(aggregateArgument.id_expression(), rootContext);
        }
        JQParser.Simple_path_expressionContext simplePathExpression = aggregateArgument.simple_path_expression();
        if (simplePathExpression != null) {
            String text = simplePathExpression.getText();
            if (rootContext.isRootReference(text)) {
                return (Expression<?>) rootContext.root();
            }
            return (Expression<?>) getExpression(simplePathExpression, false, rootContext);
        }
        throw new IllegalStateException("Unsupported aggregate argument: " + aggregateArgument.getText());
    }

    private static Expression<?> getExpression(JQParser.Scalar_expressionContext scalarExpression,
                                               RootContext rootContext,
                                               CriteriaBuilder criteriaBuilder) {
        JQParser.Primary_expressionContext primaryExpression = scalarExpression.primary_expression();
        if (primaryExpression != null) {
            return getExpression(primaryExpression, rootContext, criteriaBuilder);
        }
        if (scalarExpression.LPAREN() != null) {
            return getExpression(scalarExpression.scalar_expression(0), rootContext, criteriaBuilder);
        }
        if (scalarExpression.scalar_expression().size() == 1) {
            Expression<?> expression = getExpression(scalarExpression.scalar_expression(0), rootContext, criteriaBuilder);
            if (scalarExpression.PLUS() != null) {
                return expression;
            }
            if (scalarExpression.MINUS() != null) {
                return negate(expression, criteriaBuilder);
            }
        }
        Expression<?> firstExp = getExpression(
            Objects.requireNonNull((JQParser.Scalar_expressionContext) scalarExpression.getChild(0), "First expression cannot be null"),
            rootContext,
            criteriaBuilder
        );
        Expression<?> secondExp = getExpression(
            Objects.requireNonNull((JQParser.Scalar_expressionContext) scalarExpression.getChild(2), "First expression cannot be null"),
            rootContext,
            criteriaBuilder
        );
        if (scalarExpression.PLUS() != null) {
            return criteriaBuilder.sum((Expression) firstExp, (Expression) secondExp);
        }
        if (scalarExpression.MINUS() != null) {
            return criteriaBuilder.diff((Expression) firstExp, (Expression) secondExp);
        }
        if (scalarExpression.CONCAT() != null) {
            return criteriaBuilder.concat((Expression) firstExp, (Expression) secondExp);
        }
        if (scalarExpression.MUL() != null) {
            return criteriaBuilder.prod((Expression) firstExp, (Expression) secondExp);
        }
        if (scalarExpression.DIV() != null) {
            return criteriaBuilder.quot((Expression) firstExp, (Expression) secondExp);
        }
        throw new IllegalStateException("Unknown primary expression");
    }

    private static Expression<?> negate(Expression<?> expression, CriteriaBuilder criteriaBuilder) {
        if (expression instanceof LiteralExpression<?> literalExpression && literalExpression.getValue() instanceof Number number) {
            return criteriaBuilder.literal(negate(number));
        }
        return criteriaBuilder.prod((Expression) criteriaBuilder.literal(-1), (Expression) expression);
    }

    private static Number negate(Number number) {
        if (number instanceof Integer integer) {
            return -integer;
        }
        if (number instanceof Long longValue) {
            return -longValue;
        }
        if (number instanceof Float floatValue) {
            return -floatValue;
        }
        if (number instanceof Double doubleValue) {
            return -doubleValue;
        }
        throw new IllegalStateException("Unsupported numeric literal: " + number);
    }

    private static Expression<?> getExpression(JQParser.Primary_expressionContext context,
                                               RootContext rootContext,
                                               CriteriaBuilder criteriaBuilder) {
        if (context.literal() != null) {
            return getExpression(context.literal(), criteriaBuilder);
        }
        if (context.input_parameter() != null) {
            return getExpression(context.input_parameter(), criteriaBuilder);
        }
        if (context.special_expression() != null) {
            var specialExpression = context.special_expression().getText().replaceAll("\\s+", " ");
            return switch (specialExpression.toUpperCase(Locale.ROOT)) {
                case "TRUE" -> criteriaBuilder.literal(true);
                case "FALSE" -> criteriaBuilder.literal(false);
                case "LOCAL DATE" -> criteriaBuilder.localDate();
                case "LOCAL TIME" -> criteriaBuilder.localTime();
                case "LOCAL DATETIME" -> criteriaBuilder.localDateTime();
                default ->
                    throw new UnsupportedOperationException("Unsupported special expression: " + specialExpression);
            };
        }
        if (context.enum_literal() != null) {
            return getExpression(context.enum_literal(), criteriaBuilder);
        }
        if (context.simple_path_expression() != null) {
            var stateContext = context.simple_path_expression();
            return getPathOrEnumLiteral(stateContext, rootContext, criteriaBuilder);
        }
        JQParser.Function_expressionContext functionExpression = context.function_expression();
        if (functionExpression != null) {
            List<JQParser.Scalar_expressionContext> arguments = functionExpression.scalar_expression();
            String functionName = functionExpression.IDENTIFIER().getText().toLowerCase(Locale.ROOT);
            return switch (functionName) {
                case "abs" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("ABS", Number.class, expression);
                }
                case "ceiling", "ceil" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("CEILING", Number.class, expression);
                }
                case "exp" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("EXP", Number.class, expression);
                }
                case "floor" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("FLOOR", Number.class, expression);
                }
                case "ln" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("LN", Number.class, expression);
                }
                case "sign" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("SIGN", Number.class, expression);
                }
                case "sqrt" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function("SQRT", Number.class, expression);
                }
                case "length" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.length(expression);
                }
                case "lower" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.lower(expression);
                }
                case "upper" -> {
                    requireArgumentCount(functionExpression, arguments, 1);
                    Expression expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.upper(expression);
                }
                case "left" -> {
                    requireArgumentCount(functionExpression, arguments, 2);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function(
                        "LEFT",
                        String.class,
                        expression,
                        getExpression(arguments.get(1), rootContext, criteriaBuilder)
                    );
                }
                case "right" -> {
                    requireArgumentCount(functionExpression, arguments, 2);
                    Expression<?> expression = getExpression(arguments.get(0), rootContext, criteriaBuilder);
                    yield criteriaBuilder.function(
                        "RIGHT",
                        String.class,
                        expression,
                        getExpression(arguments.get(1), rootContext, criteriaBuilder)
                    );
                }
                case "concat" -> {
                    requireAtLeastArgumentCount(functionExpression, arguments, 2);
                    yield criteriaBuilder.function(
                        "CONCAT",
                        String.class,
                        getExpressions(arguments, rootContext, criteriaBuilder)
                    );
                }
                case "substring" -> {
                    requireArgumentCount(functionExpression, arguments, 2, 3);
                    yield criteriaBuilder.function(
                        "SUBSTRING",
                        String.class,
                        getExpressions(arguments, rootContext, criteriaBuilder)
                    );
                }
                case "locate" -> {
                    requireArgumentCount(functionExpression, arguments, 2, 3);
                    yield criteriaBuilder.function(
                        "LOCATE",
                        Integer.class,
                        getExpressions(arguments, rootContext, criteriaBuilder)
                    );
                }
                case "mod" -> {
                    requireArgumentCount(functionExpression, arguments, 2);
                    yield criteriaBuilder.function(
                        "MOD",
                        Integer.class,
                        getExpressions(arguments, rootContext, criteriaBuilder)
                    );
                }
                case "power" -> {
                    requireArgumentCount(functionExpression, arguments, 2);
                    yield criteriaBuilder.function(
                        "POWER",
                        Number.class,
                        getExpressions(arguments, rootContext, criteriaBuilder)
                    );
                }
                case "round" -> {
                    requireArgumentCount(functionExpression, arguments, 2);
                    yield criteriaBuilder.function(
                        "ROUND",
                        Number.class,
                        getExpressions(arguments, rootContext, criteriaBuilder)
                    );
                }
                default ->
                    throw new UnsupportedOperationException("Unsupported function expression: " + functionName);
            };
        }
        JQParser.Id_expressionContext idExpression = context.id_expression();
        if (idExpression != null) {
            return ((PersistentEntityRoot<?>) rootContext.root()).id();
        }
        throw new UnsupportedOperationException("Not supported expression: " + context.getText());
    }

    private static Expression<?>[] getExpressions(List<JQParser.Scalar_expressionContext> arguments,
                                                  RootContext rootContext,
                                                  CriteriaBuilder criteriaBuilder) {
        return arguments
            .stream()
            .map(argument -> getExpression(argument, rootContext, criteriaBuilder))
            .toArray(Expression<?>[]::new);
    }

    private static void requireArgumentCount(JQParser.Function_expressionContext functionExpression,
                                             List<JQParser.Scalar_expressionContext> arguments,
                                             int expectedCount) {
        if (arguments.size() != expectedCount) {
            throw new UnsupportedOperationException("Function " + functionExpression.IDENTIFIER().getText() + " expects " + expectedCount + " argument(s): " + functionExpression.getText());
        }
    }

    private static void requireArgumentCount(JQParser.Function_expressionContext functionExpression,
                                             List<JQParser.Scalar_expressionContext> arguments,
                                             int min,
                                             int max) {
        if (arguments.size() < min || arguments.size() > max) {
            throw new UnsupportedOperationException("Function " + functionExpression.IDENTIFIER().getText() + " expects between " + min + " and " + max + " argument(s): " + functionExpression.getText());
        }
    }

    private static void requireAtLeastArgumentCount(JQParser.Function_expressionContext functionExpression,
                                                   List<JQParser.Scalar_expressionContext> arguments,
                                                   int min) {
        if (arguments.size() < min) {
            throw new UnsupportedOperationException("Function " + functionExpression.IDENTIFIER().getText() + " expects at least " + min + " argument(s): " + functionExpression.getText());
        }
    }

    private static Expression<?> getExpression(JQParser.Enum_literalContext enumLiteralContext,
                                               CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.literal(getEnumValue(enumLiteralContext.getText()));
    }

    private static Expression<? extends Serializable> getExpression(JQParser.LiteralContext literal, CriteriaBuilder criteriaBuilder) {
        if (literal.string_literal() != null) {
            return criteriaBuilder.literal(
                getString(
                    literal.string_literal().STRING().getText()
                )
            );
        } else if (literal.numeric_literal() != null) {
            return criteriaBuilder.literal(getNumber(literal.numeric_literal().getText()));
        }
        throw new IllegalStateException("Unknown literal parameter: " + literal);
    }

    private static Serializable getNumber(String text) {
        String value = text.replace("_", "");
        char last = Character.toLowerCase(value.charAt(value.length() - 1));
        if (last == 'f') {
            return Float.valueOf(value.substring(0, value.length() - 1));
        }
        if (last == 'd') {
            return Double.valueOf(value.substring(0, value.length() - 1));
        }
        if (last == 'l') {
            return Long.valueOf(value.substring(0, value.length() - 1));
        }
        if (value.indexOf('.') > -1 || value.indexOf('e') > -1 || value.indexOf('E') > -1) {
            return Double.valueOf(value);
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return Long.valueOf(value);
        }
    }

    private static Selection<?> getExpression(JQParser.Simple_path_expressionContext simplePathExpression,
                                              boolean alias,
                                              RootContext rootContext) {
        var text = rootContext.stripIdentificationVariable(simplePathExpression.getText());
        Path<?> path = getPath(simplePathExpression, rootContext);
        if (alias) {
            return path.alias(text);
        }
        return path;
    }

    private static Expression<?> getPathOrEnumLiteral(JQParser.Simple_path_expressionContext simplePathExpression,
                                                      RootContext rootContext,
                                                      CriteriaBuilder criteriaBuilder) {
        try {
            return (Expression<?>) getExpression(simplePathExpression, false, rootContext);
        } catch (IllegalStateException e) {
            if (simplePathExpression.getText().indexOf('.') > -1) {
                return criteriaBuilder.literal(getEnumValue(simplePathExpression.getText()));
            }
            throw e;
        }
    }

    private static Path<?> getPath(JQParser.Simple_path_expressionContext simplePathExpression,
                                   RootContext rootContext) {
        Path<?> path = rootContext.root();
        String text = rootContext.stripIdentificationVariable(simplePathExpression.getText());
        if (text.isEmpty()) {
            return path;
        }
        int start = 0;
        int dot = text.indexOf('.');
        while (dot > -1) {
            path = path.get(text.substring(start, dot));
            start = dot + 1;
            dot = text.indexOf('.', start);
        }
        path = path.get(text.substring(start));
        return path;
    }

    private static String getEnumValue(String text) {
        int index = text.lastIndexOf('.');
        if (index > -1) {
            return text.substring(index + 1);
        }
        return text;
    }

    private static Expression<?> getExpression(JQParser.Input_parameterContext inputParameter,
                                               CriteriaBuilder criteriaBuilder) {
        SourcePersistentEntityCriteriaBuilder sourcePersistentEntityCriteriaBuilder = (SourcePersistentEntityCriteriaBuilder) criteriaBuilder;
        String text = inputParameter.getChild(0).getText();
        if (text.equals("?")) {
            int parameterIndex = Integer.parseInt(inputParameter.getChild(1).getText().replace("_", "")) - 1;
            return sourcePersistentEntityCriteriaBuilder.parameterReferencingMethodParameter(parameterIndex);
        }
        if (text.equals(":")) {
            return sourcePersistentEntityCriteriaBuilder.parameterReferencingMethodParameter(inputParameter.parameter_name().getText());
        }
        throw new IllegalStateException("Unknown input parameter: " + text);
    }

    private static Expression<?> getExpression(JQParser.In_itemContext inItem,
                                               CriteriaBuilder criteriaBuilder) {
        JQParser.LiteralContext literal = inItem.literal();
        if (literal != null) {
            return getExpression(literal, criteriaBuilder);
        }
        JQParser.Enum_literalContext enumLiteral = inItem.enum_literal();
        if (enumLiteral != null) {
            return getExpression(enumLiteral, criteriaBuilder);
        }
        JQParser.Input_parameterContext inputParameter = inItem.input_parameter();
        if (inputParameter != null) {
            return getExpression(inputParameter, criteriaBuilder);
        }
        throw new IllegalStateException("Unknown IN item: " + inItem);
    }

    private static Expression<?> getExpression(JQParser.Id_expressionContext idExpression,
                                               RootContext rootContext) {
        String idText = idExpression.IDENTIFIER().getText().toLowerCase(Locale.ROOT);
        if (!idText.equals("id")) {
            throw new IllegalStateException("Invalid id expression, expected ID(THIS) but got " + idExpression.getText());
        }
        return ((PersistentEntityRoot<?>) rootContext.root()).id();
    }

    private record RootContext(Root<?> root, @Nullable String identificationVariable) {

        boolean isRootReference(String text) {
            return identificationVariable != null && identificationVariable.equals(text);
        }

        String stripIdentificationVariable(String path) {
            if (identificationVariable == null) {
                return path;
            }
            if (path.equals(identificationVariable)) {
                return "";
            }
            String prefix = identificationVariable + ".";
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
            return path;
        }
    }

}
