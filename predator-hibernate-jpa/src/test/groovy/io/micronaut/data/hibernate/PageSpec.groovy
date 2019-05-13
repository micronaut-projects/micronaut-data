package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class PageSpec extends Specification {

    @Inject
    @Shared
    PersonRepository personRepository

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    def setupSpec() {

        List<Person> people = []
        5.times { num ->
            ('A'..'Z').each {
                people << new Person(name: it * 5 + num)
            }
        }

        crudRepository.saveAll(people)
    }


    void "test pageable list"() {
        when:"All the people are count"
        def count = crudRepository.count()

        then:"the count is correct"
        count == 130

        when:"10 people are paged"
        Page<Person> page = personRepository.list(Pageable.from(0, 10))

        then:"The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name.startsWith("A")
        page.content[1].name.startsWith("B")
        page.totalSize == 130
        page.totalPages == 13
        page.nextPageable().offset == 10
        page.nextPageable().size == 10

        when:"The next page is selected"
        page = personRepository.list(page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.content[0].name.startsWith("K")
    }

    void "test pageable findBy"() {
        when:"People are searched for"
        Page<Person> page = personRepository.findByNameLike("A%", Pageable.from(0, 10))

        then:"The page is correct"
        page.offset == 0
        page.totalSize == 5
        personRepository.findByNameLike("A%", page.nextPageable()).isEmpty()
    }
}
