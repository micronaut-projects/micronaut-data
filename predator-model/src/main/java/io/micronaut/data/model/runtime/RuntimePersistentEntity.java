/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runtime implementation of {@link PersistentEntity} that uses pre-computed {@link io.micronaut.core.annotation.Introspected} bean data and is completely stateless.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The type
 */
public class RuntimePersistentEntity<T> extends AbstractPersistentEntity implements PersistentEntity {

    private final BeanIntrospection<T> introspection;
    private final RuntimePersistentProperty<T> identity;
    private RuntimePersistentProperty<T> version;

    /**
     * Default constructor.
     * @param type The type
     */
    public RuntimePersistentEntity(@NonNull Class<T> type) {
        this(BeanIntrospection.getIntrospection(type));
    }

    /**
     * Default constructor.
     * @param introspection The introspection
     */
    public RuntimePersistentEntity(@NonNull BeanIntrospection<T> introspection) {
        super(introspection);
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.introspection = introspection;
        identity = introspection.getIndexedProperty(Id.class).map(bp ->
                new RuntimePersistentProperty<>(this, bp)
        ).orElse(null);
        version = introspection.getIndexedProperty(Version.class).map(bp ->
                new RuntimePersistentProperty<>(this, bp)
        ).orElse(null);
    }

    /**
     * @return The underlying introspection.
     */
    public BeanIntrospection<T> getIntrospection() {
        return introspection;
    }

    @NonNull
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
    public RuntimePersistentProperty<T> getIdentity() {
        return identity;
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getVersion() {
        return version;
    }

    @NonNull
    @Override
    public List<PersistentProperty> getPersistentProperties() {
        return introspection.getBeanProperties()
                .stream()
                .filter((bp) -> bp.isReadWrite() && !(bp.hasStereotype(Id.class, Version.class)))
                .map(bp -> {
                    if (bp.hasAnnotation(Relation.class)) {
                        if (isEmbedded(bp)) {
                            return new RuntimeEmbedded<>(this, bp);
                        } else {
                            return new RuntimeAssociation<>(this, bp);
                        }
                    } else {
                        return new RuntimePersistentProperty<>(this, bp);
                    }
                })
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<Association> getAssociations() {
        return introspection.getBeanProperties().stream()
                .filter(bp -> bp.hasStereotype(Relation.class))
                .map(propertyElement -> new RuntimeAssociation<>(this, propertyElement))
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<Embedded> getEmbedded() {
        return introspection.getBeanProperties().stream()
                .filter(this::isEmbedded)
                .map(propertyElement -> new RuntimeEmbedded<>(this, propertyElement))
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getPropertyByName(String name) {
        return introspection.getProperty(name).map(bp -> {
            if (bp.hasStereotype(Relation.class)) {
                if (isEmbedded(bp)) {
                    return new RuntimeEmbedded<>(this, bp);
                } else {
                    return new RuntimeAssociation<>(this, bp);
                }
            } else {
                return new RuntimePersistentProperty<>(this, bp);
            }
        }).orElse(null);
    }

    @NonNull
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
