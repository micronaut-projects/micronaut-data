/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.aws.dynamodb.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field annotation indicating that entity field should be used as index sort key - part of the secondary index.
 * Can be used for global or local secondary index.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface IndexSortKey {

    /**
     * The name of the index sort key if the serialized attribute name differs from the field name.
     *
     * @return partition key name
     */
    String value() default "";

    /**
     * List of Global Secondary Index names where field is used. One field can be used in one or more global secondary indexes.
     *
     * @return the Global Secondary Index names
     */
    String[] globalSecondaryIndexNames() default {};

    /**
     * List of Local Secondary Index names where field is used. One field can be used in one or more local secondary indexes.
     *
     * @return the Local Secondary Index names
     */
    String[] localSecondaryIndexNames() default {};
}
