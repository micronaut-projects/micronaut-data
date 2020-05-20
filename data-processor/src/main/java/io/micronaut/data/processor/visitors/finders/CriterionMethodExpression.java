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

import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.factory.Restrictions;

/**
 *  Method expression used to evaluate a dynamic finder.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class CriterionMethodExpression {

    private static final String IGNORE_CASE_SUFFIX = "IgnoreCase";
    protected final boolean ignoreCase;
    protected String propertyName;
    protected String[] argumentNames;
    protected int argumentsRequired = 1;

    /**
     * Default constructor.
     * @param propertyName The property name the criterion expression relates to
     */
    protected CriterionMethodExpression(String propertyName) {
        if (propertyName.endsWith(IGNORE_CASE_SUFFIX)) {
            this.propertyName = propertyName.substring(0, propertyName.length() - IGNORE_CASE_SUFFIX.length());
            this.ignoreCase = true;
        } else {
            this.propertyName = propertyName;
            this.ignoreCase = false;
        }
    }

    /**
     * Creates the criterion.
     * @return The criterion
     */
    public abstract QueryModel.Criterion createCriterion();

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

    /**
     * Greater than expression.
     */
    public static class GreaterThan extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public GreaterThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.gt(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Same as {@link GreaterThan}.
     */
    public static class After extends GreaterThan {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public After(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Same as {@link LessThan}.
     */
    public static class Before extends LessThan {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Before(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Greater than equals.
     */
    public static class GreaterThanEquals extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public GreaterThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.gte(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Less than.
     */
    public static class LessThan extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public LessThan(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.lt(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Less than equals.
     */
    public static class LessThanEquals extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public LessThanEquals(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.lte(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Like criterion.
     */
    public static class Like extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Like(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.like(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Contains with criterion.
     */
    public static class Contains extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Contains(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.contains(propertyName, new QueryParameter(argumentNames[0]));
        }
    }


    /**
     * Contains with criterion.
     */
    public static class Containing extends Contains {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Containing(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Starts with criterion.
     */
    public static class StartsWith extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public StartsWith(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.startsWith(propertyName, new QueryParameter(argumentNames[0]))
                    .ignoreCase(ignoreCase);
        }
    }


    /**
     * Starts with criterion.
     */
    public static class StartingWith extends StartsWith {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public StartingWith(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Ends with criterion.
     */
    public static class EndsWith extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public EndsWith(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.endsWith(propertyName, new QueryParameter(argumentNames[0]))
                                .ignoreCase(ignoreCase);
        }
    }

    /**
     * Ends with criterion.
     */
    public static class EndingWith extends EndsWith {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public EndingWith(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Case insensitive like.
     */
    public static class Ilike extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Ilike(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.ilike(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Regex like.
     */
    public static class Rlike extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Rlike(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.rlike(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * Not in list.
     */
    public static class NotInList extends CriterionMethodExpression {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public NotInList(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            QueryModel.Negation negation = new QueryModel.Negation();
            negation.add(Restrictions.in(propertyName, new QueryParameter(argumentNames[0])));
            return negation;
        }
    }

    /**
     * In list.
     */
    public static class InList extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public InList(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.in(propertyName, new QueryParameter(argumentNames[0]));
        }
    }

    /**
     * In criterion.
     */
    public static class In extends InList {
        /**
         * Default constructor.
         * @param propertyName The property
         */
        public In(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Between criterion.
     */
    public static class Between extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Between(String propertyName) {
            super(propertyName);
            argumentsRequired = 2;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.between(propertyName, new QueryParameter(argumentNames[0]), new QueryParameter(argumentNames[1]));
        }
    }

    /**
     * In range criterion.
     */
    public static class InRange extends Between {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public InRange(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Is null criterion.
     */
    public static class IsNull extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public IsNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.isNull(propertyName);
        }

    }


    /**
     * true criterion.
     */
    public static class True extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public True(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.isTrue(propertyName);
        }

    }

    /**
     * false criterion.
     */
    public static class False extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public False(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.isFalse(propertyName);
        }
    }

    /**
     * Is not null criterion.
     */
    public static class IsNotNull extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public IsNotNull(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.isNotNull(propertyName);
        }

    }

    /**
     * Is empty criterion.
     */
    public static class IsEmpty extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public IsEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.isEmpty(propertyName);
        }

    }

    /**
     * Is not empty criterion.
     */
    public static class IsNotEmpty extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public IsNotEmpty(String propertyName) {
            super(propertyName);
            argumentsRequired = 0;
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            return Restrictions.isNotEmpty(propertyName);
        }
    }

    /**
     * Is equal criterion.
     */
    public static class Equal extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public Equal(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            String argument = argumentNames[0];
            if (argument != null) {
                return Restrictions.eq(propertyName, new QueryParameter(argument))
                            .ignoreCase(ignoreCase);
            } else {
                return Restrictions.isNull(propertyName);
            }
        }

    }

    /**
     * Is not equal criterion.
     */
    public static class NotEqual extends CriterionMethodExpression {

        /**
         * Default constructor.
         * @param propertyName The property
         */
        public NotEqual(String propertyName) {
            super(propertyName);
        }

        @Override
        public QueryModel.Criterion createCriterion() {
            String argument = argumentNames[0];
            if (argument != null) {
                return Restrictions.ne(propertyName, new QueryParameter(argumentNames[0]))
                        .ignoreCase(ignoreCase);
            } else {
                return Restrictions.isNotNull(propertyName);
            }
        }

    }
}
