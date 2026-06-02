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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/** Maps entity property paths to Nitrite document field names. */
final class NitriteFieldNameResolver {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteFieldNameResolver.class);
    static final String ID_FIELD = "id";

    private NitriteFieldNameResolver() {}

    static String getFieldName(PersistentPropertyPath propertyPath) {
        String result = resolve(propertyPath);
        LOG.debug("getFieldName: path={}, result={}", propertyPath.getPath(), result);
        return result;
    }

    static String asPath(Collection<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return property.getPersistedName();
        }
        StringBuilder sb = new StringBuilder();
        for (Association association : associations) {
            sb.append(association.getPersistedName()).append(".");
        }
        sb.append(property.getPersistedName());
        return sb.toString();
    }

    private static String resolve(PersistentPropertyPath propertyPath) {
        PersistentProperty property = propertyPath.getProperty();
        PersistentEntity owner = property.getOwner();
        PersistentProperty identity;
        try {
            identity = owner.getIdentity();
        } catch (IllegalStateException e) {
            identity = null;
        }
        if (identity != null && identity.equals(property) && propertyPath.getAssociations().isEmpty()) {
            return ID_FIELD;
        }

        if (propertyPath.getAssociations().isEmpty()) {
            return property.getPersistedName();
        }

        StringBuilder sb = new StringBuilder();
        boolean inIdentityPath = false;
        for (Association association : propertyPath.getAssociations()) {
            if (association.isEmbedded()) {
                boolean isIdentityAssoc = false;
                try {
                    PersistentProperty ownerIdentity = association.getOwner().getIdentity();
                    isIdentityAssoc = ownerIdentity.equals(association);
                } catch (IllegalStateException ignored) {
                }
                String segment = isIdentityAssoc ? "_id" : association.getPersistedName();
                sb.append(segment).append(".");
                if (isIdentityAssoc) {
                    inIdentityPath = true;
                }
            } else {
                if (inIdentityPath) {
                    boolean isAssocIdentity = false;
                    try {
                        PersistentProperty assocOwnerIdentity = association.getOwner().getIdentity();
                        isAssocIdentity = assocOwnerIdentity.equals(association);
                    } catch (IllegalStateException ignored) {
                    }
                    String embeddedName;
                    if (isAssocIdentity) {
                        embeddedName = "_id";
                    } else if (association.getAnnotationMetadata().stringValue(MappedProperty.class).isPresent()) {
                        embeddedName = association.getPersistedName();
                    } else {
                        embeddedName = association.getName();
                    }
                    sb.append(embeddedName).append(".");
                } else if (association.getKind() == Relation.Kind.ONE_TO_MANY || association.getKind() == Relation.Kind.MANY_TO_MANY) {
                    sb.append(association.getPersistedName()).append(".");
                } else {
                    List<Association> assocs = propertyPath.getAssociations();
                    boolean isLast = association == assocs.get(assocs.size() - 1);
                    boolean isIdentityAccess = false;
                    if (isLast) {
                        try {
                            isIdentityAccess = association.getAssociatedEntity().getIdentity().equals(property);
                        } catch (Exception ignored) {
                            // Best-effort identity access check
                        }
                    }
                    if (isLast && isIdentityAccess) {
                        return association.getPersistedName();
                    }
                    sb.append(association.getName()).append(".");
                }
            }
        }
        if (inIdentityPath) {
            boolean isPropertyIdentity = false;
            try {
                PersistentProperty ownerIdentity = property.getOwner().getIdentity();
                isPropertyIdentity = ownerIdentity.equals(property);
            } catch (IllegalStateException ignored) {
            }
            sb.append(isPropertyIdentity ? "_id" : property.getPersistedName());
        } else {
            sb.append(property.getPersistedName());
        }
        return sb.toString();
    }
}
