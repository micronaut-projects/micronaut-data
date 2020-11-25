package io.micronaut.data.jdbc.oraclexe


import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
@Ignore("Fails with: Invalid column type - no idea what's wrong here")
class OracleXEUUIDSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    OracleXEUuidRepository repository = applicationContext.getBean(OracleXEUuidRepository)

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
