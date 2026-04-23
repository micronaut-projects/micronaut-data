package io.micronaut.data.jdbc.sqlite.one2many;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.JavaSQLiteDBProperties;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2many")
class OneToManyHierarchicalTest {

    @Inject
    TestEntityRepository testEntityRepository;

    @Inject
    TestHierarchyEntityRepository testHierarchyEntityRepository;

    @Test
    void testOneToManyParentChildHierarchy() {
        TestEntity testEntity = new TestEntity();
        testEntity.setCode("code1");
        TestEntity testEntity2 = new TestEntity();
        testEntity2.setCode("code2");

        TestHierarchyEntity testHierarchyEntity1 = new TestHierarchyEntity();
        testHierarchyEntity1.setParent(testEntity);
        TestHierarchyEntity testHierarchyEntity2 = new TestHierarchyEntity();
        testHierarchyEntity2.setParent(testEntity);
        TestHierarchyEntity testHierarchyEntity3 = new TestHierarchyEntity();
        testHierarchyEntity3.setChild(testEntity);
        testHierarchyEntity3.setParent(testEntity2);

        testEntityRepository.saveAll(List.of(testEntity, testEntity2));
        testHierarchyEntityRepository.saveAll(List.of(testHierarchyEntity1, testHierarchyEntity2, testHierarchyEntity3));

        Optional<TestEntity> optTestEntity = testEntityRepository.findById(testEntity.getId());
        Optional<TestHierarchyEntity> optTestHierarchyEntity = testHierarchyEntityRepository.findById(testHierarchyEntity1.getHierarchyId());

        assertTrue(optTestEntity.isPresent());
        TestEntity loadedTestEntity = optTestEntity.orElseThrow();
        assertEquals(2, loadedTestEntity.getParents().size());
        assertEquals(1, loadedTestEntity.getChildren().size());

        assertTrue(optTestHierarchyEntity.isPresent());
        TestHierarchyEntity loadedTestHierarchyEntity = optTestHierarchyEntity.orElseThrow();
        assertNotNull(loadedTestHierarchyEntity.getParent());
        assertFalse(loadedTestHierarchyEntity.getChild() != null);

        List<TestEntity> testEntities = testEntityRepository.findAll(Specifications.getChildrenByParentCodeSpecification("code2"));
        assertEquals(1, testEntities.size());
        assertEquals(testEntity.getCode(), testEntities.getFirst().getCode());

        testEntities = testEntityRepository.findAll(Specifications.getChildrenByParentIdSpecification(testEntity2.getId()));
        assertEquals(1, testEntities.size());
        assertEquals(testEntity.getId(), testEntities.getFirst().getId());
    }
}

@MappedEntity("test_main_entity")
class TestEntity {
    @Id
    @GeneratedValue
    private UUID id;
    private String code;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    private List<TestHierarchyEntity> parents;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "child")
    private List<TestHierarchyEntity> children;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getCode() {
        return code;
    }

    void setCode(String code) {
        this.code = code;
    }

    List<TestHierarchyEntity> getParents() {
        return parents;
    }

    void setParents(List<TestHierarchyEntity> parents) {
        this.parents = parents;
    }

    List<TestHierarchyEntity> getChildren() {
        return children;
    }

    void setChildren(List<TestHierarchyEntity> children) {
        this.children = children;
    }
}

@MappedEntity("test_hierarchy_entity")
class TestHierarchyEntity {
    @Id
    @GeneratedValue
    private UUID hierarchyId;
    @Nullable
    @MappedProperty("parent_id")
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    private TestEntity parent;
    @Nullable
    @MappedProperty("child_id")
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    private TestEntity child;

    UUID getHierarchyId() {
        return hierarchyId;
    }

    void setHierarchyId(UUID hierarchyId) {
        this.hierarchyId = hierarchyId;
    }

    TestEntity getParent() {
        return parent;
    }

    void setParent(TestEntity parent) {
        this.parent = parent;
    }

    TestEntity getChild() {
        return child;
    }

    void setChild(TestEntity child) {
        this.child = child;
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface TestHierarchyEntityRepository extends CrudRepository<TestHierarchyEntity, UUID> {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface TestEntityRepository extends CrudRepository<TestEntity, UUID>, JpaSpecificationExecutor<TestEntity> {

    @Join("parents")
    @Join("children")
    @Override
    Optional<TestEntity> findById(@NonNull UUID uuid);
}

final class Specifications {
    private Specifications() {
    }

    static PredicateSpecification<TestEntity> getChildrenByParentIdSpecification(UUID id) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.join("children").join("parent").get("id"), id);
    }

    static PredicateSpecification<TestEntity> getChildrenByParentCodeSpecification(String code) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.join("children").join("parent").get("code"), code);
    }
}
