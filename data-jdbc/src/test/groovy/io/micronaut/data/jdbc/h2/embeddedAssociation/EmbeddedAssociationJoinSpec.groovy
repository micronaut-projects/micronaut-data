package io.micronaut.data.jdbc.h2.embeddedAssociation

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.tck.entities.Order
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

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
