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
package io.micronaut.data.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * The util class for annotations for micronaut-data module.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Internal
public final class DataAnnotationUtils {

    private DataAnnotationUtils() {
    }

    /**
     * Gets an indicator telling whether annotation metadata is annotated with {@link EntityRepresentation} with JSON column.
     *
     * @param annotationMetadata the annotation metadata
     *
     * @return true if annotation metadata is annotated with {@link EntityRepresentation} with JSON column
     */
    public static boolean hasJsonEntityRepresentationAnnotation(@NonNull AnnotationMetadata annotationMetadata) {
        AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = annotationMetadata.getAnnotation(EntityRepresentation.class);
        if (entityRepresentationAnnotationValue == null) {
            return false;
        }
        EntityRepresentation.Type type = entityRepresentationAnnotationValue.getRequiredValue("type", EntityRepresentation.Type.class);
        if (type != EntityRepresentation.Type.COLUMN) {
            return  false;
        }
        EntityRepresentation.ColumnType columnType = entityRepresentationAnnotationValue.getRequiredValue("columnType", EntityRepresentation.ColumnType.class);
        return columnType == EntityRepresentation.ColumnType.JSON;
    }

}
