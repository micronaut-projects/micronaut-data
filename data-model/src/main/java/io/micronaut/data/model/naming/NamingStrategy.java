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
package io.micronaut.data.model.naming;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;


/**
 * A strategy interface for resolving the mapped name of an entity or property.
 *
 * @author graemerocher
 * @since 1.0
 */
@FunctionalInterface
@Introspected
public interface NamingStrategy {

    /**
     * Constant for the default under score separated lower case strategy.
     */
    NamingStrategy DEFAULT = new NamingStrategies.UnderScoreSeparatedLowerCase();

    /**
     * Return the mapped name for the given name.
     * @param name The name
     * @return The mapped name
     */
    @NonNull
    String mappedName(@NonNull String name);

    /**
     * Return the mapped name for the given entity.
     * @param entity The entity
     * @return The mapped name
     */
    default @NonNull String mappedName(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class)
                .filter(StringUtils::isNotEmpty)
                .orElseGet(() -> mappedName(entity.getSimpleName()));
    }

    /**
     * Return the mapped name given an {@link Embedded} association and the property of the association. The
     * default strategy takes the parent embedded property name and combines it underscore separated with the child parent property name.
     *
     * <p>For example given:</p>
     *
     * <pre><code>
     * {@literal @}Embedded Address address;
     * </code></pre>
     *
     * <p>Where the {@code Address} type has a property called {@code street} then a name of {@code address_street} will be returned</p>
     * @param embedded The embedded parent
     * @param property The embedded property
     * @return The mapped name
     */
    default @NonNull String mappedName(Embedded embedded, PersistentProperty property) {
        return mappedName(embedded.getName() + mappedAssociatedName(property.getPersistedName()));
    }

    /**
     * Return the mapped name for the given property.
     * @param property The property
     * @return The mapped name
     */
    default @NonNull String mappedName(@NonNull PersistentProperty property) {
        ArgumentUtils.requireNonNull("property", property);
        if (property instanceof Association) {
            return mappedName((Association) property);
        } else {
            return property.getAnnotationMetadata()
                    .stringValue(MappedProperty.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElseGet(() -> mappedName(property.getName()));
        }
    }

    /**
     * Return the mapped name for the given association.
     * @param association The association
     * @return The mapped name
     */
    default @NonNull String mappedName(Association association) {
        String providedName = association.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
        if (providedName != null) {
            return providedName;
        }
        if (association.isForeignKey()) {
            Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
            Association owningAssociation = inverseSide.orElse(association);
            return mappedName(owningAssociation.getOwner().getDecapitalizedName() + owningAssociation.getAssociatedEntity().getSimpleName());
        } else {
            return switch (association.getKind()) {
                case ONE_TO_ONE, MANY_TO_ONE -> mappedName(association.getName() + getForeignKeySuffix());
                default -> mappedName(association.getName());
            };
        }
    }

    /**
     * Convert the associated name to a proper format to be appended to the path.
     * @param associatedName The associated name
     * @return the name in a proper format
     * @since 4.2.0
     */
    @NonNull
    default String mappedAssociatedName(@NonNull String associatedName) {
        return NameUtils.capitalize(associatedName);
    }

    default @NonNull String mappedName(@NonNull List<Association> associations, @NonNull PersistentProperty property) {
        if (associations.isEmpty()) {
            return mappedName(property);
        }
        StringBuilder sb = new StringBuilder();
        Association foreignAssociation = null;
        for (Association association : associations) {
            if (association.getKind() != Relation.Kind.EMBEDDED && foreignAssociation == null) {
                foreignAssociation = association;
            }
            final String originalAssocName = association.getName();
            String assocName = association.getKind() == Relation.Kind.EMBEDDED ? association.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(originalAssocName) : originalAssocName;
            if (!sb.isEmpty()) {
                sb.append(mappedAssociatedName(assocName));
            } else {
                sb.append(assocName);
            }
        }
        if (foreignAssociation != null) {
            PersistentEntity associatedEntity = foreignAssociation.getAssociatedEntity();
            PersistentProperty associatedEntityIdentity = associatedEntity != null ? associatedEntity.getIdentity() : null;
            if (associatedEntity != null && associatedEntity.equals(property.getOwner())
                && associatedEntityIdentity != null && associatedEntityIdentity.equals(property)) {
                String providedName = foreignAssociation.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
                if (providedName != null) {
                    return providedName;
                }
                sb.append(getForeignKeySuffix());
                return mappedName(sb.toString());
            } else if (foreignAssociation.isForeignKey()) {
                throw new IllegalStateException("Foreign association cannot be mapped!");
            }
        } else {
            String providedName = property.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
            if (providedName != null) {
                return providedName;
            }
        }
        if (!sb.isEmpty()) {
            sb.append(mappedAssociatedName(property.getName()));
        } else {
            sb.append(property.getName());
        }
        return mappedName(sb.toString());
    }

    default String mappedJoinTableColumn(PersistentEntity associated, List<Association> associations, PersistentProperty property) {
        StringBuilder sb = new StringBuilder();
        sb.append(associated.getDecapitalizedName());
        for (Association association : associations) {
            sb.append(mappedAssociatedName(association.getName()));
        }
        if (associations.isEmpty()) {
            sb.append(getForeignKeySuffix());
        } else {
            sb.append(mappedAssociatedName(property.getName()));
        }
        return mappedName(sb.toString());
    }

    /**
     * The default foreign key suffix for property names.
     * @return The suffix
     */
    default @NonNull String getForeignKeySuffix() {
        return "Id";
    }
}
