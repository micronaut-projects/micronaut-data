package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
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
class OneToManyChildrenSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    ParentRepository parentRepository = applicationContext.getBean(ParentRepository)

    void 'test one-to-many hierarchy'() {
        given:
            def children = []
            Parent parent = new Parent(name: "parent", children: children)
            children.add new Child(name: "A", parent: parent)
            children.add new Child(name: "B", parent: parent)
            children.add new Child(name: "C", parent: parent)
        when:
            parentRepository.save(parent)
        then:
            parent.id
            parent.children.size() == 3
            parent.children.forEach {
                verifyAll(it) {
                    it.id
                    it.parent
                    it.name
                }
            }
        when:
            parent = parentRepository.findById(parent.id).get()
        then:
            parent.id
            parent.children.size() == 3
            parent.children.forEach {
                verifyAll(it) {
                    it.id
                    it.parent
                    it.name
                }
            }
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface ParentRepository extends CrudRepository<Parent, Long> {

    @Join(value = "children", type = Join.Type.FETCH)
    @Override
    Optional<Parent> findById(Long id);
}

@MappedEntity("x_product")
class Parent {
    String name
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    List<Child> children
    @Id
    @GeneratedValue
    Long id
}

@MappedEntity("x_child")
class Child {
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    Parent parent
    @Id
    @GeneratedValue
    Long id
}