package io.micronaut.data.document.mongodb

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.Id
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoRuntimeSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    TestContentRepository contentRepository = applicationContext.getBean(TestContentRepository)

    def cleanup() {
        contentRepository.deleteAll()
    }

    void 'test runtime serialization'() {
        given:
            TestContent testContent = new TestContent(
                    id: 'test',
                    category: [
                            one  : 'one',
                            two  : 2,
                            three: false
                    ]
            )

        when:
            contentRepository.save(testContent)
        then:
            contentRepository.findById('test').get().category == testContent.category
    }

}

@CompileStatic
@MongoRepository
interface TestContentRepository extends CrudRepository<TestContent, String> {
}

@CompileStatic
@Serdeable
@MappedEntity
@Introspected
class TestContent {

    @Id
    String id

    BigDecimal number


    String propertyId = null    // Property name.


    Map category = null
}
