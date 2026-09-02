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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The utility methods for query builders.
 */
@Internal
final class SqlQueryBuilderUtils {

    /**
     * Annotation used to represent join tables.
     */
    static final String ANN_JOIN_TABLE = "io.micronaut.data.annotation.sql.JoinTable";
    static final String ANN_JOIN_COLUMNS = "io.micronaut.data.annotation.sql.JoinColumns";
    static final String SEQ_SUFFIX = "_seq";
    private static final String PREFIX = "${";
    private static final String SUFFIX = "}";

    private SqlQueryBuilderUtils() { }

    /**
     * Maps the persisted name by applying the provided mapping function to each segment
     * of the persisted name that does not contain placeholders. Placeholders are defined
     * as strings enclosed within '${' and '}' characters.
     *
     * @param persistedName the persisted name to be mapped
     * @param mapFunction the function to apply to each non-placeholder segment
     * @return the mapped persisted name
     * @throws ConfigurationException if incomplete placeholder definitions are detected
     */
    static String mapPersistedName(String persistedName, UnaryOperator<String> mapFunction) {
        if (StringUtils.isEmpty(persistedName)) {
            return persistedName;
        }
        StringBuilder sb = new StringBuilder();

        String value = persistedName;
        int i = value.indexOf(PREFIX);
        while (i > -1) {
            //the text before the prefix
            if (i > 0) {
                String rawSegment = value.substring(0, i);
                sb.append(mapFunction.apply(rawSegment));
            }
            // everything after the prefix
            value = value.substring(i + PREFIX.length());
            int suffixIdx = value.indexOf(SUFFIX);
            if (suffixIdx > -1) {
                String expr = value.substring(0, suffixIdx).trim();
                sb.append(PREFIX).append(expr).append(SUFFIX);
                value = value.substring(suffixIdx + SUFFIX.length());
            } else {
                throw new ConfigurationException("Incomplete placeholder definitions detected: " + persistedName);
            }
            i = value.indexOf(PREFIX);
        }
        if (!value.isEmpty()) {
            sb.append(mapFunction.apply(value));
        }
        return sb.toString();
    }

    /**
     * Checks whether all associations in the given list are embedded.
     *
     * This method iterates over each association in the list and checks if its kind is {@link Relation.Kind#EMBEDDED}.
     * If any association is not embedded, the method immediately returns {@code false}. If all associations are embedded,
     * the method returns {@code true}.
     *
     * @param associations the list of associations to check
     * @return {@code true} if all associations are embedded, {@code false} otherwise
     */
    static boolean isNotForeign(List<Association> associations) {
        for (Association association : associations) {
            if (association.getKind() != Relation.Kind.EMBEDDED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Detects the narrow case where a relation deliberately reuses an entity identity column.
     *
     * <p>The duplicate insert/DDL column checks use this to distinguish a valid shared primary-key/foreign-key
     * one-to-one mapping from an accidental duplicate column mapping. Plain embedded paths are intentionally
     * rejected because they do not have join metadata proving that the duplicate column is a shared identity column.</p>
     *
     * @param associations The property path associations that lead to {@code property}
     * @param property The associated identity property
     * @param columnName The owner-side column name being written or generated
     * @return {@code true} if an explicit owning relation join column maps {@code columnName} to {@code property};
     *         an omitted referenced column name uses the associated identity property
     */
    static boolean isExplicitSharedIdentityJoinColumn(List<Association> associations,
                                                      PersistentProperty property,
                                                      String columnName) {
        Association foreignAssociation = null;
        for (Association association : associations) {
            if (association.getKind() != Relation.Kind.EMBEDDED) {
                foreignAssociation = association;
                break;
            }
        }
        if (foreignAssociation == null || foreignAssociation.isForeignKey()) {
            return false;
        }
        AnnotationValue<JoinColumns> joinColumns = foreignAssociation.getAnnotationMetadata().getAnnotation(JoinColumns.class);
        if (joinColumns == null) {
            return false;
        }
        for (AnnotationValue<?> joinColumn : joinColumns.getAnnotations(AnnotationMetadata.VALUE_MEMBER)) {
            String name = joinColumn.stringValue("name").orElse(null);
            String referencedColumnName = joinColumn.stringValue("referencedColumnName").orElse(null);
            if (name != null && name.isBlank()) {
                name = null;
            }
            if (referencedColumnName != null && referencedColumnName.isBlank()) {
                referencedColumnName = null;
            }
            if (columnName.equals(name) && (property.getPersistedName().equals(referencedColumnName)
                || (referencedColumnName == null
                && isImplicitIdentityProperty(foreignAssociation.getAssociatedEntity(), property, name)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImplicitIdentityProperty(PersistentEntity entity,
                                                      PersistentProperty property,
                                                      @Nullable String joinColumnName) {
        boolean[] identityProperty = {false};
        int[] identityPropertyCount = {0};
        for (PersistentProperty identity : entity.getIdentityProperties()) {
            PersistentEntityUtils.traversePersistentProperties(List.of(), identity, (associations, candidate) -> {
                identityPropertyCount[0]++;
                if (candidate.equals(property)) {
                    identityProperty[0] = true;
                }
            });
        }
        return identityProperty[0]
            && (identityPropertyCount[0] == 1
            || (joinColumnName != null && joinColumnName.equals(property.getPersistedName())));
    }

    /**
     * Detects the shared-identity update/DDL/insert case where an explicit join column also maps to
     * one of the root entity identity columns.
     *
     * @param entity The root entity being written
     * @param namingStrategy The naming strategy for the entity
     * @param associations The property path associations that lead to {@code property}
     * @param property The associated identity property
     * @param columnName The owner-side physical column name
     * @return {@code true} if the relation path maps to a root identity column
     */
    static boolean isSharedIdentityColumn(PersistentEntity entity,
                                          NamingStrategy namingStrategy,
                                          List<Association> associations,
                                          PersistentProperty property,
                                          String columnName) {
        return isExplicitSharedIdentityJoinColumn(associations, property, columnName)
            && isIdentityColumn(entity, namingStrategy, columnName);
    }

    /**
     * Checks whether the provided physical column belongs to the root entity identity.
     *
     * <p>This resolves embedded identities to their concrete columns so callers can distinguish true shared
     * identity columns from regular foreign-key columns that also reference an associated identity property.</p>
     */
    static boolean isIdentityColumn(PersistentEntity entity, NamingStrategy namingStrategy, String columnName) {
        for (PersistentProperty identity : entity.getIdentityProperties()) {
            boolean[] match = {false};
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property) -> {
                if (columnName.equals(namingStrategy.mappedName(associations, property))) {
                    match[0] = true;
                }
            });
            if (match[0]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts an association/property traversal to the dot-path used by query parameter bindings and conflict checks.
     */
    static String[] asPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return new String[]{property.getName()};
        }
        List<String> path = new ArrayList<>(associations.size() + 1);
        for (Association association : associations) {
            path.add(association.getName());
        }
        path.add(property.getName());
        return path.toArray(new String[0]);
    }

    /**
     * Retrieves the joined columns from the provided annotation metadata.
     *
     * This method checks for the presence of the {@code @JoinTable} annotation and extracts the joined columns
     * specified by either the {@code joinColumns} or {@code inverseJoinColumns} annotations, depending on the
     * association owner flag.
     *
     * @param annotationMetadata the annotation metadata to extract joined columns from
     * @param associationOwner whether the association is the owner side
     * @param columnType the type of column to retrieve (e.g., "name")
     * @return a list of joined column names, or an empty list if none are found
     */

    static List<String> getJoinedColumns(AnnotationMetadata annotationMetadata, boolean associationOwner, String columnType) {
        AnnotationValue<Annotation> joinTable = annotationMetadata.getAnnotation(ANN_JOIN_TABLE);
        if (joinTable != null) {
            return joinTable.getAnnotations(associationOwner ? "joinColumns" : "inverseJoinColumns")
                .stream()
                .flatMap(ann -> ann.stringValue(columnType).stream())
                .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Resolves the join table join columns based on the provided annotation metadata, association owner flag, entity, and naming strategy.
     *
     * If the annotation metadata contains explicit join columns, they are returned. Otherwise, the method traverses the entity's identity properties using the provided naming strategy to determine the join table column names.
     *
     * @param annotationMetadata the annotation metadata to check for explicit join columns
     * @param associationOwner whether the association is the owner side
     * @param entity the entity whose identity properties will be traversed if no explicit join columns are found
     * @param namingStrategy the naming strategy to use for determining join table column names
     * @return a list of join table column names
     */

    static List<String> resolveJoinTableJoinColumns(AnnotationMetadata annotationMetadata, boolean associationOwner, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedColumns(annotationMetadata, associationOwner, "name");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        List<String> columns = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), entity.getIdentity(), (associations, property)
            -> columns.add(namingStrategy.mappedJoinTableColumn(entity, associations, property)));
        return columns;
    }

    /**
     * Recursively flattens an embedded property into a stream of its constituent properties.
     *
     * If the provided property is an instance of {@link Embedded}, this method will recursively traverse its associated entity's properties.
     * Otherwise, it simply returns a stream containing the original property.
     *
     * @param pp the property to flatten
     * @return a stream of flattened properties
     */
    @SuppressWarnings("java:S1452")
    static Stream<? extends PersistentProperty> flatMapEmbedded(PersistentProperty pp) {
        if (pp instanceof Embedded embedded) {
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            return embeddedEntity.getPersistentProperties()
                .stream()
                .flatMap(SqlQueryBuilderUtils::flatMapEmbedded);
        }
        return Stream.of(pp);
    }

    /**
     * Retrieves a collection of associations that have a join table.
     *
     * This method iterates through the persistent properties of the given entity,
     * including its identity and embedded properties, and filters out those that
     * are not associations with a join table.
     *
     * @param persistentEntity the entity to retrieve associations from
     * @return a non-empty collection of associations with a join table
     */

    static Collection<Association> getJoinTableAssociations(PersistentEntity persistentEntity) {
        return Stream.concat(persistentEntity.getIdentityProperties().stream(), persistentEntity.getPersistentProperties().stream())
            .flatMap(SqlQueryBuilderUtils::flatMapEmbedded)
            .filter(p -> {
                if (p instanceof Association a) {
                    return isForeignKeyWithJoinTable(a);
                }
                return false;
            }).map(p -> (Association) p).toList();
    }

    /**
     * Retrieves the schema name from the given PersistentEntity.
     *
     * If the MappedEntity annotation contains a schema value, it will be returned.
     * Otherwise, the method will attempt to retrieve the schema value again from the same annotation.
     * If no schema value is found, null will be returned.
     *
     * @param entity the PersistentEntity to retrieve the schema name from
     * @return the schema name, or null if not found
     */
    static String getSchemaName(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElseGet(() ->
            entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElse(null));
    }

    /**
     * Finds int value for javax.persistence.Column given value, if not present falls back to jakarta.persistence.Column.
     *
     * @param annotationMetadata the annotation metadata
     * @param value the annotation value to be looked at
     * @return OptionalInt for given annotation value
     */
    static OptionalInt findPersistenceColumnValue(AnnotationMetadata annotationMetadata, String value) {
        String annotationName = "javax.persistence.Column";
        OptionalInt optionalInt = annotationMetadata.intValue(annotationName, value);
        if (optionalInt.isEmpty()) {
            annotationName = "jakarta.persistence.Column";
            optionalInt = annotationMetadata.intValue(annotationName, value);
        }
        return optionalInt;
    }

    /**
     * Determines whether a spatial property should use the database geography type.
     * An explicit column definition takes precedence over the coordinate reference system type.
     *
     * @param annotationMetadata The property annotation metadata
     * @return {@code true} if the property should use geography storage
     */
    static boolean isGeography(AnnotationMetadata annotationMetadata) {
        Optional<String> definition = annotationMetadata.stringValue(MappedProperty.class, "definition");
        if (definition.isPresent()) {
            return isGeographyDefinition(definition.get());
        }
        return annotationMetadata.enumValue(Srid.class, "type", Srid.CrsType.class)
            .orElse(Srid.CrsType.PROJECTED) == Srid.CrsType.GEOGRAPHIC;
    }

    /**
     * @param definition The database column definition
     * @return {@code true} if the definition specifies geography storage
     */
    static boolean isGeographyDefinition(String definition) {
        return definition.toLowerCase(Locale.ROOT).contains("geography");
    }

    /**
     * Is the given association a foreign key reference that requires a join table.
     *
     * @param association The association.
     * @return True if it is.
     */
    static boolean isForeignKeyWithJoinTable(Association association) {
        if (!association.isForeignKey()) {
            return false;
        }
        if (association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isPresent()) {
            return false;
        }
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = association.getAnnotationMetadata().getAnnotation(JoinColumns.class);
        return joinColumnsAnnotationValue == null || CollectionUtils.isEmpty(joinColumnsAnnotationValue.getAnnotations("value"));
    }

    /**
     * Checks whether a given property is considered generated within the context of the association path.
     *
     * A property is considered generated if it is annotated with {@link io.micronaut.data.annotation.GeneratedValue} and its owner is either the same as the given entity or is an embeddable entity.
     *
     * @param property     the persistent property to check
     * @param associations the association path leading to the property (can be empty)
     * @return true if the property is generated for this context, false otherwise
     */
    static boolean isGeneratedProperty(PersistentProperty property, List<Association> associations) {
        boolean generated = property.isGenerated();
        if (generated) {
            // If this property is an identity of the associated entity being referenced,
            // treat it as NOT generated so we can set the FK value.
            if (generated && CollectionUtils.isNotEmpty(associations)) {
                Association last = associations.get(associations.size() - 1);
                PersistentEntity assocEntity = last.getAssociatedEntity();
                if (assocEntity != null && assocEntity.getIdentityProperties().contains(property)) {
                    generated = false;
                }
            }
        }
        return generated;
    }
}
