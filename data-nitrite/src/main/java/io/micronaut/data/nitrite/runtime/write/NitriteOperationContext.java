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
