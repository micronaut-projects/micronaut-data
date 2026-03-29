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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Declares a field within a compound MongoDB index.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MongoCompoundIndexField {

    /**
     * @return The property path.
     */
    String value();

    /**
     * @return The field direction.
     */
    MongoIndexDirection direction() default MongoIndexDirection.ASC;

    /**
     * @return The geospatial key kind when this field should use a geospatial index key inside a compound index.
     */
    MongoGeoIndexType geoType() default MongoGeoIndexType.GEO_2DSPHERE;

    /**
     * @return Whether the field should use the geospatial key kind instead of the numeric direction.
     */
    boolean geo() default false;

    /**
     * @return The 2d index bits setting, or -1 if unset.
     */
    int bits() default -1;

    /**
     * @return The 2d index minimum value, or NaN if unset.
     */
    double min() default Double.NaN;

    /**
     * @return The 2d index maximum value, or NaN if unset.
     */
    double max() default Double.NaN;
}
