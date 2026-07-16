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
package io.micronaut.data.annotation.sql;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation to indicate that the annotated mapped property participates
 * in the computation of an entity {@link GeneratedETag}.
 * <p>
 * Fields or properties annotated with {@code @ETagValue} will be gathered by the
 * annotation processing step to build the read expression of a {@link ColumnTransformer}
 * on the property annotated with {@link GeneratedETag}.
 * <p>
 * The processor resolves the persisted/column names of the marked properties, so
 * fields within embedded types are handled according to the configured naming strategy.
 *
 * <p>Usage: mark properties participating in the ETag with {@literal @}ETagValue, and annotate the target ETag
 * property with {@link GeneratedETag}. The processor derives the SQL read expression accordingly.</p>
 *
 * @author radovanradic
 * @since 5.1
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface ETagValue {

    /**
     * If true, the annotated field is excluded from ETag computation.
     *
     * @return True if field should be excluded
     */
    boolean exclude() default false;
}
