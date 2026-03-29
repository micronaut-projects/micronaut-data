package io.micronaut.data.document.mongodb.validation.indexfailurepolicy

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoIndexFailurePolicyWarnContinueSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.indexfailurepolicy']
    }

    void 'starts when index initialization fails and policy is warn and continue'() {
        when:
        ApplicationContext context = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections'          : 'true',
                'micronaut.data.mongodb.create-indexes'              : 'true',
                'micronaut.data.mongodb.create-indexes-failure-policy': 'WARN_AND_CONTINUE'
        ])

        then:
        noExceptionThrown()
        context.getBean(IndexFailurePolicyEntityRepository)

        cleanup:
        context?.close()
    }
}

@MongoRepository
interface IndexFailurePolicyEntityRepository extends CrudRepository<IndexFailurePolicyEntity, String> {
}

@MongoCompoundIndex(
        name = 'index_failure_policy_invalid_path_idx',
        fields = [
                @MongoCompoundIndexField(value = 'missing', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('index_failure_policy_entities')
class IndexFailurePolicyEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
