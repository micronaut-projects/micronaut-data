package io.micronaut.data.runtime.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.impl.DefaultPersistentPropertyPath;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;

import java.util.List;

/**
 * The runtime property path.
 *
 * @param <I> The entity type
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class RuntimePersistentPropertyPathImpl<I, T> extends DefaultPersistentPropertyPath<T> {

    private final Path<?> parentPath;
    private final RuntimePersistentProperty<I> runtimePersistentProperty;

    RuntimePersistentPropertyPathImpl(Path<?> parentPath,
                                             List<Association> path,
                                             RuntimePersistentProperty<I> persistentProperty,
                                             CriteriaBuilder criteriaBuilder) {
        super(persistentProperty, path, criteriaBuilder);
        this.parentPath = parentPath;
        this.runtimePersistentProperty = persistentProperty;
    }

    @Override
    public Path<?> getParentPath() {
        return parentPath;
    }

    @Override
    public <Y> PersistentPropertyPath<Y> get(String attributeName) {
        if (runtimePersistentProperty instanceof RuntimeAssociation<?> association) {
            AbstractPersistentEntityFrom<?, ?> from = (AbstractPersistentEntityFrom<?, ?>) parentPath;
            PersistentAssociationPath<?, ?> join = from.join(association.getName());
            return join.get(attributeName);
        }
        return super.get(attributeName);
    }

    @Override
    public Class<? extends T> getJavaType() {
        return (Class<? extends T>) runtimePersistentProperty.getType();
    }

    @Override
    public String toString() {
        return "RuntimePersistentPropertyPath{" +
            "runtimePersistentProperty=" + runtimePersistentProperty +
            '}';
    }
}
