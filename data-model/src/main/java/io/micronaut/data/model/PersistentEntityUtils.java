/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Persistent entity utils.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class PersistentEntityUtils {

    private static final String UNDERSCORE = "_";

    private PersistentEntityUtils() {
    }

    /**
     * Check if the association's property is stored on the owning side, and so can be read without joining
     * to the associated table.
     *
     * <p>The answer has to agree with what {@link #traversePersistentProperties(List, PersistentProperty, boolean, BiConsumer)}
     * emits for the same association, because a caller that resolves a property path uses this method to decide
     * whether the leaf that traversal produced belongs to the owning table or to the join alias. The three
     * association target shapes are therefore mirrored here:</p>
     * <ul>
     *     <li>a single identity - only that identity is on the owning side;</li>
     *     <li>a composite identity - every identity property maps a column on the owning side;</li>
     *     <li>no identity - the target is stored inline, so all of its properties are on the owning side.</li>
     * </ul>
     *
     * <p>Traversal descends through embedded properties, so the leaf it produces can be nested several
     * embeddeds deep; matching is recursive to reach it.</p>
     *
     * @param association The association
     * @param persistentProperty The association's property
     * @return true if can be accessed
     * @since 4.2.0
     */
    public static boolean isAccessibleWithoutJoin(Association association, PersistentProperty persistentProperty) {
        if (association instanceof Embedded) {
            return true;
        }
        if (association.isForeignKey()) {
            return false;
        }
        PersistentEntity associatedEntity = association.getAssociatedEntity();
        List<PersistentProperty> identityProperties = associatedEntity.getIdentityProperties();
        if (identityProperties.isEmpty()) {
            // An identity-less association is stored embedded in the owning document
            return contains(associatedEntity.getPersistentProperties(), persistentProperty);
        }
        for (PersistentProperty identity : identityProperties) {
            if (identity == persistentProperty) {
                return true;
            }
            if (identity instanceof Embedded embedded && contains(embedded.getAssociatedEntity().getPersistentProperties(), persistentProperty)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Collection<? extends PersistentProperty> properties, PersistentProperty persistentProperty) {
        for (PersistentProperty property : properties) {
            if (property == persistentProperty) {
                return true;
            }
            // Traversal descends through embedded properties, so the leaf it reaches can be nested
            // several embeddeds deep; matching only the top level would report a needless join
            if (property instanceof Embedded embedded
                && contains(embedded.getAssociatedEntity().getPersistentProperties(), persistentProperty)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Traverses properties that should be persisted.
     *
     * @param property The property to start traversing from
     * @param consumer The function to invoke on every property
     */
    public static void traversePersistentProperties(PersistentProperty property, BiConsumer<List<Association>, PersistentProperty> consumer) {
        traversePersistentProperties(Collections.emptyList(), property, consumer);
    }

    /**
     * Traverses properties that should be persisted.
     *
     * @param persistentEntity The persistent entity
     * @param consumer         The function to invoke on every property
     */
    public static void traversePersistentProperties(PersistentEntity persistentEntity, BiConsumer<List<Association>, PersistentProperty> consumer) {
        for (PersistentProperty identityProperty : persistentEntity.getIdentityProperties()) {
            traversePersistentProperties(Collections.emptyList(), identityProperty, consumer);
        }
        if (persistentEntity.hasVersion()) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getVersion(), consumer);
        }
        for (PersistentProperty property : persistentEntity.getPersistentProperties()) {
            traversePersistentProperties(Collections.emptyList(), property, consumer);
        }
    }

    /**
     * Traverses properties that should be persisted.
     *
     * @param persistentEntity The persistent entity
     * @param includeIdentity  Should be identifier included
     * @param includeVersion   Should be version included
     * @param consumer         The function to invoke on every property
     */
    public static void traversePersistentProperties(PersistentEntity persistentEntity, boolean includeIdentity, boolean includeVersion, BiConsumer<List<Association>, PersistentProperty> consumer) {
        if (includeIdentity) {
            for (PersistentProperty identityProperty : persistentEntity.getIdentityProperties()) {
                traversePersistentProperties(Collections.emptyList(), identityProperty, consumer);
            }
        }
        if (includeVersion && persistentEntity.hasVersion()) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getVersion(), consumer);
        }
        for (PersistentProperty property : persistentEntity.getPersistentProperties()) {
            traversePersistentProperties(Collections.emptyList(), property, consumer);
        }
    }

    /**
     * Count possible embedded properties.
     *
     * @param property The property
     * @return the count
     */
    public static int countPersistentProperties(PersistentProperty property) {
        return countPersistentProperties(List.of(), property);
    }

    /**
     * Count possible embedded properties.
     *
     * @param property     The property
     * @param associations The associations
     * @return the count
     */
    public static int countPersistentProperties(List<Association> associations,
                                                PersistentProperty property) {
        int[] count = new int[1];
        traversePersistentProperties(associations, property, (ignore1, ignore2) -> count[0]++);
        return count[0];
    }

    public static void traversePersistentProperties(List<Association> associations,
                                                    PersistentProperty property,
                                                    BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        traversePersistentProperties(associations, property, true, consumerProperty);
    }

    public static void traversePersistentProperties(PersistentPropertyPath propertyPath,
                                                    BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), true, consumerProperty);
    }

    public static void traverse(PersistentPropertyPath propertyPath, Consumer<PersistentPropertyPath> consumer) {
        BiConsumer<List<Association>, PersistentProperty> consumerProperty
            = (associations, property) -> consumer.accept(new PersistentPropertyPath(associations, property));
        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), true, consumerProperty);
    }

    public static void traversePersistentProperties(PersistentPropertyPath propertyPath,
                                                    boolean traverseEmbedded,
                                                    BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), traverseEmbedded, consumerProperty);
    }

    /**
     * Traverses the properties that should be persisted for the given property, descending through embedded
     * properties and through the owning side of non-foreign-key associations until a leaf is reached.
     *
     * <p>An association contributes the columns that the owning side stores for it, which depends on the
     * shape of the association's target:</p>
     * <ul>
     *     <li><b>single identity</b> - that identity, or the column named by a single {@code @JoinColumn}
     *     when one is declared on the property;</li>
     *     <li><b>composite identity</b> - one leaf per identity property. A single referenced column cannot
     *     stand in for several, so {@code @JoinColumn} substitution does not apply;</li>
     *     <li><b>no identity</b> - the target is a value object stored inline in the owning record, so its
     *     properties are traversed as if it were embedded. Traversal stops if the value object refers back
     *     to an entity already on the path, which would otherwise never terminate.</li>
     * </ul>
     *
     * <p>The latter two shapes only arise in document stores; a relational mapping declares an identity on
     * every association target.</p>
     *
     * @param associations      The associations traversed so far, prefixed to every emitted leaf
     * @param property          The property to traverse
     * @param traverseEmbedded  Whether to descend into an embedded property or emit it whole
     * @param consumerProperty  The function to invoke on every leaf property
     */
    public static void traversePersistentProperties(List<Association> associations,
                                                    PersistentProperty property,
                                                    boolean traverseEmbedded,
                                                    BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        if (property instanceof Embedded embedded) {
            if (traverseEmbedded) {
                PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
                Collection<? extends PersistentProperty> embeddedProperties = embeddedEntity.getPersistentProperties();
                List<Association> newAssociations = new ArrayList<>(associations);
                newAssociations.add((Association) property);
                for (PersistentProperty embeddedProperty : embeddedProperties) {
                    traversePersistentProperties(newAssociations, embeddedProperty, consumerProperty);
                }
            } else {
                consumerProperty.accept(associations, property);
            }
        } else if (property instanceof Association association) {
            if (association.isForeignKey()) {
                return;
            }
            List<Association> newAssociations = new ArrayList<>(associations);
            newAssociations.add(association);
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            Collection<? extends PersistentProperty> identityProperties = associatedEntity.getIdentityProperties();
            if (identityProperties.isEmpty()) {
                // Identity-less associations behave like embedded value objects.
                for (Association traversedAssociation : associations) {
                    if (traversedAssociation.getAssociatedEntity() == associatedEntity) {
                        // The value object refers back to itself, traversing it would never terminate
                        return;
                    }
                }
                for (PersistentProperty associatedProperty : associatedEntity.getPersistentProperties()) {
                    traversePersistentProperties(newAssociations, associatedProperty, consumerProperty);
                }
                return;
            }
            // In case there is a single JoinColumn defined on the property, we might use the specified
            // column instead of the association id. A composite identity maps a column each, so the
            // single referenced column cannot stand in for all of them.
            PersistentProperty joinColumnIdentity = identityProperties.size() == 1
                ? getJoinColumnAssocIdentity(property, associatedEntity)
                : null;
            for (PersistentProperty identityProperty : identityProperties) {
                if (identityProperty instanceof Association) {
                    traversePersistentProperties(newAssociations, identityProperty, consumerProperty);
                } else {
                    consumerProperty.accept(newAssociations,
                        joinColumnIdentity != null ? joinColumnIdentity : identityProperty);
                }
            }
        } else {
            consumerProperty.accept(associations, property);
        }
    }

    /**
     * Computes a dot separated property path for the given camel case path.
     *
     * @param path The camel case path, can contain underscore to indicate how we should traverse entity properties
     * @param entity the persistent entity
     * @return The dot separated version or null if it cannot be computed
     */
    public static Optional<String> getPersistentPropertyPath(PersistentEntity entity, String path) {
        String decapitalizedPath = NameUtils.decapitalize(path);
        if (entity.getPropertyByName(decapitalizedPath) != null) {
            // First try to see if there is direct property on the entity
            return Optional.of(decapitalizedPath);
        }
        // Then see if path contains underscore to indicate which paths/entities to lookup
        String[] entityPaths = path.split(UNDERSCORE);
        if (entityPaths.length > 1) {
            String assocPath = entityPaths[0];
            PersistentProperty pp = entity.getPropertyByName(assocPath);
            if (pp instanceof Association assoc) {
                PersistentEntity assocEntity = assoc.getAssociatedEntity();
                String restPath = path.replaceFirst(assocPath + UNDERSCORE, "");
                Optional<String> tailPath = getPersistentPropertyPath(assocEntity, restPath);
                if (tailPath.isPresent()) {
                    return Optional.of(assocPath + "." + tailPath.get());
                }
                throw new IllegalArgumentException("Invalid path [" + restPath + "] of [" + assocEntity + "]");
            }
        }
        return entity.getPath(path);
    }

    @Nullable
    private static PersistentProperty getJoinColumnAssocIdentity(PersistentProperty property, PersistentEntity associatedEntity) {
        AnnotationMetadata propertyAnnotationMetadata = property.getAnnotationMetadata();
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = propertyAnnotationMetadata.getAnnotation(JoinColumns.class);
        if (joinColumnsAnnotationValue == null) {
            return null;
        }
        List<AnnotationValue<JoinColumn>> joinColumnsAnnotationValueAnnotations = joinColumnsAnnotationValue.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        if (joinColumnsAnnotationValueAnnotations.size() != 1) {
            // we can match only by one JoinColumn
            return null;
        }
        AnnotationValue<JoinColumn> joinColumnAnnotationValue = joinColumnsAnnotationValueAnnotations.get(0);
        String fieldName = joinColumnAnnotationValue.stringValue("referencedColumnName").orElse(null);
        if (fieldName == null) {
            return null;
        }
        Collection<? extends PersistentProperty> assocPersistentProperties = associatedEntity.getPersistentProperties();
        for (PersistentProperty assocPersistentProperty : assocPersistentProperties) {
            if (fieldName.equals(assocPersistentProperty.getPersistedName())) {
                return assocPersistentProperty;
            }
        }
        return null;
    }
}
