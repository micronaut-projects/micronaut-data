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
package io.micronaut.data.mongodb.annotation.index;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a simple MongoDB geospatial index for a property.
 *
 * @author radovanradic
 * @since 5.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Documented
@Inherited
public @interface MongoGeoIndexed {

    /**
     * @return The index name.
     */
    String name() default "";

    /**
     * @return The geospatial index kind. Defaults to {@link MongoGeoIndexType#GEO_2DSPHERE}.
     */
    MongoGeoIndexType type() default MongoGeoIndexType.GEO_2DSPHERE;

    /**
     * @return Whether the index is hidden.
     */
    boolean hidden() default false;

    /**
     * @return The index creation command comment.
     */
    String comment() default "";

    /**
     * @return The storage engine options as JSON.
     */
    String storageEngine() default "";

    /**
     * @return The partial filter expression as JSON.
     */
    String partialFilterExpression() default "";

    /**
     * @return The collation definition as JSON.
     */
    String collation() default "";

    /**
     * @return The 2dsphere index version, or {@code -1} if unset. Only valid for
     * {@link MongoGeoIndexType#GEO_2DSPHERE}.
     */
    int sphereVersion() default -1;

    /**
     * @return The 2d index bits setting, or {@code -1} if unset. Only valid for
     * {@link MongoGeoIndexType#GEO_2D}. Valid range is 1..32 inclusive. MongoDB default is 26.
     */
    int bits() default -1;

    /**
     * @return The 2d index minimum value, or {@link Double#NaN} if unset. Only valid for
     * {@link MongoGeoIndexType#GEO_2D}. Represents the lower inclusive boundary. MongoDB default is -180.0.
     */
    double min() default Double.NaN;

    /**
     * @return The 2d index maximum value, or {@link Double#NaN} if unset. Only valid for
     * {@link MongoGeoIndexType#GEO_2D}. Represents the upper inclusive boundary. MongoDB default is 180.0.
     */
    double max() default Double.NaN;

    /**
     * @return The createIndexes commit quorum.
     */
    String commitQuorum() default "";
}
