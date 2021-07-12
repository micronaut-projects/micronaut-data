/*
 * Copyright 2017-2021 original authors
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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates one of the indexes part of the indexes member within an Table annotation. Typically not used
 * directly but instead mapped to via annotation such as {@code javax.persistence.Index}.
 *
 * @author Davide Pugliese
 * @since 2.4
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Repeatable(value = Indexes.class)
@Inherited
public @interface Index {
    /**
     * (Optional) The name of the index; defaults to a provider-generated name.
     *
     * @return The name of the index
     */
    String name() default "";


    /**
     * (Required) The list of columns to be used to create an index.
     *
     * @return The list of columns
     */
    String[] columns();

    /**
     *
     * @return (Optional) Whether the index is unique
     */
    boolean unique() default false;

}
