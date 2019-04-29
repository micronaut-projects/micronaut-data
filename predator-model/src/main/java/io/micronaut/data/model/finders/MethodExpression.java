package io.micronaut.data.model.finders;

import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.factory.Restrictions;

import java.util.Arrays;


/**
 *  Method expression used to evaluate a dynamic finder.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class MethodExpression {

    protected String propertyName;
    protected String[] argumentNames;
    protected int argumentsRequired = 1;

    public abstract Query.Criterion createCriterion();

    protected MethodExpression(String propertyName) {
        this.propertyName = propertyName;
    }

    public int getArgumentsRequired() {
        return argumentsRequired;
    }

    public void setArgumentNames(String[] argumentNames) {
        this.argumentNames = argumentNames;
    }

    public String[] getArgumentNames() {
        return Arrays.copyOf(argumentNames, argumentNames.length);
    }

    public String getPropertyName() {
        return propertyName;
    }

    public static class GreaterThan extends MethodExpression {
        public GreaterThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.gt(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class GreaterThanEquals extends MethodExpression {
        public GreaterThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.gte(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class LessThan extends MethodExpression {
        public LessThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.lt(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class LessThanEquals extends MethodExpression {
        public LessThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.lte(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Like extends MethodExpression {
        public Like(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.like(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Ilike extends MethodExpression {
        public Ilike(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.ilike(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Rlike extends MethodExpression {
        public Rlike(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.rlike(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class NotInList extends MethodExpression {
        public NotInList(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            Query.Negation negation = new Query.Negation();
            negation.add(Restrictions.in(propertyName, new QueryParameter(argumentNames[0])));
            return negation;
        }
    }

    public static class InList extends MethodExpression {

        public InList(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.in(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Between extends MethodExpression {

        public Between(String propertyName) {
            super(propertyName);
            argumentsRequired = 2;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.between(propertyName, new QueryParameter(argumentNames[0]), new QueryParameter(argumentNames[1]));
        }
    }

    public static class InRange extends MethodExpression {

        public InRange(String propertyName) {
            super(propertyName);
            argumentsRequired = 1;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.between(propertyName, new QueryParameter(argumentNames[0]), new QueryParameter(argumentNames[1]));
        }
    }

    public static class IsNull extends MethodExpression {

        public IsNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isNull(propertyName);
        }

    }

    public static class IsNotNull extends MethodExpression {

        public IsNotNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isNotNull(propertyName);
        }

    }

    public static class IsEmpty extends MethodExpression {

        public IsEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isEmpty(propertyName);
        }

    }

    public static class IsNotEmpty extends MethodExpression {

        public IsNotEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isNotEmpty(propertyName);
        }
    }

    public static class Equal extends MethodExpression {

        public Equal(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            String argument = argumentNames[0];
            if (argument != null) {
                return Restrictions.eq(propertyName, new QueryParameter(argument));
            } else {
                return Restrictions.isNull(propertyName);
            }
        }

    }
    public static class NotEqual extends MethodExpression {

        public NotEqual(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            String argument = argumentNames[0];
            if (argument != null) {
                return Restrictions.ne(propertyName, new QueryParameter(argumentNames[0]));
            } else {
                return Restrictions.isNotNull(propertyName);
            }
        }

    }
}
