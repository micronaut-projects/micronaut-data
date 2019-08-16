/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Method expression for performing a projection of some sort.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class ProjectionMethodExpression {

    private final int requiredProperties;

    /**
     * Default constructor.
     * @param requiredProperties The number of required properties.
     */
    ProjectionMethodExpression(int requiredProperties) {
        this.requiredProperties = requiredProperties;
    }

    /**
     * Match a projection.
     * @param matchContext The context
     * @param projection The projection to match
     * @return An expression or null if no match is possible or an error occurred.
     */
    public static @Nullable ProjectionMethodExpression matchProjection(@NonNull MethodMatchContext matchContext, @NonNull String projection) {

        Class<?>[] innerClasses = ProjectionMethodExpression.class.getClasses();
        SourcePersistentEntity entity = matchContext.getRootEntity();
        String decapitilized = NameUtils.decapitalize(projection);
        Optional<String> path = entity.getPath(decapitilized);
        if (path.isPresent()) {
            return new Property().init(matchContext, projection);
        } else {

            for (Class<?> innerClass : innerClasses) {
                String simpleName = innerClass.getSimpleName();
                if (projection.startsWith(simpleName)) {
                    Object o;
                    try {
                        o = innerClass.newInstance();
                    } catch (Throwable e) {
                        continue;
                    }


                    if (o instanceof ProjectionMethodExpression) {
                        ProjectionMethodExpression pme = (ProjectionMethodExpression) o;
                        ProjectionMethodExpression initialized = pme.init(matchContext, projection);
                        if (initialized != null) {
                            return initialized;
                        }
                    }
                }
            }

            // allow findAllBy as an alternative to findBy
            if (!Arrays.asList("all", "one").contains(decapitilized)) {
                Matcher topMatcher = Pattern.compile("^(top|first)(\\d*)$").matcher(decapitilized);
                if (topMatcher.find()) {
                    return new RestrictMaxResultProjection(topMatcher, matchContext).initProjection(matchContext, decapitilized);
                }
                // if the return type simple name is the same then we assume this is ok
                // this allows for Optional findOptionalByName
                if (!projection.equals(matchContext.getReturnType().getSimpleName())) {
                    matchContext.fail("Cannot project on non-existent property: " + decapitilized);
                }
            }
            return null;
        }
    }

    /**
     * Creates the projection or returns null if an error occurred reporting the error to the visitor context.
     * @param matchContext The visitor context
     * @param projectionDefinition The projection definition
     * @return The projection
     */
    protected final @Nullable ProjectionMethodExpression init(
            @NonNull MethodMatchContext matchContext,
            @NonNull String projectionDefinition
    ) {
        int len = getClass().getSimpleName().length();
        if (projectionDefinition.length() >= len) {
            String remaining = projectionDefinition.substring(len);
            return initProjection(matchContext, NameUtils.decapitalize(remaining));
        } else {
            return initProjection(matchContext, projectionDefinition);
        }
    }

    /**
     * Initialize the projection, returning null if it cannot be initialized.
     * @param matchContext The match context
     * @param remaining The remaing projection string
     * @return The projection method expression
     */
    protected abstract @Nullable ProjectionMethodExpression initProjection(
            @NonNull MethodMatchContext matchContext,
            @Nullable String remaining
    );

    /**
     * Apply the projection to the query object.
     *
     * @param matchContext The match context.
     * @param query The query object.
     */
    protected abstract void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query);

    /**
     * @return The arguments required to satisfy this projection.
     */
    public int getRequiredProperties() {
        return requiredProperties;
    }

    /**
     * @return The expected result type
     */
    public abstract @NonNull ClassElement getExpectedResultType();


    /**
     * The distinct projection creator.
     */
    @SuppressWarnings("unused")
    public static class Distinct extends ProjectionMethodExpression {

        private String property;
        private ClassElement expectedType;

        /**
         * Default constructor.
         */
        public Distinct() {
            super(0);
        }

        @Override
        protected ProjectionMethodExpression initProjection(@NonNull MethodMatchContext matchContext, String remaining) {
            if (StringUtils.isEmpty(remaining)) {
                this.expectedType = matchContext.getRootEntity().getType();
                return this;
            } else {
                this.property = NameUtils.decapitalize(remaining);
                SourcePersistentProperty pp = matchContext.getRootEntity().getPropertyByName(property);
                if (pp == null || pp.getType() == null) {
                    matchContext.fail("Cannot project on non-existent property " + property);
                    return null;
                }
                this.expectedType = pp.getType();
                return this;
            }
        }

        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            if (property == null) {
                query.projections().distinct();
            } else {
                query.projections().distinct(property);
            }
        }

        @NonNull
        @Override
        public ClassElement getExpectedResultType() {
            return expectedType;
        }

    }

    /**
     * Max projection.
     */
    @SuppressWarnings("unused")
    public static class Max extends Property {
        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            query.projections().max(getName());
        }
    }

    /**
     * Sum projection.
     */
    @SuppressWarnings("unused")
    public static class Sum extends Property {
        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            query.projections().sum(getName());
        }

        @Override
        protected ClassElement resolveExpectedType(@NonNull MethodMatchContext matchContext, @NonNull ClassElement classElement) {
            if (TypeUtils.isNumber(classElement)) {
                return matchContext.getVisitorContext().getClassElement(Number.class)
                            .orElse(super.resolveExpectedType(matchContext, classElement));
            } else {
                return super.resolveExpectedType(matchContext, classElement);
            }
        }
    }

    /**
     * Avg projection.
     */
    @SuppressWarnings("unused")
    public static class Avg extends Property {
        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            query.projections().avg(getName());
        }

        @Override
        protected ClassElement resolveExpectedType(@NonNull MethodMatchContext matchContext, @NonNull ClassElement classElement) {
            if (TypeUtils.isNumber(classElement)) {
                return matchContext.getVisitorContext().getClassElement(Double.class)
                        .orElse(super.resolveExpectedType(matchContext, classElement));
            } else {
                return super.resolveExpectedType(matchContext, classElement);
            }
        }
    }


    /**
     * Min projection.
     */
    @SuppressWarnings("unused")
    public static class Min extends Property {
        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            query.projections().min(getName());
        }

        @Override
        protected ProjectionMethodExpression initProjection(@NonNull MethodMatchContext matchContext, String remaining) {
            return super.initProjection(matchContext, remaining);
        }
    }

    /**
     * The property projection.
     */
    public static class Property extends ProjectionMethodExpression implements Named {

        private String property;
        private ClassElement expectedType;

        /**
         * Default constructor.
         */
        public Property() {
            super(1);
        }

        @Override
        protected ProjectionMethodExpression initProjection(@NonNull MethodMatchContext matchContext, String remaining) {
            if (StringUtils.isEmpty(remaining)) {
                matchContext.fail(getClass().getSimpleName() + " projection requires a property name");
                return null;
            } else {

                this.property = NameUtils.decapitalize(remaining);
                SourcePersistentProperty pp = (SourcePersistentProperty) matchContext.getRootEntity().getPropertyByPath(property).orElse(null);
                if (pp == null || pp.getType() == null) {
                    matchContext.fail("Cannot project on non-existent property " + property);
                    return null;
                }
                this.expectedType = resolveExpectedType(matchContext, pp.getType());
                return this;
            }
        }

        /**
         * Resolve the expected type for the projection.
         * @param matchContext The match context
         * @param classElement The class element
         * @return The expected type
         */
        protected ClassElement resolveExpectedType(@NonNull MethodMatchContext matchContext, @NonNull ClassElement classElement) {
            return classElement;
        }

        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            query.projections().property(property);
        }

        @NonNull
        @Override
        public ClassElement getExpectedResultType() {
            return expectedType;
        }

        @NonNull
        @Override
        public String getName() {
            return property;
        }
    }

    /**
     * Restricts the pageSize result.
     */
    private static class RestrictMaxResultProjection extends ProjectionMethodExpression {

        private final Matcher topMatcher;
        private final MethodMatchContext matchContext;
        private int max;

        RestrictMaxResultProjection(Matcher topMatcher, MethodMatchContext matchContext) {
            super(0);
            this.topMatcher = topMatcher;
            this.matchContext = matchContext;
            max = -1;
        }

        @Override
        protected ProjectionMethodExpression initProjection(@NonNull MethodMatchContext matchContext, @Nullable String remaining) {
            String str = topMatcher.group(2);
            try {
                max = StringUtils.isNotEmpty(str) ? Integer.parseInt(str) : 1;
            } catch (NumberFormatException e) {
                matchContext.fail("Invalid number specified to top: " + str);
                return null;
            }
            return this;
        }

        @Override
        protected void apply(@NonNull MethodMatchContext matchContext, @NonNull QueryModel query) {
            if (max > -1) {
                query.max(max);
            }
        }

        @NonNull
        @Override
        public ClassElement getExpectedResultType() {
            return matchContext.getRootEntity().getClassElement();
        }
    }
}
