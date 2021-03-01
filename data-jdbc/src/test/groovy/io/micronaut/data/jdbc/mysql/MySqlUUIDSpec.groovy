package io.micronaut.data.jdbc.mysql

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MySqlUUIDSpec extends Specification implements MySQLTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    MySqlUuidEntityRepository repository = applicationContext.getBean(MySqlUuidEntityRepository)

    void 'test insert with UUID'() {
        given:
            def entity = new MySqlUuidEntity()
            entity.id = UUID.randomUUID()
            entity.id2 = UUID.randomUUID()
            entity.name = "SPECIAL"
        when:
            MySqlUuidEntity test = repository.save(entity)
        then:
            test.id
        when:
            def test2 = repository.findById(test.id).get()
        then:
            test2.id == test.id
            test2.id2 == test.id2
            test2.name == 'SPECIAL'
        when: "update query with transform is used"
            def newUUID = UUID.randomUUID()
            repository.update(test.id, newUUID)
            test2 = repository.findById(test.id).get()
        then:
            test2.id == test.id
            test2.id2 == newUUID
            test2.name == 'SPECIAL'

        cleanup:
            repository.deleteAll()
    }

}
