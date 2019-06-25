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
package io.micronaut.data.model;


import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategy;


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
        return NamingStrategy.DEFAULT.mappedName(getName()) + "_";
    }

    /**
     * The associated entity if any.
     * @return The associated entity
     */
    @Nullable
    PersistentEntity getAssociatedEntity();

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
        return kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY;
    }
}
