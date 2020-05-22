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
package io.micronaut.data.intercept.annotation;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.model.DataType;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal annotation used to configure execution handling for {@link io.micronaut.data.intercept.DataIntroductionAdvice}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Internal
public @interface DataMethod {

    /**
     * The member that holds the count query.
     */
    String META_MEMBER_COUNT_QUERY = "countQuery";

    /**
     * The member that holds the count parameters.
     */
    String META_MEMBER_COUNT_PARAMETERS = "countParameters";

    /**
     * The member name that holds the result type.
     */
    String META_MEMBER_RESULT_TYPE = "resultType";

    /**
     * The member name that holds the result type.
     */
    String META_MEMBER_RESULT_DATA_TYPE = "resultDataType";

    /**
     * The member name that holds the root entity type.
     */
    String META_MEMBER_ROOT_ENTITY = "rootEntity";

    /**
     * The member name that holds the interceptor type.
     */
    String META_MEMBER_INTERCEPTOR = "interceptor";

    /**
     * The member name that holds parameter binding.
     */
    String META_MEMBER_PARAMETER_BINDING = "parameterBinding";

    /**
     * The member name that holds parameter binding paths.
     */
    String META_MEMBER_PARAMETER_BINDING_PATHS = META_MEMBER_PARAMETER_BINDING + "Paths";

    /**
     * The ID type.
     */
    String META_MEMBER_ID_TYPE = "idType";

    /**
     * The parameter that holds the pageSize value.
     */
    String META_MEMBER_PAGE_SIZE = "pageSize";

    /**
     * The parameter that holds the offset value.
     */
    String META_MEMBER_PAGE_INDEX = "pageIndex";

    /**
     * The parameter that references the entity.
     */
    String META_MEMBER_ENTITY = "entity";

    /**
     * The parameter that references the ID.
     */
    String META_MEMBER_ID = "id";

    /**
     * Does the query result in a DTO object.
     */
    String META_MEMBER_DTO = "dto";

    /**
     * The query builder to use.
     */
    String META_MEMBER_QUERY_BUILDER = "queryBuilder";

    /**
     * Whether the user is a raw user specified query.
     */
    String META_MEMBER_RAW_QUERY = "rawQuery";
    /**
     * Meta member for storing the parameter type defs.
     */
    String META_MEMBER_PARAMETER_TYPE_DEFS = "parameterTypeDefs";
    /**
     * @return The child interceptor to use for the method execution.
     */
    Class<? extends DataInterceptor> interceptor();

    /**
     * The root entity this method applies to.
     * @return The root entity
     */
    Class<?> rootEntity() default void.class;

    /**
     * The computed result type. This represents the type that is to be read from the database. For example for a {@link java.util.List}
     * this would return the value of the generic type parameter {@code E}. Or for an entity result the return type itself.
     *
     * @return The result type
     */
    Class<?> resultType() default void.class;

    /**
     * @return The result data type.
     */
    DataType resultDataType() default DataType.OBJECT;

    /**
     * The identifier type for the method being executed.
     *
     * @return The ID type
     */
    Class<?> idType() default Serializable.class;

    /**
     * The parameter binding defines which method arguments bind to which
     * query parameters. The {@link Property#name()} is used to define the query parameter name and the
     * {@link Property#value()} is used to define method argument name to bind.
     *
     * @return The parameter binding.
     */
    Property[] parameterBinding() default {};

    /**
     * The argument that defines the pageable object.
     *
     * @return The pageable.
     */
    String pageable() default "";

    /**
     * The argument that represents the entity for save, update, query by example operations etc.
     *
     * @return The entity argument
     */
    String entity() default "";

    /**
     * The member that defines the ID for lookup, delete, update by ID.
     * @return The ID
     */
    String id() default "";

    /**
     * An explicit pageSize (in absence of a pageable).
     * @return The pageSize
     */
    int pageSize() default -1;

    /**
     * An explicit offset (in absence of a pageable).
     *
     * @return The offset
     */
    long pageIndex() default 0;
}
