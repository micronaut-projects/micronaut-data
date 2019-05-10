package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * A parameter role indicates a method parameter in a repository play a role in query execution and should
 * not be factored into query calculation but instead made available at runtime using the specified role name.
 *
 * <p>This is used for example to configure a {@link io.micronaut.data.model.Pageable} object to be handled differently
 * to other query arguments.</p>
 *
 * <p>The parameter names of each role can be resolved from the {@link io.micronaut.aop.MethodInvocationContext} as a member of the
 * {@link io.micronaut.data.intercept.annotation.PredatorMethod} annotation where the member name is the role name.</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface ParameterRole {
    /**
     * The parameter that is used for pagination.
     */
    String PAGEABLE = "pageable";

    /**
     * The parameter that is used for sorting.
     */
    String SORT = "sort";

    /**
     * The parameter that is used for the ID of entity.
     */
    String ID = "id";

    /**
     * The parameter that defines an instance of the entity.
     */
    String ENTITY = "entity";

    /**
     * The last updated property of the entity for an update operation.
     */
    String LAST_UPDATED_PROPERTY = "lastUpdatedProperty";

    /**
     * The name of the role.
     * @return The role name
     */
    String role();

    /**
     * The parameter type.
     * @return The parameter type
     */
    Class<?> type();
}
