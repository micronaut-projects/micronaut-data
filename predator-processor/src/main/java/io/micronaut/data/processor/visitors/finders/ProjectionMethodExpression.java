package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.ClassElement;

import javax.annotation.Nonnull;


/**
 * Method expression for performing a projection of some sort.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class ProjectionMethodExpression {

    private final int requiredProperties;

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
        SourcePersistentEntity entity = matchContext.getEntity();
        String decapitilized = NameUtils.decapitalize(projection);
        SourcePersistentProperty projectedProperty = entity.getPropertyByName(decapitilized);
        if (projectedProperty != null) {
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

    protected abstract ProjectionMethodExpression initProjection(@NonNull MethodMatchContext matchContext, @Nullable String remaining);

    /**
     * Apply the projection to the query object.
     *
     * @param matchContext The match context.
     * @param query The query object.
     * @return If the projection couldn't be applied because an error occurred.
     */
    protected abstract void apply(@NonNull MethodMatchContext matchContext, @NonNull Query query);

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
     * The distinct projection creator
     */
    public static class Distinct extends ProjectionMethodExpression {

        private String property;
        private ClassElement expectedType;

        public Distinct() {
            super(0);
        }

        @Override
        protected ProjectionMethodExpression initProjection(@NonNull MethodMatchContext matchContext, String remaining) {
            if (StringUtils.isEmpty(remaining)) {
                this.expectedType = matchContext.getEntity().getType();
                return this;
            } else {
                this.property = NameUtils.decapitalize(remaining);
                SourcePersistentProperty pp = matchContext.getEntity().getPropertyByName(property);
                if (pp == null || pp.getType() == null) {
                    matchContext.fail("Cannot project on non-existent property " + property);
                    return null;
                }
                this.expectedType = pp.getType();
                return this;
            }
        }

        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull Query query) {
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
    public static class Max extends Property {
        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull Query query) {
            query.projections().max(getName());
        }

        @NonNull
        @Override
        protected ClassElement resolveExpectedType(@NonNull MethodMatchContext matchContext, @NonNull ClassElement type) {
            return matchContext.getVisitorContext().getClassElement(Number.class).orElse(super.resolveExpectedType(matchContext, type));
        }
    }

    /**
     * Min projection.
     */
    public static class Min extends Property {
        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull Query query) {
            query.projections().min(getName());
        }

        @NonNull
        @Override
        protected ClassElement resolveExpectedType(@NonNull MethodMatchContext matchContext, @NonNull ClassElement type) {
            return matchContext.getVisitorContext().getClassElement(Number.class).orElse(super.resolveExpectedType(matchContext, type));
        }
    }

    /**
     * The property projection.
     */
    public static class Property extends ProjectionMethodExpression implements Named {

        private String property;
        private ClassElement expectedType;

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
                SourcePersistentProperty pp = matchContext.getEntity().getPropertyByName(property);
                if (pp == null || pp.getType() == null) {
                    matchContext.fail("Cannot project on non-existent property " + property);
                    return null;
                }
                this.expectedType = resolveExpectedType(matchContext, pp.getType());
                return this;
            }
        }

        protected @NonNull ClassElement resolveExpectedType(@NonNull MethodMatchContext matchContext, @NonNull ClassElement type) {
            return type;
        }

        @Override
        public void apply(@NonNull MethodMatchContext matchContext, @NonNull Query query) {
            query.projections().property(property);
        }

        @NonNull
        @Override
        public ClassElement getExpectedResultType() {
            return expectedType;
        }

        @Nonnull
        @Override
        public String getName() {
            return property;
        }
    }

}
