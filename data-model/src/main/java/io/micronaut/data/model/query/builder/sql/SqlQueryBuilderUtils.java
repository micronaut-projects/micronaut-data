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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
