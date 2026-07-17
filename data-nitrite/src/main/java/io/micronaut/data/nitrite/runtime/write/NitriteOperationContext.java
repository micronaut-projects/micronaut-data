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
package io.micronaut.data.nitrite.runtime.write;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.runtime.operations.internal.OperationContext;

/**
 * Context for Nitrite operations tracking state during entity operations.
 *
 * @since 4.14.0
 */
@Internal
public final class NitriteOperationContext extends OperationContext {

    private static final String MICRONAUT_INSERT = "io.micronaut.data.annotation.Insert";
    private static final String JAKARTA_INSERT = "jakarta.data.repository.Insert";

    private final boolean strictInsert;

    /**
     * Creates a new NitriteOperationContext.
     *
     * @param annotationMetadata the annotation metadata
     * @param repositoryType the repository type
     */
    public NitriteOperationContext(AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
        super(annotationMetadata, repositoryType);
        this.strictInsert = annotationMetadata.hasAnnotation(MICRONAUT_INSERT)
            || annotationMetadata.hasAnnotation(JAKARTA_INSERT);
    }

    /**
     * Returns whether this operation came from an explicit insert method.
     *
     * @return {@code true} if the operation originated from an explicit insert method
     */
    public boolean isStrictInsert() {
        return strictInsert;
    }
}
