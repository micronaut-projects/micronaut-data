/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosException
import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.UniqueKey
import com.azure.cosmos.models.UniqueKeyPolicy
import io.micronaut.context.ApplicationContext
import io.micronaut.data.azure.entities.bulk.BulkDoc
import io.micronaut.data.azure.entities.bulk.UniqueNameDoc
import io.micronaut.data.azure.repositories.BulkDocRepository
import io.micronaut.data.azure.repositories.UniqueNameDocRepository
import io.micronaut.data.cosmos.common.CosmosAccessException
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Results of bulk operations: failed items, empty bulks and versions of documents with equal ids.
 */
class CosmosBulkOperationsSpec extends Specification implements AzureCosmosTestProperties {

    // The maximum size of a document is 2 MB
    private static final int TOO_LARGE = 3 * 1024 * 1024

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    BulkDocRepository repository = context.getBean(BulkDocRepository)

    @Shared
    UniqueNameDocRepository uniqueNameRepository = context.getBean(UniqueNameDocRepository)

    @Override
    Map<String, String> getDbInitProperties() {
        return [
                'azure.cosmos.database.packages'      : 'io.micronaut.data.azure.entities.bulk',
                'azure.cosmos.database.update-policy' : 'CREATE_IF_NOT_EXISTS'
        ]
    }

    def setupSpec() {
        // The container created from the entity has no unique key, so it is replaced by one that has it
        def containerProperties = new CosmosContainerProperties("uniquenamedoc", "/region")
        containerProperties.setUniqueKeyPolicy(new UniqueKeyPolicy().setUniqueKeys([new UniqueKey(["/name"])]))
        def databaseName = context.getBean(CosmosDatabaseConfiguration).databaseName
        def database = context.getBean(CosmosClient).getDatabase(databaseName)
        database.getContainer("uniquenamedoc").delete()
        database.createContainer(containerProperties)
    }

    def cleanup() {
        repository.deleteAll()
        uniqueNameRepository.deleteAll()
    }

    void "a bulk update with a stale version fails"() {
        given:
            repository.insertAll([new BulkDoc("1", "eu", "a"), new BulkDoc("2", "eu", "b")])
            def docs = repository.findByRegion("eu").sort { it.id }
        when: "the second document has a version that no longer matches"
            docs[0].name = "a2"
            docs[1].name = "b2"
            docs[1].version = UUID.randomUUID().toString()
            repository.updateAll(docs)
        then:
            thrown(OptimisticLockException)
            repository.findByRegion("eu").find { it.id == "2" }.name == "b"
    }

    void "a bulk delete with a stale version fails"() {
        given:
            repository.insertAll([new BulkDoc("1", "eu", "a"), new BulkDoc("2", "eu", "b")])
            def docs = repository.findByRegion("eu").sort { it.id }
        when:
            docs[1].version = UUID.randomUUID().toString()
            repository.deleteAll(docs)
        then:
            thrown(OptimisticLockException)
            repository.findByRegion("eu")*.id.contains("2")
    }

    void "a bulk insert of a document that already exists fails"() {
        given:
            repository.insert(new BulkDoc("1", "eu", "a"))
        when:
            repository.insertAll([new BulkDoc("1", "eu", "duplicate"), new BulkDoc("2", "eu", "b")])
        then:
            thrown(CosmosAccessException)
            repository.findById("1").get().name == "a"
    }

    void "a bulk insert of a document that is rejected fails"() {
        when: "a document is larger than the maximum document size"
            repository.insertAll([new BulkDoc("1", "eu", "a"), new BulkDoc("2", "eu", "x" * TOO_LARGE)])
        then:
            def e = thrown(CosmosAccessException)
            e.cause instanceof CosmosException
            e.cause.statusCode == 413
            !repository.findById("2").isPresent()
    }

    void "a bulk update by query of a document that is rejected fails"() {
        given:
            repository.insert(new BulkDoc("1", "eu", "a"))
        when: "the updated document is larger than the maximum document size"
            repository.updateAll(new UpdateSpecification<BulkDoc>() {
                @Override
                Predicate toPredicate(Root<BulkDoc> root, CriteriaUpdate<?> query, CriteriaBuilder criteriaBuilder) {
                    query.set("name", "x" * TOO_LARGE)
                    return criteriaBuilder.equal(root.get("name"), "a")
                }
            })
        then:
            def e = thrown(CosmosAccessException)
            e.cause instanceof CosmosException
            e.cause.statusCode == 413
            repository.findById("1").get().name == "a"
    }

    void "a bulk update by query of a document that violates a unique key fails"() {
        given:
            uniqueNameRepository.saveAll([new UniqueNameDoc("1", "eu", "a"), new UniqueNameDoc("2", "eu", "b")])
        when:
            uniqueNameRepository.updateAll(new UpdateSpecification<UniqueNameDoc>() {
                @Override
                Predicate toPredicate(Root<UniqueNameDoc> root, CriteriaUpdate<?> query, CriteriaBuilder criteriaBuilder) {
                    query.set("name", "a")
                    return criteriaBuilder.equal(root.get("name"), "b")
                }
            })
        then:
            def e = thrown(CosmosAccessException)
            e.message.contains("status code 409")
            uniqueNameRepository.findById("2").get().name == "b"
    }

    void "a bulk update and delete of a document that no longer exists fail as the update and delete of one document do"() {
        given:
            def saved = repository.insert(new BulkDoc("1", "eu", "a"))
            def missing = new BulkDoc("2", "eu", "b")
        when:
            repository.update(missing)
        then:
            thrown(CosmosAccessException)
        when:
            repository.delete(missing)
        then:
            thrown(CosmosAccessException)
        when:
            repository.updateAll([saved, missing])
        then:
            def e = thrown(CosmosAccessException)
            e.message.contains("status code 404")
        when:
            repository.deleteAll([saved, missing])
        then:
            e = thrown(CosmosAccessException)
            e.message.contains("status code 404")
            !repository.findById("1").isPresent()
    }

    void "a bulk update by query of a document that is not found is not counted"() {
        given:
            repository.insert(new BulkDoc("1", "eu", "a"))
        when: "the document is looked up in another partition, as its partition key is changed"
            def updated = repository.updateAll(new UpdateSpecification<BulkDoc>() {
                @Override
                Predicate toPredicate(Root<BulkDoc> root, CriteriaUpdate<?> query, CriteriaBuilder criteriaBuilder) {
                    query.set("region", "us")
                    return criteriaBuilder.equal(root.get("name"), "a")
                }
            })
        then:
            updated == 0
            repository.findById("1").get().region == "eu"
    }

    void "documents read by a list query have their version, so a concurrent change is detected"() {
        given:
            def saved = repository.insert(new BulkDoc("1", "eu", "a"))
        when:
            def fromList = repository.findByRegion("eu")[0]
            def fromFindAll = repository.findAll().find { it.id == "1" }
        then:
            fromList.version == saved.version
            fromFindAll.version == saved.version
        when: "the document changes after it was read"
            saved.name = "changed"
            repository.update(saved)
            fromList.name = "stale"
            repository.update(fromList)
        then:
            thrown(OptimisticLockException)
            repository.findById("1").get().name == "changed"
    }

    void "a bulk update and delete by query that match nothing return zero"() {
        given:
            repository.insert(new BulkDoc("1", "eu", "a"))
        expect:
            repository.updateAll(new UpdateSpecification<BulkDoc>() {
                @Override
                Predicate toPredicate(Root<BulkDoc> root, CriteriaUpdate<?> query, CriteriaBuilder criteriaBuilder) {
                    query.set("name", "changed")
                    return criteriaBuilder.equal(root.get("name"), "no such name")
                }
            }) == 0
            repository.deleteAll(new DeleteSpecification<BulkDoc>() {
                @Override
                Predicate toPredicate(Root<BulkDoc> root, CriteriaDelete<?> query, CriteriaBuilder criteriaBuilder) {
                    return criteriaBuilder.equal(root.get("name"), "no such name")
                }
            }) == 0
    }

    void "a bulk insert assigns each document its own version when ids are equal across partitions"() {
        given:
            def eu = new BulkDoc("1", "eu", "a")
            def us = new BulkDoc("1", "us", "b")
        when:
            repository.insertAll([eu, us])
        then:
            eu.version
            us.version
            eu.version == repository.findByRegion("eu")[0].version
            us.version == repository.findByRegion("us")[0].version
        when: "both can be updated in bulk with the versions they were given"
            eu.name = "a2"
            us.name = "b2"
            repository.updateAll([eu, us])
        then:
            repository.findByRegion("eu")[0].name == "a2"
            repository.findByRegion("us")[0].name == "b2"
            eu.version == repository.findByRegion("eu")[0].version
            us.version == repository.findByRegion("us")[0].version
    }
}
