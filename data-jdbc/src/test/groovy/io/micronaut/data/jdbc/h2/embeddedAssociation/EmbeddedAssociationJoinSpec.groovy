package io.micronaut.data.jdbc.h2.embeddedAssociation

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.annotation.JoinTable
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

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
interface MainEntityRepository extends CrudRepository<MainEntity, Long> {

    @Join(value = "assoc", type = Join.Type.FETCH)
    @Join(value = "em.assoc", type = Join.Type.FETCH)
    @Override
    Optional<MainEntity> findById(Long aLong)
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