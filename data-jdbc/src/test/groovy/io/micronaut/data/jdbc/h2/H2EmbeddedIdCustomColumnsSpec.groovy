package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.embedded.EmbeddedIdExample
import io.micronaut.data.jdbc.embedded.EmbeddedIdExampleId
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2EmbeddedIdCustomColumnsSpec extends Specification {

    @Inject
    H2EmbeddedIdExampleRepository repository

    void "test CRUD"() {
        when:
        EmbeddedIdExampleId id = new EmbeddedIdExampleId("a", "b")
        repository.save(new EmbeddedIdExample(id, "test"))

        def entity = repository.findById(id).orElse(null)

        then:
        entity != null
    }
}
