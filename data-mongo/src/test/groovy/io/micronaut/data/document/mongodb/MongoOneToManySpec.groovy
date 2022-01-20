package io.micronaut.data.document.mongodb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoOneToManySpec extends Specification implements MongoTestPropertyProvider {
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
                    // TODO:
//                    it.parent
                    it.name
                }
            }
        when:
            parent.children.forEach(it -> it.name = it.name + " mod!")
            parentRepository.update(parent)
            parent = parentRepository.findById(parent.id).get()
        then:
            parent.children.forEach {
                verifyAll(it) {
                    it.id
                    // TODO:
//                    it.parent
                    it.name.endsWith(" mod!")
                }
            }
    }

}

@MongoRepository
interface ParentRepository extends CrudRepository<Parent, String> {

    @Join("children")
    @Override
    Optional<Parent> findById(String id);
}

@MappedEntity("x_product")
class Parent {
    String name
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    List<Child> children
    @Id
    @GeneratedValue
    String id
}

@MappedEntity("x_child")
class Child {
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    Parent parent
    @Id
    @GeneratedValue
    String id
}