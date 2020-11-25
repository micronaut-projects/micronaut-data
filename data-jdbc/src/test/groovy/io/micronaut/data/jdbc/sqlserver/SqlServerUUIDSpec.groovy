package io.micronaut.data.jdbc.sqlserver


import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class SqlServerUUIDSpec extends Specification implements MSSQLTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    SqlServerUuidRepository repository = applicationContext.getBean(SqlServerUuidRepository)

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
        test = repository.findById(test.uuid).get()

        then:
        test.name == "John"

        cleanup:
        repository.deleteAll()
    }
}
