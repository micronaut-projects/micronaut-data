/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.connection.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repeatable annotation for {@link ClientInfo.Attribute}.
 *
 * @author radovanradic
 * @since 4.11
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientInfo {

    /**
     * Returns the list of the client information attributes.
     *
     * @return the attribute collection
     */
    Attribute[] value() default {};

    /**
     * An annotation used to set client info for the connection.
     *
     * @author radovanradic
     * @since 4.11
     */
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(ClientInfo.class)
    @interface Attribute {

        /**
         * Returns the name of the client information attribute.
         *
         * @return the attribute name
         */
        String name();

        /**
         * Returns the value of the client information attribute.
         *
         * @return the attribute value
         */
        String value();
    }
}
