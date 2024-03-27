package io.micronaut.data.jdbc.oraclexe.bool

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleXEBooleanTypeSpec extends Specification implements OracleTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    void "test persist oracle boolean type as null"() {
        given:
        def repository = applicationContext.getBean(OracleBooleanTestRepository)

        when:
        def result = repository.save(new BooleanTest(null, null))
        result = repository.findById(result.id()).orElse(null)

        then:
        result != null
        result.canBeNull() == null

        when:
        repository.update(new BooleanTest(result.id(), true))
        result = repository.findById(result.id()).orElse(null)

        then:
        result != null
        result.canBeNull()

        when:
        repository.update(new BooleanTest(result.id(), null))
        result = repository.findById(result.id()).orElse(null)

        then:
        result != null
        result.canBeNull() == null

    }
}
