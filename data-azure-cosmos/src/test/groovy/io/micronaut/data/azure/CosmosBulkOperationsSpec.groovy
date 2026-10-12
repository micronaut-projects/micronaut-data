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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.azure.entities.bulk.BulkDoc
import io.micronaut.data.azure.repositories.BulkDocRepository
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

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    BulkDocRepository repository = context.getBean(BulkDocRepository)

    @Override
    Map<String, String> getDbInitProperties() {
        return [
                'azure.cosmos.database.packages'      : 'io.micronaut.data.azure.entities.bulk',
                'azure.cosmos.database.update-policy' : 'CREATE_IF_NOT_EXISTS'
        ]
    }

    def cleanup() {
        repository.deleteAll()
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
