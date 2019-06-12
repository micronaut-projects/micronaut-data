package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;

import java.util.Optional;

/**
 * Abstract version of the {@link PersistentEntity} interface.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractPersistentEntity implements PersistentEntity {

    private final AnnotationMetadataProvider annotationMetadataProvider;
    private final NamingStrategy namingStrategy;

    /**
     * Default constructor.
     * @param annotationMetadataProvider The annotation metadata provider.
     */
    protected AbstractPersistentEntity(AnnotationMetadataProvider annotationMetadataProvider) {
        this.annotationMetadataProvider = annotationMetadataProvider;
        this.namingStrategy = annotationMetadataProvider.getAnnotationMetadata()
                .classValue(MappedEntity.class, "namingStrategy")
                .flatMap(aClass -> {
                    @SuppressWarnings("unchecked")
                    Object o = InstantiationUtils.tryInstantiate(aClass).orElse(null);
                    if (o instanceof NamingStrategy) {
                        return Optional.of((NamingStrategy) o);
                    }
                    return Optional.empty();
                }).orElse(new NamingStrategies.UnderScoreSeparatedLowerCase());
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadataProvider.getAnnotationMetadata();
    }

    /**
     * Obtain the naming strategy for the entity.
     * @return The naming strategy
     */
    public @NonNull NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    @NonNull
    @Override
    public String getPersistedName() {
        return namingStrategy.mappedName(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().isInstance(o)) {
            return false;
        }
        AbstractPersistentEntity that = (AbstractPersistentEntity) o;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
