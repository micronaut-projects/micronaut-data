package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.TableRatings
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class EscapeIdentifiersSpec extends Specification{

    @Inject
    @Shared
    H2TableRatingsRepository repository

    void "test save one"() {
        when:"one is saved"
        def ratings = new TableRatings(10)
        repository.save(ratings)

        then:"the instance is persisted"
        ratings.id != null
        repository.findById(ratings.id).isPresent()
        repository.existsById(ratings.id)
        repository.count() == 1
        repository.findAll().size() == 1
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = repository.save(new TableRatings(20))
        def p2 = repository.save(new TableRatings(30))
        def ratings = [p1,p2]

        then:"all are saved"
        ratings.every { it.id != null }
        ratings.every { repository.findById(it.id).isPresent() }
        repository.findAll().size() == 3
        repository.count() == 3
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def rating = repository.findByRating(20)

        then:"the person is not null"
        rating != null
        rating.rating == 20
        repository.findById(rating.id).isPresent()

        when:"the person is deleted"
        repository.deleteById(rating.id)

        then:"They are really deleted"
        !repository.findById(rating.id).isPresent()
        repository.count() == 2
    }

    void "test update one"() {
        when:"A person is retrieved"
        def ratings = repository.findByRating(10)

        then:"The person is present"
        ratings != null

        when:"The person is updated"
        repository.updateRating(ratings.id, 15)

        then:"the person is updated"
        repository.findByRating(10) == null
        repository.findByRating(15) != null
    }

}
