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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A type role indicates a method element in a repository that plays a role in query execution and should
 * not be factored into query calculation but instead made available at runtime using the specified role name.
 *
 * <p>This is used for example to configure a {@link io.micronaut.data.model.Pageable} object to be handled differently
 * to other query arguments.</p>
 *
 * <p>The parameter names of each role can be resolved from the {@link io.micronaut.aop.MethodInvocationContext} as a member of the
 * {@link io.micronaut.data.intercept.annotation.DataMethod} annotation where the member name is the role name.</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Documented
@Inherited
public @interface TypeRole {
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
     * The parameter that defines an iterable of the entity instances.
     */
    String ENTITIES = "entities";

    /**
     * The parameter that is used to represent a {@link io.micronaut.data.model.Slice}.
     */
    String SLICE = "slice";

    /**
     * The parameter that is used to represent a {@link io.micronaut.data.model.Page}.
     */
    String PAGE = "page";

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
