package io.micronaut.data.model;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentEntityUtilsTest {

    private record Visit(List<Association> associations, PersistentProperty property) {
    }

    private static List<Visit> traverse(PersistentProperty property) {
        List<Visit> visits = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(property,
            (associations, p) -> visits.add(new Visit(List.copyOf(associations), p)));
        return visits;
    }

    @Test
    void visitsPlainProperty() {
        TestEntity entity = new TestEntity("Entity", null, null, new TestProperty("id"), new TestProperty("name"));
        TestProperty name = (TestProperty) entity.getPropertyByName("name");

        List<Visit> visits = traverse(name);

        assertEquals(1, visits.size());
        assertSame(name, visits.get(0).property());
        assertTrue(visits.get(0).associations().isEmpty());
    }

    @Test
    void traversesEmbeddedProperties() {
        TestEntity embeddedEntity = new TestEntity("Embedded", null, null, new TestProperty("e1"), new TestProperty("e2"));
        TestEntity entity = new TestEntity("Entity", null, null, new TestEmbedded("embedded", embeddedEntity));
        TestEmbedded embedded = (TestEmbedded) entity.getPropertyByName("embedded");

        List<Visit> visits = traverse(embedded);

        assertEquals(Set.of("e1", "e2"), visits.stream().map(v -> v.property().getName()).collect(toSet()));
        for (Visit visit : visits) {
            assertEquals(List.of(embedded), visit.associations());
        }
    }

    @Test
    void keepsEmbeddedPropertyWhenNotTraversingEmbedded() {
        TestEntity embeddedEntity = new TestEntity("Embedded", null, null, new TestProperty("e1"));
        TestEntity entity = new TestEntity("Entity", null, null, new TestEmbedded("embedded", embeddedEntity));
        TestEmbedded embedded = (TestEmbedded) entity.getPropertyByName("embedded");

        List<Visit> visits = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), embedded, false,
            (associations, p) -> visits.add(new Visit(List.copyOf(associations), p)));

        assertEquals(1, visits.size());
        assertSame(embedded, visits.get(0).property());
        assertTrue(visits.get(0).associations().isEmpty());
    }

    @Test
    void skipsForeignKeyAssociations() {
        TestEntity childEntity = new TestEntity("Child", null, null, new TestProperty("id"));
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("children", childEntity, true));
        TestAssociation children = (TestAssociation) entity.getPropertyByName("children");

        List<Visit> visits = traverse(children);

        assertTrue(visits.isEmpty());
    }

    @Test
    void visitsSingleIdentityOfAssociation() {
        TestProperty parentId = new TestProperty("id");
        TestEntity parent = new TestEntity("Parent", parentId, null, parentId, new TestProperty("name"));
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        List<Visit> visits = traverse(parentAssociation);

        assertEquals(1, visits.size());
        assertSame(parentId, visits.get(0).property());
        assertEquals(List.of(parentAssociation), visits.get(0).associations());
    }

    @Test
    void recursesIntoAssociationIdentity() {
        TestProperty idValue = new TestProperty("value");
        TestEntity idEntity = new TestEntity("Id", idValue, null, idValue);
        TestEntity parent = new TestEntity("Parent", null, null, new TestEmbedded("id", idEntity));
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");
        TestEmbedded embeddedId = (TestEmbedded) parent.getPropertyByName("id");

        List<Visit> visits = traverse(parentAssociation);

        assertEquals(1, visits.size());
        assertSame(idValue, visits.get(0).property());
        assertEquals(List.of(parentAssociation, embeddedId), visits.get(0).associations());
    }

    @Test
    void visitsEachCompositeIdentityProperty() {
        TestProperty tenantId = new TestProperty("tenantId");
        TestProperty refId = new TestProperty("refId");
        TestEntity parent = new TestEntity("Parent", null, new PersistentProperty[]{tenantId, refId}, tenantId, refId);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        List<Visit> visits = traverse(parentAssociation);

        assertEquals(Set.of("tenantId", "refId"), visits.stream().map(v -> v.property().getName()).collect(toSet()));
        for (Visit visit : visits) {
            assertEquals(List.of(parentAssociation), visit.associations());
        }
    }

    @Test
    void traversesPropertiesOfIdentityLessAssociation() {
        TestEntity valueObject = new TestEntity("Value", null, null, new TestProperty("key"), new TestProperty("data"));
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("value", valueObject, false));
        TestAssociation valueAssociation = (TestAssociation) entity.getPropertyByName("value");

        List<Visit> visits = traverse(valueAssociation);

        assertEquals(Set.of("key", "data"), visits.stream().map(v -> v.property().getName()).collect(toSet()));
        for (Visit visit : visits) {
            assertEquals(List.of(valueAssociation), visit.associations());
        }
    }

    @Test
    void stopsAtSelfReferencingIdentityLessAssociation() {
        TestEntity valueObject = new TestEntity("Value", null, null, new TestProperty("data"));
        valueObject.addProperty(new TestAssociation("self", valueObject, false));
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("value", valueObject, false));
        TestAssociation valueAssociation = (TestAssociation) entity.getPropertyByName("value");

        List<Visit> visits = traverse(valueAssociation);

        assertEquals(List.of("data"), visits.stream().map(v -> v.property().getName()).toList());
    }

    @Test
    void usesJoinColumnInsteadOfSingleIdentity() {
        TestProperty parentId = new TestProperty("id");
        TestProperty code = new TestProperty("code");
        TestEntity parent = new TestEntity("Parent", parentId, null, parentId, code);
        TestEntity entity = new TestEntity("Entity", null, null,
            new TestAssociation("parent", parent, false, joinColumns("code")));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        List<Visit> visits = traverse(parentAssociation);

        assertEquals(1, visits.size());
        assertSame(code, visits.get(0).property());
    }

    @Test
    void ignoresJoinColumnForCompositeIdentity() {
        TestProperty tenantId = new TestProperty("tenantId");
        TestProperty refId = new TestProperty("refId");
        TestEntity parent = new TestEntity("Parent", null, new PersistentProperty[]{tenantId, refId}, tenantId, refId);
        TestEntity entity = new TestEntity("Entity", null, null,
            new TestAssociation("parent", parent, false, joinColumns("tenantId")));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        List<Visit> visits = traverse(parentAssociation);

        assertEquals(List.of(tenantId, refId), visits.stream().map(Visit::property).toList());
    }

    @Test
    void embeddedAssociationIsAccessibleWithoutJoin() {
        TestEntity embeddedEntity = new TestEntity("Embedded", null, null, new TestProperty("e1"));
        TestEntity entity = new TestEntity("Entity", null, null, new TestEmbedded("embedded", embeddedEntity));
        TestEmbedded embedded = (TestEmbedded) entity.getPropertyByName("embedded");

        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(embedded, embeddedEntity.getPropertyByName("e1")));
    }

    @Test
    void foreignKeyAssociationIsNotAccessibleWithoutJoin() {
        TestProperty childId = new TestProperty("id");
        TestEntity child = new TestEntity("Child", childId, null, childId);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("children", child, true));
        TestAssociation children = (TestAssociation) entity.getPropertyByName("children");

        assertFalse(PersistentEntityUtils.isAccessibleWithoutJoin(children, childId));
    }

    @Test
    void onlyIdentityIsAccessibleWithoutJoin() {
        TestProperty parentId = new TestProperty("id");
        TestProperty name = new TestProperty("name");
        TestEntity parent = new TestEntity("Parent", parentId, null, parentId, name);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, parentId));
        assertFalse(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, name));
    }

    @Test
    void embeddedIdentityPropertyIsAccessibleWithoutJoin() {
        TestProperty idValue = new TestProperty("value");
        TestEntity idEntity = new TestEntity("Id", idValue, null, idValue);
        TestEmbedded embeddedId = new TestEmbedded("id", idEntity);
        TestEntity parent = new TestEntity("Parent", embeddedId, null, embeddedId);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, idValue));
    }

    @Test
    void eachCompositeIdentityPropertyIsAccessibleWithoutJoin() {
        TestProperty tenantId = new TestProperty("tenantId");
        TestProperty refId = new TestProperty("refId");
        TestProperty name = new TestProperty("name");
        TestEntity parent = new TestEntity("Parent", null, new PersistentProperty[]{tenantId, refId}, tenantId, refId, name);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, tenantId));
        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, refId));
        assertFalse(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, name));
    }

    @Test
    void identityLessAssociationPropertiesAreAccessibleWithoutJoin() {
        TestProperty data = new TestProperty("data");
        TestEntity valueObject = new TestEntity("Value", null, null, data);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("value", valueObject, false));
        TestAssociation valueAssociation = (TestAssociation) entity.getPropertyByName("value");

        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(valueAssociation, data));
        assertFalse(PersistentEntityUtils.isAccessibleWithoutJoin(valueAssociation, new TestProperty("other")));
    }

    @Test
    void nestedEmbeddedPropertyOfIdentityLessAssociationIsAccessibleWithoutJoin() {
        TestProperty street = new TestProperty("street");
        TestEntity addressEntity = new TestEntity("Address", null, null, street);
        TestEmbedded address = new TestEmbedded("address", addressEntity);
        TestEntity valueObject = new TestEntity("Value", null, null, address);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("value", valueObject, false));
        TestAssociation valueAssociation = (TestAssociation) entity.getPropertyByName("value");

        // traversal reaches the nested leaf, so accessibility has to agree with it
        assertEquals(List.of(street), traverse(valueAssociation).stream().map(Visit::property).toList());
        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(valueAssociation, street));
    }

    @Test
    void nestedEmbeddedIdentityPropertyIsAccessibleWithoutJoin() {
        TestProperty value = new TestProperty("value");
        TestEntity innerEntity = new TestEntity("Inner", null, null, value);
        TestEmbedded inner = new TestEmbedded("inner", innerEntity);
        TestEntity idEntity = new TestEntity("Id", null, null, inner);
        TestEmbedded embeddedId = new TestEmbedded("id", idEntity);
        TestEntity parent = new TestEntity("Parent", embeddedId, null, embeddedId);
        TestEntity entity = new TestEntity("Entity", null, null, new TestAssociation("parent", parent, false));
        TestAssociation parentAssociation = (TestAssociation) entity.getPropertyByName("parent");

        assertEquals(List.of(value), traverse(parentAssociation).stream().map(Visit::property).toList());
        assertTrue(PersistentEntityUtils.isAccessibleWithoutJoin(parentAssociation, value));
    }

    private static AnnotationMetadata joinColumns(String... referencedColumnNames) {
        List<AnnotationValue<JoinColumn>> joinColumns = Arrays.stream(referencedColumnNames)
            .map(columnName -> AnnotationValue.builder(JoinColumn.class)
                .member("referencedColumnName", columnName)
                .build())
            .toList();
        Map<String, Map<CharSequence, Object>> annotations =
            Map.of(JoinColumns.class.getName(), Map.of(AnnotationMetadata.VALUE_MEMBER, joinColumns));
        return new DefaultAnnotationMetadata(annotations, Collections.emptyMap(), Collections.emptyMap(),
            annotations, Collections.emptyMap());
    }

    static final class TestEntity extends AbstractPersistentEntity {

        private final String name;
        private final PersistentProperty identity;
        private final PersistentProperty[] compositeIdentity;
        private final List<PersistentProperty> properties;

        TestEntity(String name, PersistentProperty identity, PersistentProperty[] compositeIdentity, PersistentProperty... properties) {
            super(new AnnotationMetadataProvider() {
                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return AnnotationMetadata.EMPTY_METADATA;
                }
            });
            this.name = name;
            this.identity = identity;
            this.compositeIdentity = compositeIdentity;
            this.properties = new ArrayList<>(List.of(properties));
            for (PersistentProperty property : properties) {
                own(property);
            }
        }

        TestEntity addProperty(PersistentProperty property) {
            properties.add(property);
            own(property);
            return this;
        }

        private void own(PersistentProperty property) {
            if (property instanceof TestProperty testProperty) {
                testProperty.owner = this;
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasCompositeIdentity() {
            return compositeIdentity != null;
        }

        @Override
        public boolean hasIdentity() {
            return identity != null;
        }

        @Override
        public PersistentProperty getIdentity() {
            return identity;
        }

        @Override
        public PersistentProperty[] getCompositeIdentity() {
            return compositeIdentity;
        }

        @Override
        public PersistentProperty getVersion() {
            return null;
        }

        @Override
        public boolean hasVersion() {
            return false;
        }

        @Override
        public Collection<? extends PersistentProperty> getPersistentProperties() {
            return properties;
        }

        @Override
        public PersistentProperty getPropertyByName(String propertyName) {
            for (PersistentProperty property : properties) {
                if (property.getName().equals(propertyName)) {
                    return property;
                }
            }
            return null;
        }

        @Override
        public PersistentProperty getPropertyByNameIgnoreCase(String propertyName) {
            for (PersistentProperty property : properties) {
                if (property.getName().equalsIgnoreCase(propertyName)) {
                    return property;
                }
            }
            return null;
        }

        @Override
        public Collection<String> getPersistentPropertyNames() {
            return properties.stream().map(PersistentProperty::getName).toList();
        }

        @Override
        public boolean isOwningEntity(PersistentEntity owner) {
            return false;
        }

        @Override
        public PersistentEntity getParentEntity() {
            return null;
        }
    }

    static class TestProperty implements PersistentProperty {

        private final String name;
        private final AnnotationMetadata annotationMetadata;
        private TestEntity owner;

        TestProperty(String name) {
            this(name, AnnotationMetadata.EMPTY_METADATA);
        }

        TestProperty(String name, AnnotationMetadata annotationMetadata) {
            this.name = name;
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTypeName() {
            return String.class.getName();
        }

        @Override
        public PersistentEntity getOwner() {
            return owner;
        }

        @Override
        public boolean isAssignable(String type) {
            return String.class.getName().equals(type);
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public String getPersistedName() {
            return name;
        }
    }

    static class TestAssociation extends TestProperty implements Association {

        private final TestEntity associatedEntity;
        private final boolean foreignKey;

        TestAssociation(String name, TestEntity associatedEntity, boolean foreignKey) {
            this(name, associatedEntity, foreignKey, AnnotationMetadata.EMPTY_METADATA);
        }

        TestAssociation(String name, TestEntity associatedEntity, boolean foreignKey, AnnotationMetadata annotationMetadata) {
            super(name, annotationMetadata);
            this.associatedEntity = associatedEntity;
            this.foreignKey = foreignKey;
        }

        @Override
        public PersistentEntity getAssociatedEntity() {
            return associatedEntity;
        }

        @Override
        public boolean isForeignKey() {
            return foreignKey;
        }
    }

    static final class TestEmbedded extends TestAssociation implements Embedded {

        TestEmbedded(String name, TestEntity associatedEntity) {
            super(name, associatedEntity, false);
        }
    }
}
