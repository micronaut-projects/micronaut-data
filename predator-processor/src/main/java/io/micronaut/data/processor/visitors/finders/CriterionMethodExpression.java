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
public abstract class CriterionMethodExpression {

    protected String propertyName;
    protected String[] argumentNames;
    protected int argumentsRequired = 1;

    /**
     * Default constructor.
     * @param propertyName The property name the criterion expression relates to
     */
    protected CriterionMethodExpression(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * Creates the criterion.
     * @return The criterion
     */
    public abstract Query.Criterion createCriterion();

    /**
     * The arguments required to satisfy the criterion.
     * @return The arguments required
     */
    public int getArgumentsRequired() {
        return argumentsRequired;
    }

    /**
     * Sets the argument names to use.
     * @param argumentNames The argument names.
     */
    public void setArgumentNames(String[] argumentNames) {
        this.argumentNames = argumentNames;
    }

    public static class GreaterThan extends CriterionMethodExpression {
        public GreaterThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.gt(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class After extends GreaterThan {
        public After(String propertyName) {
            super(propertyName);
        }
    }

    public static class Before extends LessThan {
        public Before(String propertyName) {
            super(propertyName);
        }
    }

    public static class GreaterThanEquals extends CriterionMethodExpression {
        public GreaterThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.gte(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class LessThan extends CriterionMethodExpression {
        public LessThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.lt(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class LessThanEquals extends CriterionMethodExpression {
        public LessThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.lte(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Like extends CriterionMethodExpression {
        public Like(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.like(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Ilike extends CriterionMethodExpression {
        public Ilike(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.ilike(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class Rlike extends CriterionMethodExpression {
        public Rlike(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.rlike(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class NotInList extends CriterionMethodExpression {
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

    public static class InList extends CriterionMethodExpression {

        public InList(String propertyName) {
            super(propertyName);
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.in(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    public static class In extends InList {
        public In(String propertyName) {
            super(propertyName);
        }
    }

    public static class Between extends CriterionMethodExpression {

        public Between(String propertyName) {
            super(propertyName);
            argumentsRequired = 2;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.between(propertyName, new QueryParameter(argumentNames[0]), new QueryParameter(argumentNames[1]));
        }
    }

    public static class InRange extends CriterionMethodExpression {

        public InRange(String propertyName) {
            super(propertyName);
            argumentsRequired = 1;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.between(propertyName, new QueryParameter(argumentNames[0]), new QueryParameter(argumentNames[1]));
        }
    }

    public static class IsNull extends CriterionMethodExpression {

        public IsNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isNull(propertyName);
        }

    }

    public static class IsNotNull extends CriterionMethodExpression {

        public IsNotNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isNotNull(propertyName);
        }

    }

    public static class IsEmpty extends CriterionMethodExpression {

        public IsEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isEmpty(propertyName);
        }

    }

    public static class IsNotEmpty extends CriterionMethodExpression {

        public IsNotEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public Query.Criterion createCriterion() {
            return Restrictions.isNotEmpty(propertyName);
        }
    }

    public static class Equal extends CriterionMethodExpression {

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
    public static class NotEqual extends CriterionMethodExpression {

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
