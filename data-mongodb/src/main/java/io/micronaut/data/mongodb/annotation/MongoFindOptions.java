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
package io.micronaut.data.mongodb.annotation;

import com.mongodb.CursorType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a custom MongoDB find query options.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
@Documented
@Inherited
public @interface MongoFindOptions {

    /**
     * The batchSize.
     *
     * @return The batchSize value
     */
    int batchSize() default -1;

    /**
     * The skip.
     *
     * @return The skip value
     */
    int skip() default -1;

    /**
     * The limit.
     *
     * @return The limit value
     */
    int limit() default -1;

    /**
     * The maxTimeMS.
     *
     * @return The maxTimeMS value
     */
    long maxTimeMS() default -1;

    /**
     * The maxAwaitTimeMS.
     *
     * @return The maxAwaitTimeMS value
     */
    long maxAwaitTimeMS() default -1;

    /**
     * The cursorType.
     *
     * @return The cursorType value
     */
    CursorType cursorType() default CursorType.NonTailable;

    /**
     * The cursorType.
     *
     * @return The cursorType value
     */
    boolean noCursorTimeout() default false;

    /**
     * The partial.
     *
     * @return The partial value
     */
    boolean partial() default false;

    /**
     * The comment.
     *
     * @return The comment value
     */
    String comment() default "";

    /**
     * The hint.
     *
     * @return The hint value
     */
    String hint() default "";

    /**
     * The max.
     *
     * @return The max value
     */
    String max() default "";

    /**
     * The min.
     *
     * @return The min value
     */
    String min() default "";

    /**
     * The returnKey.
     *
     * @return The returnKey value
     */
    boolean returnKey() default false;

    /**
     * The showRecordId.
     *
     * @return The showRecordId value
     */
    boolean showRecordId() default false;

    /**
     * The allowDiskUse.
     *
     * @return The allowDiskUse value
     */
    boolean allowDiskUse() default false;
}
