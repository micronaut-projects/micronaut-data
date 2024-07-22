package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class OneToManyHierarchicalSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    TestEntityRepository testEntityRepository = applicationContext.getBean(TestEntityRepository)

    @Shared
    @Inject
    TestHierarchyEntityRepository testHierarchyEntityRepository = applicationContext.getBean(TestHierarchyEntityRepository)

    void 'test one-to-many parent child hierarchy'() {
        given:
        def testEntity = new TestEntity(code: "code1")
        def testEntity2 = new TestEntity(code: "code2")
        def testHierarchyEntity1 = new TestHierarchyEntity(parent: testEntity)
        def testHierarchyEntity2 = new TestHierarchyEntity(parent: testEntity)
        def testHierarchyEntity3 = new TestHierarchyEntity(child: testEntity, parent:  testEntity2)
        when:
        testEntityRepository.saveAll(List.of(testEntity, testEntity2))
        testHierarchyEntityRepository.saveAll(List.of(testHierarchyEntity1, testHierarchyEntity2, testHierarchyEntity3))
        def optTestEntity = testEntityRepository.findById(testEntity.id)
        def optTestHierarchyEntity = testHierarchyEntityRepository.findById(testHierarchyEntity1.hierarchyId)
        then:
        noExceptionThrown()
        optTestEntity.present
        def loadedTestEntity = optTestEntity.get()
        loadedTestEntity.parents.size() == 2
        loadedTestEntity.children.size() == 1
        optTestHierarchyEntity.present
        def loadedTestHierarchyEntity = optTestHierarchyEntity.get()
        loadedTestHierarchyEntity.parent
        !loadedTestHierarchyEntity.child
        when:
        def testEntities = testEntityRepository.findAll(Specifications.getChildrenByParentCodeSpecification("code2"))
        then:
        testEntities.size() == 1
        testEntities[0].code == testEntity.code
        when:
        testEntities = testEntityRepository.findAll(Specifications.getChildrenByParentIdSpecification(testEntity.id))
        then:
        testEntities.size() == 1
        testEntities[0].id == testEntity.id
    }

}

@MappedEntity(value = "test_main_entity")
class TestEntity {
    @Id
    @GeneratedValue
    UUID id

    String code

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    List<TestHierarchyEntity> parents

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "child")
    List<TestHierarchyEntity> children
}

@MappedEntity(value = "test_hierarchy_entity")
class TestHierarchyEntity {
    @Id
    @GeneratedValue
    UUID hierarchyId

    @Nullable
    @MappedProperty(value = "parent_id")
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    TestEntity parent

    @Nullable
    @MappedProperty(value = "child_id")
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    TestEntity child
}

@JdbcRepository(dialect = Dialect.H2)
interface TestHierarchyEntityRepository extends CrudRepository<TestHierarchyEntity, UUID> {
}

@JdbcRepository(dialect = Dialect.H2)
interface TestEntityRepository extends CrudRepository<TestEntity, UUID>, JpaSpecificationExecutor<TestEntity> {

    @Join("parents")
    @Join("children")
    @Override
    Optional<TestEntity> findById(@NonNull UUID uuid)
}

class Specifications {
    static PredicateSpecification<TestEntity> getChildrenByParentIdSpecification(UUID id) {
        return (root, criteriaBuilder) ->
                criteriaBuilder.equal(root.join("children").join("parent").get("id"), id)
    }

    static PredicateSpecification<TestEntity> getChildrenByParentCodeSpecification(String code) {
        return (root, criteriaBuilder) ->
                criteriaBuilder.equal(root.join("children").join("parent").get("code"), code)
    }
}
