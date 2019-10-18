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
import java.util.stream.Stream;

/**
 * Used to represent an annotation metadata hierarchy.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public final class AnnotationMetadataHierarchy implements AnnotationMetadata {
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
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> Stream.of(am.stringValues(annotation, member)))
                .toArray(String[]::new);
    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Boolean> o = annotationMetadata.booleanValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
        return Arrays.stream(hierarchy).anyMatch(am -> am.isTrue(annotation, member));
    }

    @Override
    public OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalLong o = annotationMetadata.longValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<String> o = annotationMetadata.stringValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalInt o = annotationMetadata.intValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalInt.empty();
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final OptionalDouble o = annotationMetadata.doubleValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<E> o = annotationMetadata.enumValue(annotation, member, enumType);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Class<T>[] classValues(@Nonnull String annotation, @Nonnull String member) {
        final Class[] classes = Arrays.stream(hierarchy)
                .flatMap(am -> Stream.of(am.classValues(annotation, member)))
                .toArray(Class[]::new);
        return (Class<T>[]) classes;
    }

    @Override
    public Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            final Optional<Class> o = annotationMetadata.classValue(annotation, member);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Arrays.stream(hierarchy)
                .flatMap(am -> am.getDeclaredAnnotationNamesByStereotype(stereotype).stream())
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
                .flatMap(am -> am.getDeclaredAnnotationNames().stream())
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
                .flatMap(am -> am.getDeclaredAnnotationValuesByType(annotationType).stream())
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
            if (annotationMetadata.hasDeclaredAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        for (AnnotationMetadata annotationMetadata : hierarchy) {
            if (annotationMetadata.hasDeclaredStereotype(annotation)) {
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
