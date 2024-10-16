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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal annotation used describe the stored query definition.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Internal
@Inherited
public @interface DataMethodQuery {

    /**
     * The annotation name.
     */
    String NAME = DataMethodQuery.class.getName();

    /**
     * The member that holds the query value.
     */
    String META_MEMBER_QUERY = "query";

    /**
     * The member that holds the native query value.
     */
    String META_MEMBER_NATIVE = "nativeQuery";

    /**
     * Whether the user is a raw user specified query.
     */
    String META_MEMBER_RAW_QUERY = "rawQuery";

    /**
     * The member that holds the is procedure value.
     */
    String META_MEMBER_PROCEDURE = "procedure";

    /**
     * The member that holds expandable query parts.
     */
    String META_MEMBER_EXPANDABLE_QUERY = "expandableQuery";

    /**
     * The member name that holds the result type.
     */
    String META_MEMBER_RESULT_TYPE = "resultType";

    /**
     * The member name that holds the result type.
     */
    String META_MEMBER_RESULT_DATA_TYPE = "resultDataType";

    /**
     * The parameter that holds the offset value.
     */
    String META_MEMBER_OFFSET = "offset";

    /**
     * The parameter that holds the limit value.
     */
    String META_MEMBER_LIMIT = "limit";

    /**
     * Does the query result in a DTO object.
     */
    String META_MEMBER_DTO = "dto";

    /**
     * Does the query contains optimistic lock.
     */
    String META_MEMBER_OPTIMISTIC_LOCK = "optimisticLock";

    /**
     * Meta member for storing the parameters.
     */
    String META_MEMBER_PARAMETERS = "parameters";

    /**
     * The member name that holds the root entity type.
     */
    String META_MEMBER_OPERATION_TYPE = "opType";

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
     * @return The query parameters
     */
    DataMethodQueryParameter[] parameters() default {};

    /**
     * @return True if the method represents the procedure invocation.
     * @since 4.2.0
     */
    boolean procedure() default false;

    /**
     * Describes the operation type.
     */
    enum OperationType {
        /**
         * A query operation.
         */
        QUERY,
        /**
         * A count operation.
         */
        COUNT,
        /**
         * An exists operation.
         */
        EXISTS,
        /**
         * An update operation.
         */
        UPDATE,
        /**
         * An update returning operation.
         */
        UPDATE_RETURNING,
        /**
         * A delete operation.
         */
        DELETE,
        /**
         * An delete returning operation.
         */
        DELETE_RETURNING,
        /**
         * An insert operation.
         */
        INSERT,
        /**
         * An insert returning operation.
         */
        INSERT_RETURNING,
    }
}
