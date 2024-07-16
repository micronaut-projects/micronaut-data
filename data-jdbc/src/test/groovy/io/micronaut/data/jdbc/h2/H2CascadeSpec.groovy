package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification


@MicronautTest
@H2DBProperties
@Property(name = "datasources.default.packages", value = "io.micronaut.data.jdbc.h2")
class H2CascadeSpec extends Specification {

    @Inject
    @Shared
    CascadeEntityRepository repository

    void "test cascade save"() {
        when:
        def entityA = new CascadeSubEntityA(null, 1, null)
        def entityB = new CascadeSubEntityB(null, 2, null)
        def entity = new CascadeEntity(null, List.of(entityA), List.of(entityB))
        entity = repository.save(entity)
        def opt = repository.findById(entity.id)
        then:
        opt.present
        def loadedEntity = opt.get()
        loadedEntity.subEntityAs.size() == 1
        loadedEntity.subEntityBs.size() == 1
    }
}
