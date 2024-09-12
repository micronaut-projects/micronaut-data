/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

/**
 * A property that represents an association.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Association extends PersistentProperty {

    /**
     * @return The alias name representation.
     */
    default String getAliasName() {
        AnnotationValue<MappedProperty> mappedProperty = getAnnotation(MappedProperty.class);
        if (mappedProperty != null) {
            Optional<String> alias = mappedProperty.stringValue("alias");
            if (alias.isPresent()) {
                return alias.get();
            }
        }
        return NamingStrategy.DEFAULT.mappedName(getName()) + "_";
    }

    /**
     * @since 3.8
     * @return whether this Association has a declared alias name
     */
    default boolean hasDeclaredAliasName() {
        AnnotationValue<MappedProperty> mappedProperty = getAnnotation(MappedProperty.class);
        return mappedProperty != null && mappedProperty.stringValue("alias").isPresent();
    }

    /**
     * The associated entity if any.
     * @return The associated entity
     */
    @NonNull
    PersistentEntity getAssociatedEntity();

    /**
     * Retrieves the inverse side of the association. If there is one.
     *
     * @return The association.
     */
    default Optional<? extends Association> getInverseSide() {
        return getAnnotationMetadata()
                .stringValue(Relation.class, "mappedBy")
                .flatMap(s -> {
                    final PersistentProperty persistentProperty = getAssociatedEntity().getPropertyByPath(s).orElse(null);
                    if (persistentProperty instanceof Association association) {
                        return Optional.of(association);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Retrieves the inverse side path of the association. If there is one.
     *
     * @return The association.
     */
    default Optional<PersistentAssociationPath> getInversePathSide() {
        return getAnnotationMetadata()
                .stringValue(Relation.class, "mappedBy")
                .flatMap(s -> {
                    final PersistentPropertyPath persistentPropertyPath = getAssociatedEntity().getPropertyPath(s);
                    if (persistentPropertyPath instanceof PersistentAssociationPath persistentAssociationPath) {
                        return Optional.of(persistentAssociationPath);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Whether the relationship is bidirectional.
     * @return True if it is bidirectional.
     */
    default boolean isBidirectional() {
        return getInverseSide().isPresent();
    }

    /**
     * @return The relationship kind
     */
    default @NonNull Relation.Kind getKind() {
        return findAnnotation(Relation.class)
                .flatMap(av -> av.enumValue(Relation.Kind.class))
                .orElse(Relation.Kind.ONE_TO_ONE);
    }

    /**
     * @return Whether the association is a foreign key association
     */
    default boolean isForeignKey() {
        Relation.Kind kind = getKind();
        return kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY || (kind == Relation.Kind.ONE_TO_ONE && getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isPresent());
    }

    /**
     * Whether this association cascades the given types.
     * @param types The types
     * @return True if it does, false otherwise.
     */
    default boolean doesCascade(Relation.Cascade... types) {
        if (ArrayUtils.isEmpty(types)) {
            return false;
        }
        EnumSet<Relation.Cascade> cascadeTypes = getCascadeTypes();
        if (cascadeTypes.contains(Relation.Cascade.ALL)) {
            return true;
        }
        if (cascadeTypes.contains(Relation.Cascade.NONE)) {
            return false;
        }
        for (Relation.Cascade cascade : types) {
            if (cascadeTypes.contains(cascade)) {
                return true;
            }
        }
        return false;
    }

    default EnumSet<Relation.Cascade> getCascadeTypes() {
        final Relation.Cascade[] cascades = getAnnotationMetadata().enumValues(Relation.class, "cascade", Relation.Cascade.class);
        if (cascades.length == 0) {
            return EnumSet.noneOf(Relation.Cascade.class);
        }
        for (Relation.Cascade cascade : cascades) {
            if (cascade == Relation.Cascade.ALL) {
                return EnumSet.allOf(Relation.Cascade.class);
            }
            if (cascade == Relation.Cascade.NONE) {
                return EnumSet.noneOf(Relation.Cascade.class);
            }
        }
        return EnumSet.copyOf(Arrays.asList(cascades));
    }
}
