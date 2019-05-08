package io.micronaut.data.model.runtime;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.inject.ast.PropertyElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runtime implementation of {@link PersistentEntity} that uses pre-computed {@link io.micronaut.core.annotation.Introspected} bean data and is completely stateless.
 *
 * @author graemerocher
 * @since 1.0
 */
public class RuntimePersistentEntity implements PersistentEntity {

    private final BeanIntrospection<?> introspection;

    public RuntimePersistentEntity(@Nonnull Class<?> type) {
        this(BeanIntrospection.getIntrospection(type));
    }

    public RuntimePersistentEntity(@Nonnull BeanIntrospection<?> introspection) {
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.introspection = introspection;
    }

    @Nonnull
    @Override
    public String getName() {
        return introspection.getBeanType().getName();
    }

    @Nullable
    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return new PersistentProperty[0];
    }

    @Nullable
    @Override
    public PersistentProperty getIdentity() {
        return introspection.getIndexedProperty(Id.class).map(bp ->
                new RuntimePersistentProperty(this, bp)
        ).orElse(null);
    }

    @Nullable
    @Override
    public PersistentProperty getVersion() {
        return introspection.getIndexedProperty(Version.class).map(bp ->
                new RuntimePersistentProperty(this, bp)
        ).orElse(null);
    }

    @Nonnull
    @Override
    public List<PersistentProperty> getPersistentProperties() {
        return introspection.getBeanProperties()
                .stream()
                .filter((bp) -> bp.isReadWrite() && !(bp.hasStereotype(Id.class, Version.class)))
                .map(bp -> new RuntimePersistentProperty(this, bp))
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<Association> getAssociations() {
        return introspection.getBeanProperties().stream()
                .filter(bp -> bp.hasStereotype(Relation.class))
                .map(propertyElement -> new RuntimeAssociation(this, propertyElement))
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<Embedded> getEmbedded() {
        return introspection.getBeanProperties().stream()
                .filter(this::isEmbedded)
                .map(propertyElement -> new RuntimeEmbedded(this, propertyElement))
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public PersistentProperty getPropertyByName(String name) {
        return introspection.getProperty(name).map(bp -> {
            if (bp.hasStereotype(Relation.class)) {
                if (isEmbedded(bp)) {
                    return new RuntimeEmbedded(this, bp);
                } else {
                    return new RuntimeAssociation(this, bp);
                }
            } else {
                return new RuntimePersistentProperty(this, bp);
            }
        }).orElse(null);
    }

    @Nonnull
    @Override
    public List<String> getPersistentPropertyNames() {
        return Arrays.asList(introspection.getPropertyNames());
    }

    @Override
    public boolean isOwningEntity(PersistentEntity owner) {
        return true;
    }

    @Nullable
    @Override
    public PersistentEntity getParentEntity() {
        return null;
    }

    private boolean isEmbedded(BeanProperty bp) {
        return bp.hasStereotype(Relation.class) && bp.getValue(Relation.class, "kind", Relation.Kind.class).orElse(null) == Relation.Kind.EMBEDDED;
    }
}
