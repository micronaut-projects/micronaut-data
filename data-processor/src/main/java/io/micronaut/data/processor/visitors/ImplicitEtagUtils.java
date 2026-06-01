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

@Internal
final class ImplicitEtagUtils {

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
        if (dt == DataType.OBJECT || dt == DataType.JSON || dt.isArray()) {
            return false;
        }
        // Otherwise include
        return true;
    }

    private static boolean isIdentityProperty(SourcePersistentEntity entity,
                                              List<Association> associations,
                                              PersistentProperty property) {
        if (entity.getIdentityProperties().contains(property)) {
            return true;
        }
        return !associations.isEmpty() && entity.getIdentityProperties().contains(associations.get(0));
    }

    private static boolean hasReadTransformer(PersistentProperty property) {
        return property.getAnnotationMetadata()
            .stringValue(DataTransformer.class, "read")
            .map(value -> !value.isEmpty())
            .orElse(false);
    }
}
