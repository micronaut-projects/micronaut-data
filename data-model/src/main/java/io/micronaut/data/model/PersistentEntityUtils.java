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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Persistent entity utils.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class PersistentEntityUtils {

    private static final String ANN_JOIN_COLUMNS = "io.micronaut.data.jdbc.annotation.JoinColumns";

    private PersistentEntityUtils() {
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
        if (persistentEntity.getIdentity() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getIdentity(), consumer);
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
        if (includeIdentity && persistentEntity.getIdentity() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getIdentity(), consumer);
        }
        if (includeVersion && persistentEntity.getVersion() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getVersion(), consumer);
        }
        for (PersistentProperty property : persistentEntity.getPersistentProperties()) {
            traversePersistentProperties(Collections.emptyList(), property, consumer);
        }
    }

    public static void traversePersistentProperties(List<Association> associations,
                                                     PersistentProperty property,
                                                     BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        if (property instanceof Embedded) {
            Embedded embedded = (Embedded) property;
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            Collection<? extends PersistentProperty> embeddedProperties = embeddedEntity.getPersistentProperties();
            List<Association> newAssociations = new ArrayList<>(associations);
            newAssociations.add((Association) property);
            for (PersistentProperty embeddedProperty : embeddedProperties) {
                traversePersistentProperties(newAssociations, embeddedProperty, consumerProperty);
            }
        } else if (property instanceof Association) {
            Association association = (Association) property;
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
                List<PersistentProperty> identities = new ArrayList<>();
                AnnotationValue<Annotation> joinColumnsHolder = property.getAnnotationMetadata().getAnnotation(ANN_JOIN_COLUMNS);
                if (joinColumnsHolder != null) {
                    Collection<? extends  PersistentProperty> persistentProperties = associatedEntity.getPersistentProperties();
                    // If associated entity identity field is not used in this association we shouldn't add it since it's not needed
                    for (AnnotationValue<Annotation> ann : joinColumnsHolder.getAnnotations("value")) {
                        String referencedColumnName = ann.stringValue("referencedColumnName").orElse(null);
                        if (referencedColumnName != null && !referencedColumnName.equals(assocIdentity.getPersistedName())) {
                            for (PersistentProperty persistentProperty : persistentProperties) {
                                if (persistentProperty.getPersistedName().equals(referencedColumnName)) {
                                    identities.add(persistentProperty);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (identities.isEmpty()) {
                    identities.add(assocIdentity);
                }
                for (PersistentProperty identity : identities) {
                    consumerProperty.accept(newAssociations, identity);
                }
            }
        } else {
            consumerProperty.accept(associations, property);
        }
    }

}
