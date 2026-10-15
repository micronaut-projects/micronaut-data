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

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.BsonDocument
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Entities with relations and generated String ids written and read several times through the same codecs, so that
 * codecs reused between calls keep every result correct.
 */
class MongoCodecReuseSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    CodecAuthorRepository authorRepository = applicationContext.getBean(CodecAuthorRepository)

    @Shared
    CodecNovelRepository novelRepository = applicationContext.getBean(CodecNovelRepository)

    @Shared
    MongoClient mongoClient = applicationContext.getBean(MongoClient)

    def cleanup() {
        novelRepository.deleteAll()
        authorRepository.deleteAll()
    }

    void "entities with a to-many relation are stored without the relation on every save and update"() {
        given:
            def authors = authorRepository.saveAll((1..3).collect { new CodecAuthor(name: "Author $it") })
        when:
            (1..2).each { round ->
                authors.eachWithIndex { author, i -> author.name = "Author ${i + 1} v$round".toString() }
                authorRepository.updateAll(authors)
            }
            def stored = mongoClient.getDatabase("test").getCollection("codec_reuse_authors", BsonDocument).find().into([])
        then:
            stored.size() == 3
            stored.every { !it.containsKey("novels") }
            authorRepository.findAll()*.name.sort() == ["Author 1 v2", "Author 2 v2", "Author 3 v2"]
    }

    void "to-one relations and generated ids are decoded on every read"() {
        given:
            def authors = authorRepository.saveAll((1..2).collect { new CodecAuthor(name: "Author $it") })
            def novels = novelRepository.saveAll(authors.collectMany { author ->
                (1..2).collect { new CodecNovel(title: "Novel ${author.name}-$it", author: author) }
            })
            def expected = novels.collectEntries { [(it.id): it.author.name] }
        expect:
            (1..3).every {
                def found = novelRepository.findByTitleStartsWith("Novel")
                found.size() == 4 &&
                    found.every { it.id ==~ /[0-9a-f]{24}/ && expected[it.id] == it.author.name && it.author.id != null }
            }
    }
}

@MongoRepository
interface CodecAuthorRepository extends CrudRepository<CodecAuthor, String> {
}

@MongoRepository
interface CodecNovelRepository extends CrudRepository<CodecNovel, String> {

    @Join("author")
    List<CodecNovel> findByTitleStartsWith(String prefix)
}

@MappedEntity("codec_reuse_authors")
class CodecAuthor {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "author")
    List<CodecNovel> novels
}

@MappedEntity("codec_reuse_novels")
class CodecNovel {
    @Id
    @GeneratedValue
    String id

    String title

    @Relation(Relation.Kind.MANY_TO_ONE)
    CodecAuthor author
}
