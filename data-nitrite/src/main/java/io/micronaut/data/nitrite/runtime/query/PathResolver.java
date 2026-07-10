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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a raw field name against entity metadata to produce a {@link PathResolution},
 * replacing the string name-matching heuristics that previously used
 * {@code getSimpleName()} / {@code getDecapitalizedName()} / prefix guessing.
 */
@Internal
final class PathResolver {

    private PathResolver() {
    }

    /**
     * Resolves {@code rawField} against {@code entity} metadata.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Identity aliases ({@code id}, {@code _id}, identity property name) → PLAIN</li>
     *   <li>Direct FK match: a MANY_TO_ONE whose persisted name equals the raw field → REFERENCE</li>
     *   <li>Segment walk over {@code getPersistentProperties()} matching on property name
     *       (with persisted-name and identity fallbacks). Uses {@code getPersistentProperties()}
     *       rather than {@code getPropertyByName} so that {@code mappedBy} ONE_TO_MANY /
     *       MANY_TO_MANY associations — absent from the stored-field name index — are found.</li>
     * </ol>
     */
    static PathResolution resolve(RuntimePersistentEntity<?> entity, String rawField) {
        if (entity == null) {
            return plain(rawField);
        }

        // Step 1: identity aliases
        try {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity != null && (
                    identity.getName().equals(rawField) ||
                    "id".equals(rawField) ||
                    "_id".equals(rawField))) {
                return plain(identity.getPersistedName());
            }
        } catch (IllegalStateException ignored) {
        }

        // Step 2: direct FK match — MANY_TO_ONE whose persistedName == rawField (non-dotted).
        // Matches FK fields stored in the document (e.g. "author" in a Book document).
        if (!rawField.contains(".")) {
            for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
                if (p instanceof RuntimeAssociation<?> assoc
                        && assoc.getKind() == Relation.Kind.MANY_TO_ONE
                        && assoc.getPersistedName().equals(rawField)) {
                    return reference(List.of(assoc), null, rawField);
                }
            }
        }

        // Step 3: segment walk via getPersistentProperties().
        // getPersistentProperties() includes mappedBy ONE_TO_MANY/MANY_TO_MANY associations
        // which are absent from getPropertyByName (stored-field index only).
        String[] segments = rawField.split("\\.", -1);
        RuntimePersistentEntity<?> currentEntity = entity;
        List<RuntimeAssociation<?>> chain = new ArrayList<>(segments.length - 1);

        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            boolean terminal = (i == segments.length - 1);

            RuntimePersistentProperty<?> found = findPropertyByName(currentEntity, seg);
            if (found == null) {
                return plain(rawField);
            }

            if (terminal) {
                if (chain.isEmpty()) {
                    if (found instanceof RuntimeAssociation<?> assoc) {
                        return reference(List.of(assoc), null, assoc.getPersistedName());
                    }
                    return plain(found.getPersistedName());
                }
                if (chain.get(0).isEmbedded()) {
                    // Embedded chain: rawField is the compile-time dotted persisted path.
                    return new PathResolution(PathResolution.Kind.EMBEDDED, chain, found, rawField);
                }
                return reference(chain, found, chain.get(0).getPersistedName());
            } else {
                if (found instanceof RuntimeAssociation<?> assoc) {
                    chain.add(assoc);
                    currentEntity = (RuntimePersistentEntity<?>) assoc.getAssociatedEntity();
                } else {
                    // Non-association in a non-terminal position → plain fallback.
                    return plain(rawField);
                }
            }
        }

        return plain(rawField);
    }

    /**
     * Finds a property on {@code entity} whose name (or, as fallbacks, whose persisted name or
     * identity alias) matches {@code name}.
     */
    private static RuntimePersistentProperty<?> findPropertyByName(RuntimePersistentEntity<?> entity, String name) {
        // Primary: match on property name
        for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        // Fallback 1: identity aliases ("_id", id.getName(), id.getPersistedName())
        try {
            RuntimePersistentProperty<?> id = entity.getIdentity();
            if (id != null && (id.getName().equals(name)
                    || id.getPersistedName().equals(name)
                    || "_id".equals(name))) {
                return id;
            }
        } catch (IllegalStateException ignored) {
        }
        // Fallback 2: persisted-name match for plain (non-association) properties
        for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
            if (!(p instanceof RuntimeAssociation<?>) && p.getPersistedName().equals(name)) {
                return p;
            }
        }
        // Fallback 3: ONE_TO_MANY/MANY_TO_MANY — match by join-segment {child_collection}_{owner_collection}
        // or by the association's own persisted name (used by MANY_TO_MANY stored as an array field).
        // The {child}_{owner} naming convention mirrors NitriteQueryBuilder.addLookups / NitriteFieldNameResolver;
        // if that convention changes, update both sides in lockstep.
        for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
            if (p instanceof RuntimeAssociation<?> assoc
                    && (assoc.getKind() == Relation.Kind.ONE_TO_MANY || assoc.getKind() == Relation.Kind.MANY_TO_MANY)) {
                String joinSegment = assoc.getAssociatedEntity().getPersistedName() + "_" + entity.getPersistedName();
                if (joinSegment.equals(name) || assoc.getPersistedName().equals(name)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static PathResolution plain(String persistedField) {
        return new PathResolution(PathResolution.Kind.PLAIN, List.of(), null, persistedField);
    }

    private static PathResolution reference(List<RuntimeAssociation<?>> chain,
                                             RuntimePersistentProperty<?> terminal,
                                             String persistedField) {
        return new PathResolution(PathResolution.Kind.REFERENCE, chain, terminal, persistedField);
    }

    /**
     * Describes how a raw field name resolves against an entity's metadata.
     *
     * @param kind PLAIN: no association; EMBEDDED: all hops are embedded; REFERENCE: first hop is a reference association
     * @param chain the resolved association chain (empty for PLAIN)
     * @param terminal the terminal property (null when the path ends at an association itself)
     * @param persistedField PLAIN/EMBEDDED: persisted field name or dotted path; REFERENCE: persisted name of chain head
     */
    record PathResolution(
        Kind kind,
        List<RuntimeAssociation<?>> chain,
        RuntimePersistentProperty<?> terminal,
        String persistedField
    ) {
        boolean isReference() {
            return kind == Kind.REFERENCE;
        }

        enum Kind {
            PLAIN, EMBEDDED, REFERENCE
        }
    }
}
