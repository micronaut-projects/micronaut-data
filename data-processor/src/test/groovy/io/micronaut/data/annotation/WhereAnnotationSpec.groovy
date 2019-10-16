package io.micronaut.data.annotation

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Person

class WhereAnnotationSpec extends AbstractDataSpec {

    void "test build @Where definition with no parameters at type level"() {
        given:
        def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.tck.entities.Person;

@Where("person_.age > 18")
@Repository
interface TestRepository extends CrudRepository<Person, Long> {

}
''')
        expect:
        repository.getRequiredMethod("findAll")
                .stringValue(Query).get() == 'SELECT person_ FROM io.micronaut.data.tck.entities.Person AS person_ WHERE (person_.age > 18)'
        repository.getRequiredMethod("findById", Long)
            .stringValue(Query).get() == "SELECT person_ FROM $Person.name AS person_ WHERE (person_.id = :p1 AND (person_.age > 18))"
        repository.getRequiredMethod("deleteById", Long)
                .stringValue(Query).get() == "DELETE $Person.name  AS person_ WHERE (person_.id = :p1 AND (person_.age > 18))"
    }
}
