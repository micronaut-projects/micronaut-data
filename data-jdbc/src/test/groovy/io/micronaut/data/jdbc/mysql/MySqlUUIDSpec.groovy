package io.micronaut.data.jdbc.mysql


import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class MySqlUUIDSpec extends Specification implements MySQLTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    MySqlUuidRepository repository = applicationContext.getBean(MySqlUuidRepository)

    void 'test insert and update with UUID'() {
        when:
        def test = repository.save(new UuidTest("Fred"))

        def uuid = test.uuid
        then:
        uuid != null

        when:
        test = repository.findById(test.uuid).orElse(null)

        then:
        test.uuid != null
        test.uuid == uuid
        test.name == 'Fred'

        when:
        test.name = "John"
        test = repository.update(test)

        then:
        test.name == "John"

        when:
        test = repository.findById(test.uuid).orElse(null)

        then:
        test.name == "John"

        cleanup:
        repository.deleteAll()
    }
}
