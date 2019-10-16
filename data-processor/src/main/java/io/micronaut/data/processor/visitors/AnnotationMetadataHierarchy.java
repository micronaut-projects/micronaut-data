package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Used to represent an annotation metadata hierarchy.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class AnnotationMetadataHierarchy implements AnnotationMetadata {
    private final AnnotationMetadata[] hierarchy;

    /**
     * Default constructor.
     *
     * @param hierarchy The annotation hierarchy
     */
    public AnnotationMetadataHierarchy(AnnotationMetadata... hierarchy) {
        if (ArrayUtils.isNotEmpty(hierarchy)) {
            // place the first in the hierarchy first
            final List<AnnotationMetadata> list = Arrays.asList(hierarchy);
            Collections.reverse(list);
            this.hierarchy = list.toArray(new AnnotationMetadata[0]);
        } else {
            this.hierarchy = new AnnotationMetadata[] { AnnotationMetadata.EMPTY_METADATA };
        }
    }

    @Nonnull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getAnnotationNamesByStereotype(stereotype).stream())
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public Set<String> getDeclaredAnnotationNames() {
        return hierarchy[0].getDeclaredAnnotationNames();
    }

    @Nonnull
    @Override
    public Set<String> getAnnotationNames() {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getAnnotationNames().stream())
                .collect(Collectors.toSet());
    }

    @Nonnull
    @Override
    public <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalValues<T> values = annotationMetadata.getValues(annotation, valueType);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return OptionalValues.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<T> defaultValue = annotationMetadata.getDefaultValue(annotation, member, requiredType);
            if (defaultValue.isPresent()) {
                return defaultValue;
            }
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getAnnotationValuesByType(annotationType).stream())
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return hierarchy[0].getDeclaredAnnotationValuesByType(annotationType);
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return hierarchy[0].hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        return hierarchy[0].hasDeclaredStereotype(annotation);
    }

    @Nonnull
    @Override
    public Map<String, Object> getDefaultValues(@Nonnull String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Map<String, Object> defaultValues = annotationMetadata.getDefaultValues(annotation);
            if (!defaultValues.isEmpty()) {
                return defaultValues;
            }
        }
        return Collections.emptyMap();
    }
}
