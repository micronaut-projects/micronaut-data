package io.micronaut.data.model.finders;


import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.DefaultQuery;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.factory.Restrictions;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for dynamic finders. This class is designed to be used only within the compiler
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class DynamicFinder implements FinderMethod {

    protected Pattern pattern;

    public static final String OPERATOR_OR = "Or";
    public static final String OPERATOR_AND = "And";
    private static final String[] DEFAULT_OPERATORS = {OPERATOR_AND, OPERATOR_OR};
    private Pattern[] operatorPatterns;
    private String[] operators;

    private static Pattern methodExpressionPattern;
    private static final Pattern[] defaultOperationPatterns;

    private static final String NOT = "Not";
    private static final Map<String, Constructor> methodExpressions = new LinkedHashMap<String, Constructor>();

    static {
        defaultOperationPatterns = new Pattern[2];
        for (int i = 0; i < DEFAULT_OPERATORS.length; i++) {
            String defaultOperator = DEFAULT_OPERATORS[i];
            defaultOperationPatterns[i] = Pattern.compile("(\\w+)(" + defaultOperator + ")(\\p{Upper})(\\w+)");
        }

        // populate the default method expressions
        try {
            Class[] classes = {
                    MethodExpression.Equal.class, MethodExpression.NotEqual.class, MethodExpression.NotInList.class, MethodExpression.InList.class, MethodExpression.InRange.class, MethodExpression.Between.class, MethodExpression.Like.class, MethodExpression.Ilike.class, MethodExpression.Rlike.class,
                    MethodExpression.GreaterThanEquals.class, MethodExpression.LessThanEquals.class, MethodExpression.GreaterThan.class,
                    MethodExpression.LessThan.class, MethodExpression.IsNull.class, MethodExpression.IsNotNull.class, MethodExpression.IsEmpty.class,
                    MethodExpression.IsEmpty.class, MethodExpression.IsNotEmpty.class };
            Class[] constructorParamTypes = { String.class };
            for (Class c : classes) {
                methodExpressions.put(c.getSimpleName(), c.getConstructor(constructorParamTypes));
            }
        } catch (SecurityException e) {
            // ignore
        } catch (NoSuchMethodException e) {
            // ignore
        }

        resetMethodExpressionPattern();
    }


    protected DynamicFinder(final Pattern pattern, final String[] operators) {
        this.pattern = pattern;
        this.operators = operators;
        this.operatorPatterns = new Pattern[operators.length];
        populateOperators(operators);
    }

    /**
     * Builds a match specification that can be used to establish information about a dynamic finder compilation for the purposes of compilation etc.
     *
     * @param entity The persistent entity
     * @param methodElement The method element
     * @return The query or null if it cannot be built
     */
    @Override
    public Query buildQuery(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            VisitorContext visitorContext) {
        List<MethodExpression> expressions = new ArrayList<>();
        ParameterElement[] arguments = methodElement.getParameters();
        String methodName = methodElement.getName();
        if (arguments == null) {
            arguments = new ParameterElement[0];
        }
        Matcher match = pattern.matcher(methodName);
        // find match
        match.find();

        String[] queryParameters;
        int totalRequiredArguments = 0;
        // get the sequence clauses
        final String querySequence;
        int groupCount = match.groupCount();
        if (groupCount == 6) {
            String booleanProperty = match.group(4);
            if (booleanProperty == null) {
                booleanProperty = match.group(6);
                querySequence = null;
            }
            else {
                querySequence = match.group(5);
            }
            Boolean arg = Boolean.TRUE;
            if (booleanProperty.matches("Not[A-Z].*")) {
                booleanProperty = booleanProperty.substring(3);
                arg = Boolean.FALSE;
            }
            MethodExpression booleanExpression = findMethodExpression(booleanProperty);
//          TODO:  booleanExpression.set(new Object[]{arg});
            expressions.add(booleanExpression);
        }
        else {
            querySequence = match.group(3);
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
                        MethodExpression currentExpression = findMethodExpression(queryParameter);
                        final int requiredArgs = currentExpression.getArgumentsRequired();
                        // populate the arguments into the Expression from the argument list
                        String[] currentArguments = new String[requiredArgs];
                        if ((argumentCursor + requiredArgs) > arguments.length) {
                            throw new IllegalArgumentException("Insufficient arguments to method: " + methodName);
                        }

                        for (int k = 0; k < requiredArgs; k++, argumentCursor++) {
                            ParameterElement argument = arguments[argumentCursor];
                            currentArguments[k] = argument.getName();
                        }
                        currentExpression = getInitializedExpression(currentExpression, currentArguments);


                        // add to list of expressions
                        totalRequiredArguments += currentExpression.argumentsRequired;
                        expressions.add(currentExpression);
                    }
                    break;
                }
            }
        }
        // otherwise there is only one expression
        if (!containsOperator && querySequence != null) {
            MethodExpression solo = findMethodExpression(querySequence);

            final int requiredArguments = solo.getArgumentsRequired();
            if (requiredArguments  > arguments.length) {
                throw new IllegalArgumentException("Insufficient arguments to method: " + methodName);
            }

            totalRequiredArguments += requiredArguments;
            String[] soloArgs = new String[requiredArguments];
            for (int i = 0; i < soloArgs.length; i++) {
                soloArgs[i] = arguments[i].getName();
            }
            solo = getInitializedExpression(solo, soloArgs);
            expressions.add(solo);
        }

        // if the total of all the arguments necessary does not equal the number of arguments
        // throw exception
        if (totalRequiredArguments > arguments.length) {
            throw new IllegalArgumentException("Insufficient arguments to method: " + methodName);
        }

        // calculate the remaining arguments
        Object[] remainingArguments = new Object[arguments.length - totalRequiredArguments];
        if (remainingArguments.length > 0) {
            for (int i = 0, j = totalRequiredArguments; i < remainingArguments.length; i++,j++) {
                remainingArguments[i] = arguments[j];
            }
        }

        Query query = Query.from(entity);
        if (operatorInUse != null && "Or".equalsIgnoreCase(operatorInUse)) {
            Query.Disjunction disjunction = new Query.Disjunction();
            for (MethodExpression expression : expressions) {
                disjunction.add(expression.createCriterion());
            }

            query.add(disjunction);
        } else {
            for (MethodExpression expression : expressions) {
                query.add(expression.createCriterion());
            }
        }
        return query;
    }

    /**
     * Sets the pattern to use for this finder
     *
     * @param pattern A regular expression
     */
    public void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    /**
     * Checks whether the given method is a match
     * @param methodName The method name
     * @return True if it is
     */
    public boolean isMethodMatch(String methodName) {
        return pattern.matcher(methodName.subSequence(0, methodName.length())).find();
    }

    private MethodExpression getInitializedExpression(MethodExpression currentExpression, String[] currentArguments) {
        currentExpression.setArgumentNames(currentArguments);
        return currentExpression;
    }

    protected static MethodExpression findMethodExpression(String expression) {
        MethodExpression me = null;
        final Matcher matcher = methodExpressionPattern.matcher(expression);
        Class methodExpressionClass = MethodExpression.Equal.class;
        Constructor methodExpressionConstructor = null;
        String clause = methodExpressionClass.getSimpleName();
        if (matcher.find()) {
            clause = matcher.group(1);
            methodExpressionConstructor = methodExpressions.get(clause);
            if(methodExpressionConstructor != null) {
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
        if(methodExpressionConstructor != null) {
            try {
                me = (MethodExpression) methodExpressionConstructor.newInstance(propertyName);
            } catch (Exception e) {
                // ignore
            }
        }
        if (me == null) {
            me = new MethodExpression.Equal(propertyName);
        }
        if(negation) {
            final MethodExpression finalMe = me;
            return new MethodExpression(propertyName) {
                @Override
                public Query.Criterion createCriterion() {
                    return new Query.Negation().add(finalMe.createCriterion());
                }
                @Override
                public int getArgumentsRequired() {
                    return finalMe.getArgumentsRequired();
                }
            };
        }
        return me;
    }


    private static void resetMethodExpressionPattern() {
        String expressionPattern = String.join("|", methodExpressions.keySet());
        methodExpressionPattern = Pattern.compile("\\p{Upper}[\\p{Lower}\\d]+(" + expressionPattern + ")");
    }

    private void populateOperators(String[] operators) {
        for (int i = 0; i < operators.length; i++) {
            operatorPatterns[i] = Pattern.compile("(\\w+)(" + operators[i] + ")(\\p{Upper})(\\w+)");
        }
    }

    private static String calcPropertyName(String queryParameter, String clause) {
        String propName;
        if (clause != null && !clause.equals(MethodExpression.Equal.class.getSimpleName())) {
            int i = queryParameter.indexOf(clause);
            propName = queryParameter.substring(0,i);
        }
        else {
            propName = queryParameter;
        }

        return propName;
    }
}
