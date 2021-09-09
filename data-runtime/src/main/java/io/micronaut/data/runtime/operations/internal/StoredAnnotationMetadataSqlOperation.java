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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.query.builder.sql.Dialect;

/**
 * Implementation of {@link StoredSqlOperation} that retrieves data from {@link AnnotationMetadata}.
 */
@Internal
public class StoredAnnotationMetadataSqlOperation extends StoredSqlOperation {

    /**
     * Creates a new instance.
     *
     * @param dialect            The dialect
     * @param annotationMetadata The annotation metadata
     */
    public StoredAnnotationMetadataSqlOperation(Dialect dialect, AnnotationMetadata annotationMetadata) {
        super(dialect,
                annotationMetadata.stringValue(Query.class, "rawQuery")
                        .orElseGet(() -> annotationMetadata.stringValue(Query.class).orElse(null)),
                annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS),
                annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS),
                annotationMetadata.booleanValue(DataMethod.class, DataMethod.META_MEMBER_OPTIMISTIC_LOCK).orElse(false)
        );
    }

}
