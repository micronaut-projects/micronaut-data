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
package io.micronaut.data.mongodb.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a compound MongoDB index for an entity.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
@Inherited
@Repeatable(MongoCompoundIndexes.class)
public @interface MongoCompoundIndex {

    /**
     * @return The index name.
     */
    String name() default "";

    /**
     * @return The fields.
     */
    MongoCompoundIndexField[] fields();

    /**
     * @return Whether the index is unique.
     */
    boolean unique() default false;

    /**
     * @return Whether the index is sparse.
     */
    boolean sparse() default false;

    /**
     * @return Whether the index is hidden.
     */
    boolean hidden() default false;

    /**
     * @return The index expiration in seconds.
     */
    int expireAfterSeconds() default -1;

    /**
     * @return The partial filter expression as JSON.
     */
    String partialFilterExpression() default "";

    /**
     * @return The collation definition as JSON.
     */
    String collation() default "";

    /**
     * @return The index creation command comment.
     */
    String comment() default "";

    /**
     * @return The createIndexes commit quorum.
     */
    String commitQuorum() default "";
}
