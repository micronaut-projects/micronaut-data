/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;

import java.util.List;

/**
 * Utility methods for deciding whether a persistent property can be included in
 * implicit generated ETag input selection.
 *
 * <p>The rules here intentionally mirror the metadata synthesized by
 * {@link GeneratedETagUtils}: implicit ETag inputs must be deterministic scalar
 * values from the owning entity or embedded object graph, with special handling
 * for identity properties and opt-in owning foreign keys.</p>
 */
@Internal
final class ImplicitETagUtils {

    private ImplicitETagUtils() {
    }

    /**
     * Determines whether a traversed property is eligible for implicit ETag
     * input selection.
     *
     * <p>Generated or read-transformed properties are excluded because their
     * persisted value is not a stable direct input, except when the property is
     * part of the entity identity. The generated ETag property itself, existing
     * version properties, JSON/object/array values, and non-embedded association
     * paths are also excluded. Owning foreign-key associations are considered
     * only when the entity-level configuration explicitly includes them.</p>
     *
     * @param entity The entity that owns the generated ETag declaration
     * @param associations The association path used to reach the property
     * @param property The property being considered as an ETag input
     * @param etagProp The property that stores the generated ETag value
     * @param includeForeignKeys Whether owning foreign-key associations should be eligible
     * @return {@code true} if the property can be selected implicitly
     */
    static boolean isImplicitEtagEligible(SourcePersistentEntity entity,
                                          List<Association> associations,
                                          PersistentProperty property,
                                          SourcePersistentProperty etagProp,
                                          boolean includeForeignKeys) {
        if (associations.stream().anyMatch(association -> !(association instanceof Embedded))) {
            return false;
        }
        // Exclude the version field explicitly
        if (entity.hasVersion() && property == entity.getVersion()) {
            return false;
        }
        // Exclude the etag field itself
        if (property == etagProp) {
            return false;
        }
        if ((property.isGenerated() || hasReadTransformer(property)) && !isIdentityProperty(entity, associations, property)) {
            return false;
        }
        // Relations handling
        if (property instanceof Association association && !(association instanceof Embedded)) {
            // Non-embedded: include only if user opted in and association is MANY_TO_ONE or ONE_TO_ONE
            Relation.Kind kind = association.getKind();
            if (!(includeForeignKeys && (kind == Relation.Kind.MANY_TO_ONE || kind == Relation.Kind.ONE_TO_ONE))) {
                return false;
            }
        }
        // Exclude JSON/OBJECT and all arrays (including BYTE_ARRAY)
        DataType dt = property.getDataType();
        return dt != DataType.OBJECT && dt != DataType.JSON && !dt.isArray();
        // Otherwise include
    }

    /**
     * Checks whether a traversed property represents the entity identity.
     *
     * <p>Embedded identity members are treated as identity properties when the
     * first association in the traversal path is one of the entity identities.
     * This allows generated/read-transformed embedded-id members to participate
     * when they are the physical key columns required for ETag computation.</p>
     *
     * @param entity The entity that owns the identity metadata
     * @param associations The association path used to reach the property
     * @param property The property being considered
     * @return {@code true} if the property is the identity or part of an embedded identity
     */
    private static boolean isIdentityProperty(SourcePersistentEntity entity,
                                              List<Association> associations,
                                              PersistentProperty property) {
        if (entity.getIdentityProperties().contains(property)) {
            return true;
        }
        return !associations.isEmpty() && entity.getIdentityProperties().contains(associations.getFirst());
    }

    /**
     * Checks whether a property declares a non-empty read transformer.
     *
     * <p>Read transformers are excluded from implicit ETag inputs because the
     * transformed projection may not match the stored value used by the database
     * to compute an ETag.</p>
     *
     * @param property The property to inspect
     * @return {@code true} if a read transformer is configured
     */
    private static boolean hasReadTransformer(PersistentProperty property) {
        return property.getAnnotationMetadata()
            .stringValue(DataTransformer.class, "read")
            .map(val -> !val.isEmpty())
            .orElse(false);
    }
}
