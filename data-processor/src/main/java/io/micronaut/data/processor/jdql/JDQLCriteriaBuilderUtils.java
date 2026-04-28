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
package io.micronaut.data.processor.jdql;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.jdql.JDQLBaseListener;
import io.micronaut.data.jdql.JDQLParser;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCommonAbstractCriteria;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The utils to generate Criteria queries from Jakarta Data Query Language statements.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class JDQLCriteriaBuilderUtils {

    private JDQLCriteriaBuilderUtils() {
    }

    public static PersistentEntityCommonAbstractCriteria build(String query,
                                                               @Nullable PersistentEntity rootPersistentEntity,
                                                               MethodElement methodElement,
                                                               Function<String, ClassElement> classElementResolver,
                                                               SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        ParseTree child = parse(query, methodElement);
        if (child instanceof io.micronaut.data.jdql.JDQLParser.Delete_statementContext deleteStatementContext) {
            return JDQLCriteriaBuilderUtils.buildDelete(deleteStatementContext, classElementResolver, criteriaBuilder);
        }
        if (child instanceof JDQLParser.Update_statementContext updateStatementContext) {
            return JDQLCriteriaBuilderUtils.buildUpdate(updateStatementContext, classElementResolver, criteriaBuilder);
        }
        if (child instanceof JDQLParser.Select_statementContext select_clauseContext) {
            return JDQLCriteriaBuilderUtils.buildSelect(rootPersistentEntity, select_clauseContext, classElementResolver, criteriaBuilder, methodElement, query);
        }

        throw new MatchFailedException("Unrecognized query: " + child.getParent(), methodElement);
    }

    public static PersistentEntityCriteriaQuery<?> buildCount(String query,
                                                                    PersistentEntity rootPersistentEntity,
                                                                    MethodElement methodElement,
                                                                    Function<String, ClassElement> classElementResolver,
                                                                    SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        ParseTree child = parse(query, methodElement);
        if (child instanceof JDQLParser.Select_statementContext select_clauseContext) {
            return JDQLCriteriaBuilderUtils.buildCount(rootPersistentEntity, select_clauseContext, classElementResolver, criteriaBuilder);
        }

        throw new MatchFailedException("Unrecognized count query: " + child.getParent(), methodElement);
    }

    private static ParseTree parse(String query, Element originatingElement) {
        var inputStream = CharStreams.fromString(query);
        var lexer = new io.micronaut.data.jdql.JDQLLexer(inputStream);
        ANTLRErrorListener errorListener = new ANTLRErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new MatchFailedException("Failed to parse Jakarta Data query: " + prettifyAntlrError(offendingSymbol, line, charPositionInLine, msg, e, query), originatingElement);
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
        var parser = new JDQLParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);
        JDQLParser.StatementContext statement = parser.statement();
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
                                                               JDQLParser.Select_statementContext selectStatementContext,
                                                               Function<String, ClassElement> classElementResolver,
                                                               SourcePersistentEntityCriteriaBuilder criteriaBuilder,
                                                               @Nullable MethodElement methodElement,
                                                               String q) {

        SourcePersistentEntityCriteriaQuery<Object> query = criteriaBuilder
            .createQuery(null);
        PersistentEntityRoot<Object> root;
        JDQLParser.From_clauseContext fromClauseContext = selectStatementContext.from_clause();
        if (fromClauseContext != null) {
            String entityName = fromClauseContext.entity_name().getText();
            root = query.from(classElementResolver.apply(entityName));
        } else {
            Objects.requireNonNull(rootPersistentEntity, "Root persistent entity is not specified in the JDQL query: " + q);
            root = query.from(rootPersistentEntity);
        }
        Predicate predicate = getPredicate(selectStatementContext.where_clause(), root, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.orderBy(
            getOrders(selectStatementContext.orderby_clause(), root, criteriaBuilder, methodElement)
        );
        JDQLParser.Select_clauseContext selectClauseContext = selectStatementContext.select_clause();
        if (selectClauseContext != null) {
            JDQLParser.Select_itemsContext selectItems = selectClauseContext.select_items();
            if (selectItems != null) {
                query.multiselect(
                    selectItems.state_field_path_expression()
                        .stream()
                        .map(sfp -> {
                            Selection<?> expression = getExpression(sfp, true, root, criteriaBuilder);
                            if (methodElement != null) {
                                if (expression instanceof Path<?> path) {
                                    methodElement.annotate(Projection.class, b -> b.value(((PersistentPropertyPath<Object>) path).getProperty().getName()));
                                } else if (expression instanceof AliasedSelection<?> aliasedSelection) {
                                    methodElement.annotate(Projection.class, b -> b.value(((PersistentPropertyPath<Object>) aliasedSelection.getSelection()).getProperty().getName()));
                                }
                            }
                            return expression;
                        })
                        .collect(Collectors.toUnmodifiableList())
                );
            }
            JDQLParser.Select_itemContext selectItem = selectClauseContext.select_item();
            if (selectItem != null) {
                JDQLParser.Aggregate_expressionContext aggregateExpression = selectItem.aggregate_expression();
                if (aggregateExpression != null) {
                    query.select(criteriaBuilder.count(root));
                }
                JDQLParser.State_field_path_expressionContext sfp = selectItem.state_field_path_expression();
                if (sfp != null) {
                    query.select(getExpression(sfp, false, root, criteriaBuilder));
                }
                JDQLParser.Id_expressionContext idExpressionContext = selectItem.id_expression();
                if (idExpressionContext != null) {
                    query.select(getExpression(idExpressionContext, root));
                }
            }
        }
        return query;
    }

    public static PersistentEntityCriteriaQuery<?> buildCount(PersistentEntity rootPersistentEntity,
                                                              JDQLParser.Select_statementContext selectStatementContext,
                                                              Function<String, ClassElement> classElementResolver,
                                                              SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        SourcePersistentEntityCriteriaQuery<Object> query = criteriaBuilder
            .createQuery(null);
        PersistentEntityRoot<Object> root;
        JDQLParser.From_clauseContext fromClauseContext = selectStatementContext.from_clause();
        if (fromClauseContext != null) {
            String entityName = fromClauseContext.entity_name().getText();
            root = query.from(classElementResolver.apply(entityName));
        } else {
            root = query.from(rootPersistentEntity);
        }
        Predicate predicate = getPredicate(selectStatementContext.where_clause(), root, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.select(criteriaBuilder.count(root));
        return query;
    }

    public static PersistentEntityCriteriaUpdate<?> buildUpdate(JDQLParser.Update_statementContext updateStatementContext,
                                                                Function<String, ClassElement> classElementResolver,
                                                                SourcePersistentEntityCriteriaBuilder criteriaBuilder) {
        String entityName = updateStatementContext.entity_name().getText();

        JDQLParser.Where_clauseContext whereClauseContext = updateStatementContext.where_clause();

        SourcePersistentEntityCriteriaUpdate<Object> updateQuery = criteriaBuilder
            .createCriteriaUpdate(null);
        PersistentEntityRoot<Object> root = updateQuery.from(classElementResolver.apply(entityName));
        Predicate predicate = getPredicate(whereClauseContext, root, criteriaBuilder);
        if (predicate != null) {
            updateQuery.where(predicate);
        }

        ParseTreeWalker.DEFAULT.walk(new JDQLBaseListener() {

            @Override
            public void exitUpdate_item(JDQLParser.Update_itemContext ctx) {
                String name = ctx.state_field_path_expression().getText();
                Expression<?> expression = getExpression(ctx.scalar_expression(), root, criteriaBuilder);
                updateQuery.set(name, expression);
            }

        }, updateStatementContext);

        return updateQuery;
    }

    public static PersistentEntityCriteriaDelete<?> buildDelete(JDQLParser.Delete_statementContext deleteStatementContext,
                                                                Function<String, ClassElement> classElementResolver,
                                                                SourcePersistentEntityCriteriaBuilder criteriaBuilder) {

        JDQLParser.From_clauseContext fromClauseContext = deleteStatementContext.from_clause();
        String entityName = fromClauseContext.entity_name().getText();
        JDQLParser.Where_clauseContext whereClauseContext = deleteStatementContext.where_clause();

        SourcePersistentEntityCriteriaDelete<Object> deleteQuery = criteriaBuilder
            .createCriteriaDelete(null);
        PersistentEntityRoot<Object> root = deleteQuery.from(classElementResolver.apply(entityName));
        Predicate predicate = getPredicate(whereClauseContext, root, criteriaBuilder);
        if (predicate != null) {
            deleteQuery.where(predicate);
        }
        return deleteQuery;
    }

    @Nullable
    private static Predicate getPredicate(JDQLParser. @Nullable Where_clauseContext whereClause,
                                          Root<?> root,
                                          PersistentEntityCriteriaBuilder criteriaBuilder) {
        if (whereClause == null) {
            return null;
        }
        JDQLParser.Conditional_expressionContext conditionalExpression = whereClause.conditional_expression();
        return getPredicate(conditionalExpression, root, criteriaBuilder);
    }

    private static List<Order> getOrders(JDQLParser. @Nullable Orderby_clauseContext orderByClause,
                                         Root<?> root,
                                         PersistentEntityCriteriaBuilder criteriaBuilder,
                                         @Nullable
                                         MethodElement methodElement) {
        List<Order> orders = new ArrayList<>();
        if (orderByClause != null) {
            List<JDQLParser.Orderby_itemContext> orderbyItemContexts = orderByClause.orderby_item();
            for (JDQLParser.Orderby_itemContext orderbyItemContext : orderbyItemContexts) {
                Expression<?> expression = getExpression(orderbyItemContext.scalar_expression(), root, criteriaBuilder);
                orders.add(
                    orderbyItemContext.DESC() == null ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(expression)
                );
            }
        }
        if (methodElement != null) {
            for (AnnotationValue<?> av : methodElement.getAnnotationValuesByStereotype(OrderBy.class.getName())) {
                orders.add(criteriaBuilder.sort(
                    root.get(av.stringValue().orElseThrow()),
                    !av.booleanValue("descending").orElse(false),
                    av.booleanValue("ignoreCase").orElse(false)
                ));
            }
        }
        return orders;
    }

    private static Predicate getPredicate(JDQLParser.Conditional_expressionContext conditionalExpression,
                                          Root<?> root,
                                          PersistentEntityCriteriaBuilder criteriaBuilder) {
        if (conditionalExpression.LPAREN() != null) {
            return getPredicate(conditionalExpression.conditional_expression(0), root, criteriaBuilder);
        }
        if (conditionalExpression.AND() != null) {
            return criteriaBuilder.and(
                getPredicate(conditionalExpression.conditional_expression(0), root, criteriaBuilder),
                getPredicate(conditionalExpression.conditional_expression(1), root, criteriaBuilder)
            );
        }
        if (conditionalExpression.OR() != null) {
            return criteriaBuilder.or(
                getPredicate(conditionalExpression.conditional_expression(0), root, criteriaBuilder),
                getPredicate(conditionalExpression.conditional_expression(1), root, criteriaBuilder)
            );
        }
        if (conditionalExpression.NOT() != null) {
            return criteriaBuilder.not(
                getPredicate(conditionalExpression.conditional_expression(0), root, criteriaBuilder)
            );
        }
        JDQLParser.Comparison_expressionContext comparisonExpression = conditionalExpression.comparison_expression();
        if (comparisonExpression != null) {
            Expression<?> firstExp = getExpression(
                comparisonExpression.scalar_expression(0),
                root,
                criteriaBuilder
            );
            Expression<?> secondExp = getExpression(
                comparisonExpression.scalar_expression(1),
                root,
                criteriaBuilder
            );
            JDQLParser.Comparison_operatorContext comparisonOperator = comparisonExpression.comparison_operator();
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
        JDQLParser.Like_expressionContext likeExpression = conditionalExpression.like_expression();
        if (likeExpression != null) {
            Expression<String> pattern;
            if (likeExpression.STRING() != null) {
                pattern = criteriaBuilder.literal(
                    getString(
                        likeExpression.getChild(likeExpression.getChildCount() - 1).getText()
                    )
                );
            } else {
                pattern = (Expression<String>) getExpression(likeExpression.input_parameter(), criteriaBuilder);
            }
            Expression<String> expression = (Expression<String>) getExpression(likeExpression.scalar_expression(), root, criteriaBuilder);
            if (likeExpression.NOT() != null) {
                return criteriaBuilder.notLike(expression, pattern);
            }
            return criteriaBuilder.like(expression, pattern);
        }
        JDQLParser.In_expressionContext inExpression = conditionalExpression.in_expression();
        if (inExpression != null) {
            Expression<?> expression = getExpression(inExpression.scalar_expression(), root, criteriaBuilder);
            CriteriaBuilder.In<?> in = criteriaBuilder.in(expression);
            JDQLParser.In_item_listContext inItemListContext = inExpression.in_item_list();
            JDQLParser.Input_parameterContext inputParameterContext = inItemListContext.input_parameter();
            if (inputParameterContext != null) {
                Expression e = getExpression(inputParameterContext, criteriaBuilder);
                in.value(e);
            } else {
                JDQLParser.In_item_list_manyContext inItemListManyContext = inItemListContext.in_item_list_many();
                if (inItemListManyContext != null) {
                    for (JDQLParser.In_itemContext item : inItemListManyContext.in_item()) {
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
        JDQLParser.Between_expressionContext betweenExpression = conditionalExpression.between_expression();
        if (betweenExpression != null) {
            Predicate between = criteriaBuilder.between(
                (Expression<String>) getExpression(betweenExpression.scalar_expression(0), root, criteriaBuilder),
                (Expression<String>) getExpression(betweenExpression.scalar_expression(1), root, criteriaBuilder),
                (Expression<String>) getExpression(betweenExpression.scalar_expression(2), root, criteriaBuilder)
            );
            if (betweenExpression.NOT() != null) {
                return between.not();
            }
            return between;
        }
        JDQLParser.Null_comparison_expressionContext nullComparisonExpression = conditionalExpression.null_comparison_expression();
        if (nullComparisonExpression != null) {
            Expression<?> expression = getExpression(nullComparisonExpression.scalar_expression(), root, criteriaBuilder);
            if (nullComparisonExpression.NOT() != null) {
                return criteriaBuilder.isNotNull(expression);
            }
            return criteriaBuilder.isNull(expression);
        }
        throw new IllegalStateException("Unsupported conditional expression: " + conditionalExpression);
    }

    private static String getString(String text) {
        return text.substring(1, text.length() - 1);
    }

    private static Expression<?> getExpression(JDQLParser.Scalar_expressionContext scalarExpression,
                                               Root<?> root,
                                               CriteriaBuilder criteriaBuilder) {
        JDQLParser.Primary_expressionContext primaryExpression = scalarExpression.primary_expression();
        if (primaryExpression != null) {
            return getExpression(primaryExpression, root, criteriaBuilder);
        }
        Expression<?> firstExp = getExpression(
            Objects.requireNonNull((JDQLParser.Scalar_expressionContext) scalarExpression.getChild(0), "First expression cannot be null"),
            root,
            criteriaBuilder
        );
        Expression<?> secondExp = getExpression(
            Objects.requireNonNull((JDQLParser.Scalar_expressionContext) scalarExpression.getChild(2), "First expression cannot be null"),
            root,
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

    private static Expression<?> getExpression(JDQLParser.Primary_expressionContext context,
                                               Root<?> root,
                                               CriteriaBuilder criteriaBuilder) {
        if (context.literal() != null) {
            return getExpression(context.literal(), criteriaBuilder);
        }
        if (context.input_parameter() != null) {
            return getExpression(context.input_parameter(), criteriaBuilder);
        }
        if (context.special_expression() != null) {
            var specialExpression = context.special_expression().getText();
            return switch (specialExpression.toUpperCase(Locale.US)) {
                case "TRUE" -> criteriaBuilder.literal(true);
                case "FALSE" -> criteriaBuilder.literal(false);
                default ->
                    throw new UnsupportedOperationException("Unsupported special expression: " + specialExpression);
            };
        }
        if (context.enum_literal() != null) {
            return getExpression(context.enum_literal());
        }
        if (context.state_field_path_expression() != null) {
            var stateContext = context.state_field_path_expression();
            return (Expression<?>) getExpression(stateContext, false, root, criteriaBuilder);
        }
        JDQLParser.Function_expressionContext functionExpression = context.function_expression();
        if (functionExpression != null) {
            SourcePersistentEntityCriteriaBuilder sourcePersistentEntityCriteriaBuilder = (SourcePersistentEntityCriteriaBuilder) criteriaBuilder;
            Expression expression = getExpression(functionExpression.scalar_expression(0), root, criteriaBuilder);
            String functionName = functionExpression.getChild(0).getText().toLowerCase();
            return switch (functionName) {
                case "abs(" -> criteriaBuilder.abs(expression);
                case "length(" -> criteriaBuilder.length(expression);
                case "lower(" -> criteriaBuilder.lower(expression);
                case "upper(" -> criteriaBuilder.upper(expression);
                case "left(" -> sourcePersistentEntityCriteriaBuilder.startsWithString(
                    expression,
                    (Expression) getExpression(functionExpression.scalar_expression(1), root, criteriaBuilder)
                );
                case "right(" -> sourcePersistentEntityCriteriaBuilder.endingWithString(
                    expression,
                    (Expression) getExpression(functionExpression.scalar_expression(1), root, criteriaBuilder)
                );
                default ->
                    throw new UnsupportedOperationException("Unsupported function expression: " + functionName);
            };
        }
        JDQLParser.Id_expressionContext idExpression = context.id_expression();
        if (idExpression != null) {
            return ((PersistentEntityRoot<?>) root).id();
        }
        throw new UnsupportedOperationException("Not supported expression: " + context.getText());
    }

    private static Expression<?> getExpression(JDQLParser.Enum_literalContext enumLiteralContext) {
        throw new UnsupportedOperationException("Unsupported enum: " + enumLiteralContext);
    }

    private static Expression<? extends Serializable> getExpression(JDQLParser.LiteralContext literal, CriteriaBuilder criteriaBuilder) {
        if (literal.STRING() != null) {
            return criteriaBuilder.literal(
                getString(
                    literal.STRING().getText()
                )
            );
        } else if (literal.INTEGER() != null) {
            return criteriaBuilder.literal(Integer.valueOf(literal.INTEGER().getText()));
        } else if (literal.DOUBLE() != null) {
            return criteriaBuilder.literal(Double.valueOf(literal.DOUBLE().getText()));
        } else if (literal.FLOAT() != null) {
            return criteriaBuilder.literal(Float.valueOf(literal.FLOAT().getText()));
        }
        throw new IllegalStateException("Unknown literal parameter: " + literal);
    }

    private static Selection<?> getExpression(JDQLParser.State_field_path_expressionContext stateFieldPathExpression,
                                               boolean alias,
                                               Root<?> root,
                                               CriteriaBuilder criteriaBuilder) {
        var text = stateFieldPathExpression.getText();
        if (stateFieldPathExpression.FULLY_QUALIFIED_IDENTIFIER() != null) {
            return criteriaBuilder.literal(text);
        }
        if (alias) {
            return root.get(text).alias(text);
        }
        return root.get(text);
    }

    private static Expression<?> getExpression(JDQLParser.Input_parameterContext inputParameter,
                                               CriteriaBuilder criteriaBuilder) {
        SourcePersistentEntityCriteriaBuilder sourcePersistentEntityCriteriaBuilder = (SourcePersistentEntityCriteriaBuilder) criteriaBuilder;
        String text = inputParameter.getChild(0).getText();
        if (text.equals("?")) {
            int parameterIndex = Integer.parseInt(inputParameter.getChild(1).getText()) - 1;
            return sourcePersistentEntityCriteriaBuilder.parameterReferencingMethodParameter(parameterIndex);
        }
        if (text.equals(":")) {
            return sourcePersistentEntityCriteriaBuilder.parameterReferencingMethodParameter(inputParameter.getChild(1).getText());
        }
        throw new IllegalStateException("Unknown input parameter: " + text);
    }

    private static Expression<?> getExpression(JDQLParser.In_itemContext inItem,
                                               CriteriaBuilder criteriaBuilder) {
        JDQLParser.LiteralContext literal = inItem.literal();
        if (literal != null) {
            return getExpression(literal, criteriaBuilder);
        }
        JDQLParser.Enum_literalContext enumLiteral = inItem.enum_literal();
        if (enumLiteral != null) {
            return getExpression(enumLiteral);
        }
        JDQLParser.Input_parameterContext inputParameter = inItem.input_parameter();
        if (inputParameter != null) {
            return getExpression(inputParameter, criteriaBuilder);
        }
        throw new IllegalStateException("Unknown IN item: " + inItem);
    }

    private static Expression<?> getExpression(JDQLParser.Id_expressionContext idExpression,
                                               Root<?> root) {
        String idText = idExpression.IDENTIFIER().getText().toLowerCase(Locale.ROOT);
        if (!idText.equals("id")) {
            throw new IllegalStateException("Invalid id expression, expected ID(THIS) but got " + idExpression.getText());
        }
        return ((PersistentEntityRoot<?>) root).id();
    }

}
