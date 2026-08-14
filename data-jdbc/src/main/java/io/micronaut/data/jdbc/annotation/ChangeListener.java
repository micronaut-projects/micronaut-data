/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives database change events for a persistent entity.
 *
 * <p>The method must return {@code void} and accept exactly one
 * {@link io.micronaut.data.jdbc.notification.ChangeEvent ChangeEvent}{@code <E>} argument, where
 * {@code E} is a {@code @MappedEntity}. The available operation, entity state, metadata, ordering,
 * and delivery guarantees depend on the notification provider selected for the datasource.</p>
 *
 * @since 5.2.0
 */
@Documented
@Executable(processOnStartup = true)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface ChangeListener {

    /**
     * @return The datasource that supplies database change notifications.
     */
    @AliasFor(member = "dataSource")
    String value() default "default";

    /**
     * @return The datasource that supplies database change notifications.
     */
    @AliasFor(member = "value")
    String dataSource() default "default";

}
