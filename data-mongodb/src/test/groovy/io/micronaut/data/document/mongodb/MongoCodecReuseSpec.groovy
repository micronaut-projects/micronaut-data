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
import io.micronaut.data.document.mongodb.repositories.MongoAuthorRepository
import io.micronaut.data.document.mongodb.repositories.MongoBookRepository
import io.micronaut.data.document.tck.entities.Author
import io.micronaut.data.document.tck.entities.Book
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.types.ObjectId
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Entities with relations and generated String ids written and read several times, so that the codecs the entity
 * codecs reuse between calls keep every result correct. The entities are the Java entities of the document TCK, whose
 * relations and generated ids are mapped by the annotation processor.
 */
class MongoCodecReuseSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    MongoAuthorRepository authorRepository = applicationContext.getBean(MongoAuthorRepository)

    @Shared
    MongoBookRepository bookRepository = applicationContext.getBean(MongoBookRepository)

    @Shared
    MongoClient mongoClient = applicationContext.getBean(MongoClient)

    @Shared
    List<Author> joinedAuthors

    @Shared
    List<Book> joinedBooks

    /**
     * Two authors with two books each, read by the joined query of every round.
     */
    def setupSpec() {
        joinedAuthors = authorRepository.saveAll((1..2).collect { new Author(name: "Codec reuse author $it") })
        joinedBooks = bookRepository.saveAll((1..2).collectMany { int a ->
            (1..2).collect { int b -> new Book(title: "Codec reuse book $a-$b", totalPages: 100, author: joinedAuthors[a - 1]) }
        })
    }

    def cleanupSpec() {
        bookRepository.deleteAll(joinedBooks)
        authorRepository.deleteAll(joinedAuthors)
    }

    void "entities with a to-many relation are stored without the relation on every save and update"() {
        given:
            def authors = authorRepository.saveAll((1..3).collect { new Author(name: "Codec update author $it") })
        when:
            (1..2).each { round ->
                authors.eachWithIndex { author, i -> author.name = "Codec update author ${i + 1} v$round".toString() }
                authorRepository.updateAll(authors)
            }
            def stored = authors.collect { author ->
                mongoClient.getDatabase("test").getCollection("author", BsonDocument)
                    .find(new BsonDocument("_id", new BsonObjectId(new ObjectId(author.id)))).first()
            }
        then:
            stored*.getString("name")*.value == (1..3).collect { "Codec update author $it v2".toString() }
            stored.every { !it.containsKey("books") }
        cleanup:
            authorRepository.deleteAll(authors)
    }

    /**
     * Every round reads each book with its author again, so that from the second round on the documents are decoded
     * by the codecs created in the first round.
     */
    void "round #round: #title is read with author #author and generated ids"() {
        when:
            Book book = bookRepository.findByTitle(title)
        then:
            book.id ==~ /[0-9a-f]{24}/
            book.author.name == author
            book.author.id ==~ /[0-9a-f]{24}/

        where:
            [round, title, author] << (1..3).collectMany { int round ->
                (1..2).collectMany { int a ->
                    (1..2).collect { int b -> [round, "Codec reuse book $a-$b".toString(), "Codec reuse author $a".toString()] }
                }
            }
    }
}
