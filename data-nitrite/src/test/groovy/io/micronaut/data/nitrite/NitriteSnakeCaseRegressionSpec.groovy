package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Sort
import io.micronaut.data.nitrite.model.SnakeEntity
import io.micronaut.data.nitrite.repository.SnakeEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NitriteSnakeCaseRegressionSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run([
        'datasources.default.db-file': 'snake_regression.db',
        'datasources.default.package': 'io.micronaut.data.nitrite.model'
    ])

    @Shared
    SnakeEntityRepository repository = context.getBean(SnakeEntityRepository)

    void "test native projection for camelCase mapped to snake_case"() {
        given:
        SnakeEntity entity1 = new SnakeEntity()
        entity1.sessionId = "session-1"
        entity1.level = 2
        
        SnakeEntity entity2 = new SnakeEntity()
        entity2.sessionId = "session-2"
        entity2.level = 2
        
        repository.saveAll([entity1, entity2])

        when:
        List<String> sessionIds = repository.findSessionIdByLevel(2)

        then:
        sessionIds.size() == 2
        sessionIds.contains("session-1")
        sessionIds.contains("session-2")
        sessionIds[0] instanceof String

        cleanup:
        repository.deleteAll()
    }

    void "test cursored pagination sorted by a persisted (snake_case) name resolves the Java property"() {
        given:
        SnakeEntity entity1 = new SnakeEntity()
        entity1.sessionId = "session-1"
        entity1.level = 1

        SnakeEntity entity2 = new SnakeEntity()
        entity2.sessionId = "session-2"
        entity2.level = 2

        repository.saveAll([entity1, entity2])

        when: "sorting by the persisted field name (session_id), not the Java property name (sessionId)"
        def pageable = CursoredPageable.from(10, Sort.of(Sort.Order.asc("session_id")))
        CursoredPage<SnakeEntity> page = (CursoredPage<SnakeEntity>) repository.findAll(pageable)

        then:
        page.content.size() == 2
        page.content*.sessionId == ["session-1", "session-2"]

        cleanup:
        repository.deleteAll()
    }
}
