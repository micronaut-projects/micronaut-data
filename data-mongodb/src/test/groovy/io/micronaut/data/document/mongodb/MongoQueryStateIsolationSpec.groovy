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
package io.micronaut.data.document.mongodb

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.DeleteOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.mongodb.annotation.MongoAggregateQuery
import io.micronaut.data.mongodb.annotation.MongoCollation
import io.micronaut.data.mongodb.annotation.MongoDeleteOptions
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions
import io.micronaut.data.mongodb.operations.options.MongoFindOptions
import io.micronaut.data.mongodb.repository.MongoQueryExecutor
import io.micronaut.data.repository.CrudRepository
import org.bson.BsonDocument
import org.bson.BsonString
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Per-invocation values (dynamic sort, collation, filter) must not leak into the stored query's cached
 * pipeline and options, or into options objects passed in by the caller.
 */
class MongoQueryStateIsolationSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    RankedRepository rankedRepository = applicationContext.getBean(RankedRepository)

    @Shared
    CollatedRankedRepository collatedRankedRepository = applicationContext.getBean(CollatedRankedRepository)

    @Shared
    OptionsRankedRepository optionsRankedRepository = applicationContext.getBean(OptionsRankedRepository)

    def setup() {
        rankedRepository.saveAll([
            new Ranked(name: "a", age: 1, rank: 1),
            new Ranked(name: "a", age: 2, rank: 2),
            new Ranked(name: "b", age: 3, rank: 3)
        ])
    }

    def cleanup() {
        rankedRepository.deleteAll()
    }

    // The dynamic sort keys are merged into the pipeline's own $sort stage after its keys, so they only
    // order rows that the pipeline's sort leaves tied (here: the two rows named "a")

    void "a dynamic sort does not change the cached aggregation pipeline"() {
        when: "effective sort {name: 1, age: -1}"
            def first = rankedRepository.sortedByName(Sort.of(Sort.Order.desc("age")))
        then:
            first*.rank == [2, 1, 3]

        when: "effective sort {name: 1, rank: 1}; age from the first call must not be left over"
            def second = rankedRepository.sortedByName(Sort.of(Sort.Order.asc("rank")))
        then:
            second*.rank == [1, 2, 3]

        when: "effective sorts {name: 1, rank: -1}, then {name: 1, rank: 1}, then {name: 1, rank: -1} again"
            def unsorted = rankedRepository.sortedByNameAndRankDescending(Sort.unsorted())
            def sorted = rankedRepository.sortedByNameAndRankDescending(Sort.of(Sort.Order.asc("rank")))
            def unsortedAgain = rankedRepository.sortedByNameAndRankDescending(Sort.unsorted())
        then:
            unsorted*.rank == [2, 1, 3]
            sorted*.rank == [1, 2, 3]
            unsortedAgain*.rank == [2, 1, 3]
    }

    void "a dynamic sort with null ordering does not leave its rank field in the cached pipeline"() {
        when: "effective sorts {name: 1, <null rank of age>: 1, age: 1}, then {name: 1, rank: -1}"
            rankedRepository.sortedByName(Sort.of(new Sort.Order("age", Sort.Order.Direction.ASC, false, Sort.Order.NullOrdering.FIRST)))
            def next = rankedRepository.sortedByName(Sort.of(Sort.Order.desc("rank")))
        then:
            next*.rank == [2, 1, 3]
    }

    void "caller supplied find options are not modified"() {
        given:
            def options = new MongoFindOptions()
        when:
            def found = collatedRankedRepository.findAll(options)
        then:
            found.size() == 3
            options.collation == null
            options.filter == null
    }

    void "caller supplied aggregation options are not modified"() {
        given:
            def options = new MongoAggregationOptions()
        when:
            def found = collatedRankedRepository.findAll([Aggregates.match(new BsonDocument())], options)
        then:
            found.size() == 3
            options.collation == null
    }

    void "caller supplied update options are not modified"() {
        given:
            def options = new UpdateOptions()
        when:
            def updated = collatedRankedRepository.updateAll(new BsonDocument("name", new BsonString("b")), Updates.set("rank", 30), options)
        then:
            updated == 1
            options.collation == null
    }

    void "caller supplied update options are combined with the repository's options"() {
        given: "a filter that only matches through the caller's let variable"
            def filter = BsonDocument.parse('{ $expr: { $eq: ["$name", "$$target"] } }')
            def options = new UpdateOptions().let(new BsonDocument("target", new BsonString("b")))
        when:
            def updated = optionsRankedRepository.updateAll(filter, Updates.set("rank", 30), options)
        then:
            updated == 1
    }

    void "caller supplied delete options are combined with the repository's options"() {
        given: "a filter that only matches through the caller's let variable"
            def filter = BsonDocument.parse('{ $expr: { $eq: ["$name", "$$target"] } }')
            def options = new DeleteOptions().let(new BsonDocument("target", new BsonString("b")))
        when:
            def deleted = optionsRankedRepository.deleteAll(filter, options)
        then:
            deleted == 1
    }

    void "caller supplied delete options are not modified"() {
        given:
            def options = new DeleteOptions()
        when:
            def deleted = collatedRankedRepository.deleteAll(new BsonDocument("name", new BsonString("b")), options)
        then:
            deleted == 1
            options.collation == null
    }
}

@MongoRepository
interface RankedRepository extends CrudRepository<Ranked, String> {

    @MongoAggregateQuery('[{$sort: {name: 1}}]')
    List<Ranked> sortedByName(Sort sort)

    @MongoAggregateQuery('[{$sort: {name: 1, rank: -1}}]')
    List<Ranked> sortedByNameAndRankDescending(Sort sort)
}

@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoRepository
interface CollatedRankedRepository extends CrudRepository<Ranked, String>, MongoQueryExecutor<Ranked> {
}

@MongoUpdateOptions(bypassDocumentValidation = true)
@MongoDeleteOptions
@MongoRepository
interface OptionsRankedRepository extends CrudRepository<Ranked, String>, MongoQueryExecutor<Ranked> {
}

@MappedEntity
class Ranked {
    @Id
    @GeneratedValue
    String id

    String name
    int age
    int rank
}
