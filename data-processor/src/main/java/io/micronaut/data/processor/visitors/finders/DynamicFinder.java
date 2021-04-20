/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for dynamic finders. This class is designed to be used only within the compiler
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DynamicFinder extends AbstractPatternBasedMethod implements MethodCandidate {

    public static final String OPERATOR_OR = "Or";
    public static final String OPERATOR_AND = "And";
    public static final String[] OPERATORS = {OPERATOR_AND, OPERATOR_OR};
    private static final String NOT = "Not";
    private static final Map<String, Constructor> METHOD_EXPRESSIONS = new LinkedHashMap<>();
    private static Pattern methodExpressionPattern;

    static {
        // populate the default method expressions
        try {
            Class[] classes = Arrays.stream(CriterionMethodExpression.class.getClasses()).filter(c ->
                    CriterionMethodExpression.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())
            ).toArray(Class[]::new);
            Class[] constructorParamTypes = {String.class};
            for (Class c : classes) {
                METHOD_EXPRESSIONS.put(c.getSimpleName(), c.getConstructor(constructorParamTypes));
            }
        } catch (SecurityException e) {
            // ignore
        } catch (NoSuchMethodException e) {
            // ignore
        }

        resetMethodExpressionPattern();

    }

    private Pattern[] operatorPatterns;
    private String[] operators;

    /**
     * The prefixes to use.
     * @param prefixes The prefixes
     */
    protected DynamicFinder(final String... prefixes) {
        this(compilePattern(prefixes), OPERATORS);
    }

    /**
     * A custom finder and pattern.
     * @param pattern The pattern.
     * @param operators The operators to support
     */
    protected DynamicFinder(final Pattern pattern, final String[] operators) {
        super(pattern);
        this.operators = operators;
        this.operatorPatterns = new Pattern[operators.length];
        populateOperators(operators);
    }

    /**
     * Checks whether the given method is a match.
     *
     * @param methodElement The method element
     * @param matchContext
     * @return True if it is
     */
    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        String methodName = methodElement.getName();
        return pattern.matcher(methodName.subSequence(0, methodName.length())).find();
    }

    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        MethodElement methodElement = matchContext.getMethodElement();

        List<CriterionMethodExpression> expressions = new ArrayList<>();
        List<ProjectionMethodExpression> projectionExpressions = new ArrayList<>();
        ParameterElement[] parameters = matchContext.getParameters();
        String methodName = methodElement.getName();
        SourcePersistentEntity entity = matchContext.getRootEntity();
        VisitorContext visitorContext = matchContext.getVisitorContext();
        Matcher match = pattern.matcher(methodName);
        // find match
        match.find();

        String[] queryParameters;
        int totalRequiredArguments = 0;
        // get the sequence clauses
        List<Sort.Order> orderList = new ArrayList<>();
        String querySequence = matchForUpdate(matchContext, match.group(4));
        boolean matchedForUpdate = !Objects.equals(querySequence, match.group(4));
        querySequence = matchOrder(querySequence, orderList);
        String projectionSequence = match.group(3);

        if (projectionSequence.endsWith("Order") && methodName.contains("OrderBy" + querySequence)) {
            // disambiguate from a query with only OrderBy
            return null;
        }

        // if it contains operator and split
        boolean containsOperator = false;
        String operatorInUse = null;
        if (querySequence != null) {
            for (int i = 0; i < operators.length; i++) {
                Matcher currentMatcher = operatorPatterns[i].matcher(querySequence);
                if (currentMatcher.find()) {
                    containsOperator = true;
                    operatorInUse = operators[i];

                    queryParameters = querySequence.split(operatorInUse);

                    // loop through query parameters and create expressions
                    // calculating the number of arguments required for the expression
                    int argumentCursor = 0;
                    for (String queryParameter : queryParameters) {
                        CriterionMethodExpression currentExpression = findMethodExpression(entity, queryParameter);
                        final int requiredArgs = currentExpression.getArgumentsRequired();
                        // populate the arguments into the Expression from the argument list
                        String[] currentArguments = new String[requiredArgs];
                        if ((argumentCursor + requiredArgs) > parameters.length) {
                            visitorContext.fail("Insufficient arguments to method", methodElement);
                            return null;
                        }

                        for (int k = 0; k < requiredArgs; k++, argumentCursor++) {
                            ParameterElement argument = parameters[argumentCursor];
                            verifyFinderParameter(methodName, entity, currentExpression, argument);
                            currentArguments[k] = argument.getName();
                        }
                        initializeExpression(currentExpression, currentArguments);


                        // add to list of expressions
                        totalRequiredArguments += currentExpression.argumentsRequired;
                        expressions.add(currentExpression);
                    }
                    break;
                }
            }
        }

        if (StringUtils.isNotEmpty(projectionSequence)) {
            boolean processedThroughOperator = false;
            for (int i = 0; i < operators.length; i++) {
                Matcher currentMatcher = operatorPatterns[i].matcher(projectionSequence);
                if (currentMatcher.find()) {
                    processedThroughOperator = true;
                    String[] projections = projectionSequence.split(operators[i]);

                    // loop through query parameters and create expressions
                    // calculating the number of arguments required for the expression
                    for (String projection : projections) {
                        matchProjections(matchContext, projectionExpressions, projection);

                    }
                    break;
                }
            }

            if (!processedThroughOperator) {
                matchProjections(matchContext, projectionExpressions, projectionSequence);
            }
        }

        // otherwise there is only one expression
        if (!containsOperator && querySequence != null) {
            CriterionMethodExpression solo = findMethodExpression(entity, querySequence);


            final int requiredArguments = solo.getArgumentsRequired();
            if (requiredArguments > parameters.length) {
                visitorContext.fail("Insufficient arguments to method", methodElement);
                return null;
            }

            totalRequiredArguments += requiredArguments;
            String[] soloArgs = new String[requiredArguments];
            for (int i = 0; i < soloArgs.length; i++) {
                ParameterElement parameter = parameters[i];
                verifyFinderParameter(methodName, entity, solo, parameter);
                soloArgs[i] = parameter.getName();
            }
            initializeExpression(solo, soloArgs);
            expressions.add(solo);
        }

        // if the total of all the arguments necessary does not equal the number of arguments
        // throw exception
        if (totalRequiredArguments > parameters.length) {
            visitorContext.fail("Insufficient arguments to method", methodElement);
            return null;
        }

        QueryModel query = QueryModel.from(entity);
        ClassElement queryResultType = entity.getClassElement();

        List<AnnotationValue<Join>> joinSpecs = joinSpecsAtMatchContext(matchContext);

        if (CollectionUtils.isNotEmpty(joinSpecs)) {
            if (applyJoinSpecs(matchContext, query, entity, joinSpecs)) {
                return null;
            }
        }

        if (matchedForUpdate) {
            applyForUpdate(query);
        }

        if (applyOrderBy(matchContext, query, orderList)) {
            return null;
        }

        if (CollectionUtils.isNotEmpty(projectionExpressions)) {

            if (projectionExpressions.size() == 1) {
                // only one projection so the return type should match the project result type
                ProjectionMethodExpression projection = projectionExpressions.get(0);
                queryResultType = projection.getExpectedResultType();
                projection.apply(matchContext, query);
            } else {
                for (ProjectionMethodExpression projectionExpression : projectionExpressions) {
                    projectionExpression.apply(matchContext, query);
                }
            }
        }
        QueryParameter versionMatchParameter = null;
        if ("Or".equalsIgnoreCase(operatorInUse)) {
            QueryModel.Disjunction disjunction = new QueryModel.Disjunction();
            for (CriterionMethodExpression expression : expressions) {
                disjunction.add(expression.createCriterion());
            }

            query.add(disjunction);
        } else {
            for (CriterionMethodExpression expression : expressions) {
                QueryModel.Criterion criterion = expression.createCriterion();
                if (criterion instanceof QueryModel.Equals) {
                    QueryModel.Equals equals = (QueryModel.Equals) criterion;
                    String property = equals.getProperty();
                    SourcePersistentProperty persistentProperty = entity.getPropertyByName(property);
                    if (persistentProperty == null) {
                        if (TypeRole.ID.equals(property) && (entity.hasIdentity() || entity.hasCompositeIdentity())) {
                            query.idEq((QueryParameter) equals.getValue());
                        } else {
                            // Unknown property...
                            query.add(criterion);
                        }
                    } else if (persistentProperty == entity.getIdentity()) {
                        query.idEq((QueryParameter) equals.getValue());
                    } else if (persistentProperty == entity.getVersion()) {
                        versionMatchParameter = (QueryParameter) equals.getValue();
                        query.versionEq((QueryParameter) equals.getValue());
                    } else {
                        query.add(criterion);
                    }
                } else {
                    query.add(criterion);
                }
            }
        }

        MethodMatchInfo methodMatchInfo = buildInfo(
                matchContext,
                queryResultType,
                query
        );
        if (methodMatchInfo != null && versionMatchParameter != null) {
            methodMatchInfo.setOptimisticLock(true);
        }
        return methodMatchInfo;
    }

    private void verifyFinderParameter(String methodName, SourcePersistentEntity entity, CriterionMethodExpression methodExpression, ParameterElement parameter) {
        String propertyName = methodExpression.propertyName;
        boolean isID = propertyName.equals(TypeRole.ID);
        ClassElement genericType = parameter.getGenericType();
        if (TypeUtils.isContainerType(genericType)) {
            genericType = genericType.getFirstTypeArgument().orElse(genericType);
        }
        if (isID) {
            if (entity.hasCompositeIdentity()) {
                // Validate composite identity
                return;
            }
            SourcePersistentProperty identity = entity.getIdentity();
            if (identity != null && !TypeUtils.areTypesCompatible(genericType, identity.getType())) {
                throw new IllegalArgumentException("Parameter [" + genericType.getType().getName() + " " + parameter.getName() + "] of method [" + methodName + "] is not compatible with property [" + identity.getType().getName() + " " + identity.getName() + "] of entity: " + entity.getName());
            }
            return;
        }
        SourcePersistentProperty persistentProperty = (SourcePersistentProperty) entity.getPropertyByPath(propertyName).orElse(null);
        if (persistentProperty != null) {
            if (!TypeUtils.areTypesCompatible(genericType, persistentProperty.getType())) {
                if (!genericType.isAssignable(Iterable.class)) {
                    throw new IllegalArgumentException("Parameter [" + genericType.getType().getName() + " " + parameter.getName() + "] of method [" + methodName + "] is not compatible with property [" + persistentProperty.getType().getName() + " " + persistentProperty.getName() + "] of entity: " + entity.getName());
                }
            }
            return;
        }
        throw new IllegalArgumentException("Cannot query entity [" + entity.getSimpleName() + "] on non-existent property: " + propertyName);
    }

    private void initializeExpression(CriterionMethodExpression currentExpression, String[] currentArguments) {
        currentExpression.setArgumentNames(currentArguments);
    }

    private static Pattern compilePattern(String[] prefixes) {
        if (ArrayUtils.isEmpty(prefixes)) {
            throw new IllegalArgumentException("At least one prefix required");
        }
        String prefixPattern = String.join("|", prefixes);
        String patternStr = "((" + prefixPattern + ")([\\w\\d]*?)By)([A-Z]\\w*)";
        return Pattern.compile(patternStr);
    }

    private static CriterionMethodExpression findMethodExpression(SourcePersistentEntity entity, String expression) {
        CriterionMethodExpression me = null;
        final Matcher matcher = methodExpressionPattern.matcher(expression);
        Class methodExpressionClass = CriterionMethodExpression.Equal.class;
        Constructor methodExpressionConstructor = null;
        String clause = methodExpressionClass.getSimpleName();
        if (matcher.find()) {
            clause = matcher.group(1);
            methodExpressionConstructor = METHOD_EXPRESSIONS.get(clause);
            if (methodExpressionConstructor != null) {
                methodExpressionClass = methodExpressionConstructor.getDeclaringClass();
            }
        }

        String propertyName = calcPropertyName(expression, methodExpressionClass.getSimpleName());
        boolean negation = false;
        if (propertyName.endsWith(NOT)) {
            int i = propertyName.lastIndexOf(NOT);
            propertyName = propertyName.substring(0, i);
            negation = true;
        }

        if (StringUtils.isEmpty(propertyName)) {
            throw new IllegalArgumentException("No property name specified in clause: " + clause);
        }

        propertyName = NameUtils.decapitalize(propertyName);

        SourcePersistentProperty prop = entity.getPropertyByName(propertyName);
        if (prop == null) {
            Optional<String> propertyPath = entity.getPath(propertyName);
            if (propertyPath.isPresent()) {
                String path = propertyPath.get();
                propertyName = path;
                PersistentProperty persistentProperty = entity.getPropertyByPath(path).orElse(null);
                if (persistentProperty != null) {
                    int i = path.lastIndexOf('.');
                    if (i > -1) {
                        String associationPath = path.substring(0, i);
                        SourcePersistentProperty pp = (SourcePersistentProperty) entity.getPropertyByPath(associationPath).orElse(null);
                        if (pp instanceof Association) {
                            AssociationQuery finalQuery = new AssociationQuery(associationPath, (Association) pp);
                            CriterionMethodExpression finalSubExpression = buildCriterionExpression(
                                    methodExpressionConstructor,
                                    persistentProperty.getName(),
                                    negation
                            );
                            return new CriterionMethodExpression(path) {
                                @Override
                                public QueryModel.Criterion createCriterion() {
                                    QueryModel.Criterion c = finalSubExpression.createCriterion();
                                    finalQuery.add(c);
                                    return finalQuery;
                                }

                                @Override
                                public int getArgumentsRequired() {
                                    return finalSubExpression.getArgumentsRequired();
                                }

                                @Override
                                public void setArgumentNames(String[] argumentNames) {
                                    finalSubExpression.setArgumentNames(argumentNames);
                                }
                            };
                        }
                    }
                }
            }
            return buildCriterionExpression(methodExpressionConstructor, propertyName, negation);
        } else {
            return buildCriterionExpression(methodExpressionConstructor, propertyName, negation);
        }
    }

    private static CriterionMethodExpression buildCriterionExpression(Constructor methodExpressionConstructor, String propertyName, boolean negation) {
        CriterionMethodExpression me = null;
        if (methodExpressionConstructor != null) {
            try {
                me = (CriterionMethodExpression) methodExpressionConstructor.newInstance(propertyName);
            } catch (Exception e) {
                // ignore
            }
        }
        if (me == null) {
            me = new CriterionMethodExpression.Equal(propertyName);
        }
        if (negation) {
            final CriterionMethodExpression finalMe = me;
            return new CriterionMethodExpression(propertyName) {
                @Override
                public QueryModel.Criterion createCriterion() {
                    return new QueryModel.Negation().add(finalMe.createCriterion());
                }

                @Override
                public int getArgumentsRequired() {
                    return finalMe.getArgumentsRequired();
                }

                @Override
                public void setArgumentNames(String[] argumentNames) {
                    finalMe.setArgumentNames(argumentNames);
                }
            };
        }
        return me;
    }

    private static void resetMethodExpressionPattern() {
        String expressionPattern = String.join("|", METHOD_EXPRESSIONS.keySet());
        methodExpressionPattern = Pattern.compile("\\p{Upper}[\\p{Lower}\\d]+(" + expressionPattern + ")");
    }

    private void populateOperators(String[] operators) {
        for (int i = 0; i < operators.length; i++) {
            operatorPatterns[i] = Pattern.compile("(\\w+)(" + operators[i] + ")(\\p{Upper})(\\w+)");
        }
    }

    private static String calcPropertyName(String queryParameter, String clause) {
        String propName;
        if (clause != null) {
            int i = queryParameter.lastIndexOf(clause);
            if (i > -1) {
                propName = queryParameter.substring(0, i);
            } else {
                propName = queryParameter;
            }
        } else {
            propName = queryParameter;
        }

        return propName;
    }
}
