package io.micronaut.data.jdbc.h2.embeddedAssociation

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.GenericRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.tck.entities.Order
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

import javax.sql.DataSource

@MicronautTest
@H2DBProperties
class EmbeddedAssociationJoinSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    MainEntityRepository mainEntityRepository = applicationContext.getBean(MainEntityRepository)

    @Shared
    @Inject
    OneMainEntityRepository oneMainEntityRepository = applicationContext.getBean(OneMainEntityRepository)

    @Shared
    @Inject
    OneMainEntityEmRepository oneMainEntityEmRepository = applicationContext.getBean(OneMainEntityEmRepository)

    @Shared
    @Inject
    MyMainEntityRepository myMainEntityRepository = applicationContext.getBean(MyMainEntityRepository)

    void setup() {
        def dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource))
        def connection = dataSource.connection
        connection.prepareStatement("DROP TABLE IF EXISTS `my_main_entity`").execute()
        connection.prepareStatement("""
                                        CREATE TABLE `my_main_entity` (
                                            `id` bigint primary key not null,
                                            `value` text,
                                            `example` text,
                                            `part_text` text);
                                         """).execute()
    }

    void cleanup() {
        def dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource))
        def connection = dataSource.connection
        connection.prepareStatement("DROP TABLE IF EXISTS `my_main_entity`")
    }

    void 'test one-to-one update'() {
        given:
            ChildEntity child = new ChildEntity(name: "child")
            MainEntity main = new MainEntity(name: "test")
            main.child = child
            child.main = main
        when:
            mainEntityRepository.save(main)
            main.name = "diff-name"
            child.name = "diff-child"
            MainEntity updatedMain = mainEntityRepository.update(main)
        then:
            updatedMain.name == "diff-name"
            updatedMain.child.name == "diff-child"
    }

    void 'test many-to-many hierarchy'() {
        given:
            MainEntity e = new MainEntity(name: "test",
                    assoc: [
                    new MainEntityAssociation(name: "A"),
                    new MainEntityAssociation(name: "B"),
            ], em: new MainEmbedded(
                    assoc: [
                            new MainEntityAssociation(name: "C"),
                            new MainEntityAssociation(name: "D"),
                    ]
            ))
        when:
            mainEntityRepository.save(e)
            e = mainEntityRepository.findById(e.id).get()
            Sort.Order.Direction sortDirection = Sort.Order.Direction.ASC;
            Pageable pageable = Pageable.UNPAGED.order(new Sort.Order("child.name", sortDirection, false));
            mainEntityRepository.findAll(pageable).totalPages == 1
            PredicateSpecification<Order> predicate = null
             mainEntityRepository.findAllByCriteria(predicate, pageable).totalPages == 1
        then:
            e.id
            e.assoc.size() == 2
            e.assoc[0].name == "A"
            e.assoc[1].name == "B"
            e.em
            e.em.assoc.size() == 2
            e.em.assoc[0].name == "C"
            e.em.assoc[1].name == "D"
        when:
            mainEntityRepository.update(e)
            e = mainEntityRepository.findById(e.id).get()
        then:
            e.id
            e.assoc.size() == 2
            e.assoc[0].name == "A"
            e.assoc[1].name == "B"
            e.em.assoc.size() == 2
            e.em.assoc[0].name == "C"
            e.em.assoc[1].name == "D"
        when:
            def o = new OneMainEntity(one: e)
            o = oneMainEntityRepository.save(o)
            o = oneMainEntityRepository.findById(o.id).get()
        then:
            o.one.id
            o.one.assoc.size() == 2
            o.one.assoc[0].name == "A"
            o.one.assoc[1].name == "B"
            o.one.em.assoc.size() == 2
            o.one.em.assoc[0].name == "C"
            o.one.em.assoc[1].name == "D"
        when:
            def oem = new OneMainEntityEm(id: new EmId(one: e), name: "Embedded is crazy")
            oem = oneMainEntityEmRepository.save(oem)
            oem = oneMainEntityEmRepository.findById(oem.id).get()
        then:
            oem.name == "Embedded is crazy"
            oem.id.one.id
            oem.id.one.assoc.size() == 2
            oem.id.one.assoc[0].name == "A"
            oem.id.one.assoc[1].name == "B"
            oem.id.one.em.assoc.size() == 2
            oem.id.one.em.assoc[0].name == "C"
            oem.id.one.em.assoc[1].name == "D"
    }

    void 'test save/update embedded with @GeneratedValue'() {
        when:"should not update field 'example'"
        myMainEntityRepository.save(new MyMainEntity(id: 1L, example: "Test", value: "Val"))
        def persistedEntity = myMainEntityRepository.findById(1L).orElse(null)
        then:
        persistedEntity
        persistedEntity.value == "Val"
        !persistedEntity.example
        when:
        myMainEntityRepository.update(new MyMainEntity(id: 1L, example: "Changed", value: "Val-Changed"))
        def updatedEntity = myMainEntityRepository.findById(1L).orElse(null)
        then:
        updatedEntity
        updatedEntity.value == "Val-Changed"
        !updatedEntity.example

        when:"should not update field 'part_text'"
        myMainEntityRepository.save(new MyMainEntity(id: 2L, value: "Val1", part: new MyPart(text: "Test")))
        persistedEntity = myMainEntityRepository.findById(2L).orElse(null)
        then:
        persistedEntity
        persistedEntity.value == "Val1"
        !persistedEntity.part.text
        when:
        myMainEntityRepository.update(new MyMainEntity(id: 2L, value: "Val2", part: new MyPart(text: "Changed")))
        updatedEntity = myMainEntityRepository.findById(2L).orElse(null)
        then:
        updatedEntity
        updatedEntity.value == "Val2"
        !updatedEntity.part.text

        cleanup:
        myMainEntityRepository.deleteAll()
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface MainEntityRepository extends CrudRepository<MainEntity, Long>, JpaSpecificationExecutor<MainEntity> {

    @Join(value = "assoc", type = Join.Type.FETCH)
    @Join(value = "em.assoc", type = Join.Type.FETCH)
    @Override
    Optional<MainEntity> findById(Long aLong)

    @JoinSpecifications(@Join(value = "child", type = Join.Type.LEFT_FETCH))
    Page<MainEntity> findAll(Pageable pageable)

    @JoinSpecifications(@Join(value = "child", type = Join.Type.LEFT_FETCH))
    Page<MainEntity> findAllByCriteria(PredicateSpecification<Order> spec, Pageable pageable)
}

@JdbcRepository(dialect = Dialect.H2)
interface OneMainEntityRepository extends CrudRepository<OneMainEntity, Long> {

    @Join(value = "one", type = Join.Type.FETCH)
    @Join(value = "one.assoc", type = Join.Type.FETCH)
    @Join(value = "one.em.assoc", type = Join.Type.FETCH)
    @Override
    Optional<OneMainEntity> findById(Long aLong)
}

@Join(value = "id.one", type = Join.Type.FETCH)
@Join(value = "id.one.assoc", type = Join.Type.FETCH)
@Join(value = "id.one.em.assoc", type = Join.Type.FETCH)
@JdbcRepository(dialect = Dialect.H2)
interface OneMainEntityEmRepository extends CrudRepository<OneMainEntityEm, EmId> {
}

@MappedEntity
class OneMainEntity {
    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    MainEntity one
}

@MappedEntity
class OneMainEntityEm {
    @EmbeddedId
    EmId id

    String name
}

@Embeddable
class EmId {
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    MainEntity one
}

@MappedEntity
class MainEntity {
    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<MainEntityAssociation> assoc
    @Relation(value = Relation.Kind.EMBEDDED)
    MainEmbedded em
    @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "main", cascade = Relation.Cascade.ALL)
    ChildEntity child
    String name
}

@MappedEntity
class ChildEntity {
    @Id
    @GeneratedValue
    Long id
    @Relation(Relation.Kind.ONE_TO_ONE)
    MainEntity main
    String name
}

@Embeddable
class MainEmbedded {

    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<MainEntityAssociation> assoc

}

@MappedEntity
class MainEntityAssociation {
    @Id
    @GeneratedValue
    Long id
    String name
}

@MappedEntity("my_main_entity")
class MyMainEntity {

    @Id
    Long id

    @GeneratedValue
    String example

    String value

    @Relation(value = Relation.Kind.EMBEDDED)
    MyPart part = new MyPart()
}

@Embeddable
class MyPart {
    @GeneratedValue
    String text
}

@JdbcRepository(dialect = Dialect.H2)
interface MyMainEntityRepository extends GenericRepository<MyMainEntity, Long> {
    Optional<MyMainEntity> findById(Long id)

    MyMainEntity save(MyMainEntity entity)

    MyMainEntity update(MyMainEntity entity)

    void deleteAll()
}
