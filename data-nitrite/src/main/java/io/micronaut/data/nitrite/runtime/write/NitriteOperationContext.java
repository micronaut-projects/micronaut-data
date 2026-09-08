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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.runtime.operations.internal.OperationContext;

/**
 * Context for Nitrite operations tracking state during entity operations.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteOperationContext extends OperationContext {

    private static final String MICRONAUT_INSERT = "io.micronaut.data.annotation.Insert";
    private static final String JAKARTA_INSERT = "jakarta.data.repository.Insert";
    private static final String MICRONAUT_SAVE = "io.micronaut.data.annotation.Save";
    private static final String JAKARTA_SAVE = "jakarta.data.repository.Save";

    private final boolean strictInsert;
    private final boolean save;

    /**
     * Creates a context with the operation name available as a fallback. {@code CrudRepository}
     * and {@code BasicRepository} declare {@code save} and {@code insert} without a lifecycle
     * annotation, so an inherited call carries no {@code @Save} or {@code @Insert} to read.
     *
     * @param annotationMetadata the annotation metadata
     * @param repositoryType the repository type
     * @param operationName the operation name, or {@code null}
     */
    public NitriteOperationContext(AnnotationMetadata annotationMetadata,
                                   Class<?> repositoryType,
                                   @Nullable String operationName) {
        super(annotationMetadata, repositoryType);
        this.strictInsert = annotationMetadata.hasAnnotation(MICRONAUT_INSERT)
            || annotationMetadata.hasAnnotation(JAKARTA_INSERT)
            || isInheritedInsertOperation(operationName);
        this.save = annotationMetadata.hasAnnotation(MICRONAUT_SAVE)
            || annotationMetadata.hasAnnotation(JAKARTA_SAVE)
            || isInheritedSaveOperation(operationName);
    }

    /**
     * Returns whether this operation came from an explicit insert method.
     *
     * @return {@code true} if the operation originated from an explicit insert method
     */
    public boolean isStrictInsert() {
        return strictInsert;
    }

    /**
     * Returns whether this operation originated from a Jakarta Data/Micronaut Data save method.
     *
     * <p>Save is the one conditional lifecycle operation: Jakarta Data defines it as an update when
     * the store already holds the identity and an insert when it does not. Insert, update and
     * delete are each unconditional, so only save has to look the identity up before it can tell
     * which lifecycle it is running.
     *
     * @return {@code true} for a conditional save operation
     */
    public boolean isSave() {
        return save;
    }

    private static boolean isInheritedSaveOperation(@Nullable String operationName) {
        String methodName = methodNameOf(operationName);
        return "save".equals(methodName) || "saveAll".equals(methodName);
    }

    private static boolean isInheritedInsertOperation(@Nullable String operationName) {
        String methodName = methodNameOf(operationName);
        return "insert".equals(methodName) || "insertAll".equals(methodName);
    }

    /**
     * The method half of an operation name. {@code EntityOperation.getName} reports
     * {@code DeclaringType.methodName}, and the declaring type is the repository interface that
     * declares the method - {@code CrudRepository} or {@code BasicRepository} for an inherited
     * method, but the application's own repository when it overrides one. Only the method name
     * carries the semantics, so only the method name is matched.
     *
     * @param operationName the operation name, or {@code null}
     * @return the method name, or {@code null}
     */
    private static @Nullable String methodNameOf(@Nullable String operationName) {
        if (operationName == null) {
            return null;
        }
        int separator = operationName.lastIndexOf('.');
        return separator < 0 ? operationName : operationName.substring(separator + 1);
    }
}
