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
     * Check if the property is an association ID that can be accessed without join. In a case it's not an ID stored outside the associated table.
     * @param association The association
     * @param persistentProperty The association's property
     * @return true if can be accessed
     * @since 4.2.0
     */
    public static boolean isAccessibleWithoutJoin(Association association, PersistentProperty persistentProperty) {
        PersistentProperty identity = association.getAssociatedEntity().getIdentity();
        if (identity instanceof Embedded embedded) {
            for (PersistentProperty property : embedded.getAssociatedEntity().getPersistentProperties()) {
                if (property == persistentProperty) {
                    return !association.isForeignKey();
                }
            }

        }
        return identity == persistentProperty && !association.isForeignKey();
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
        if (persistentEntity.getVersion() != null) {
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
        if (includeVersion && persistentEntity.getVersion() != null) {
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
            newAssociations.add((Association) property);
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            PersistentProperty assocIdentity = associatedEntity.getIdentity();
            if (assocIdentity == null) {
                throw new IllegalStateException("Identity cannot be missing for: " + associatedEntity);
            }
            if (assocIdentity instanceof Association) {
                traversePersistentProperties(newAssociations, assocIdentity, consumerProperty);
            } else {
                // In case there is JoinColumn defined on property, we might use specified column
                // instead of association id
                PersistentProperty joinColumnAssocIdentity = getJoinColumnAssocIdentity(property, associatedEntity);
                if (joinColumnAssocIdentity != null) {
                    consumerProperty.accept(newAssociations, joinColumnAssocIdentity);
                } else {
                    consumerProperty.accept(newAssociations, assocIdentity);
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

    /**
     * Checks whether a given property is considered generated based on its annotations and relationship with its owner property.
     *
     * @param entity        the persistent entity that owns the property
     * @param ownerProperty the property that owns the given property. This means
     *                      when we are doing traversal in case it is an association. If not
     *                      an association then ownerProperty will be the same as property.
     * @param property      the property to check
     * @return true if the property is considered generated, false otherwise
     */
    public static boolean isPropertyGenerated(PersistentEntity entity, PersistentProperty ownerProperty, PersistentProperty property) {
        boolean generated = property.isGenerated();
        if (generated) {
            if (ownerProperty instanceof Association association) {
                generated = association.isEmbedded();
            } else if (!entity.equals(property.getOwner())) {
                generated = false;
            }
        }
        return generated;
    }

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
