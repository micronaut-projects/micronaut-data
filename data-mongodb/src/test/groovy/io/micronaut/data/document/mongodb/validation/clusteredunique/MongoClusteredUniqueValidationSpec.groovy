package isolated.mongodb.validation.clusteredunique

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoClusteredIndex
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoClusteredUniqueValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['isolated.mongodb.validation.clusteredunique']
    }

    void 'fails fast when clustered unique is false'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('must be unique=true')
    }
}

@MongoRepository
interface InvalidClusteredUniqueEntityRepository extends CrudRepository<InvalidClusteredUniqueEntity, java.time.Instant> {
}

@MongoClusteredIndex(name = 'invalid_clustered_unique_idx', unique = false)
@MappedEntity('invalid_clustered_unique_entities')
class InvalidClusteredUniqueEntity {
    @Id
    java.time.Instant id

    String name
}
