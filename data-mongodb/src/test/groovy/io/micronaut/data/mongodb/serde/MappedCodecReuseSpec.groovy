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
package io.micronaut.data.mongodb.serde

import com.mongodb.MongoClientSettings
import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.tck.entities.Author
import io.micronaut.data.document.tck.entities.Book
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * The entity codecs create the BSON codecs they need once and reuse them: after the first encode or decode, encoding
 * and decoding the same type looks up no more codecs in the registry. A codec created per call would look up a codec
 * for every BSON type each time.
 *
 * <p>The entities are the Java entities of the document TCK, whose relations and generated ids are mapped by the
 * annotation processor.</p>
 */
class MappedCodecReuseSpec extends Specification {

    // A context per feature: the serde registry keeps the deserializers it creates, bound to the registry they were
    // created with, so a shared context would let one feature count the lookups of another
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    CountingCodecRegistry registry
    DataCodecRegistry dataCodecRegistry

    def setup() {
        dataCodecRegistry = new DataCodecRegistry(null, context.getBean(DataSerdeRegistry), context.getBean(RuntimeEntityRegistry))
        registry = new CountingCodecRegistry(CodecRegistries.fromRegistries(dataCodecRegistry, MongoClientSettings.defaultCodecRegistry))
    }

    void "encoding an entity with a to-many relation looks up codecs only the first time"() {
        given:
            Codec<Author> codec = dataCodecRegistry.get(Author, registry)
            encode(codec, new Author(name: "first"))
            int lookups = registry.lookups
        when:
            def documents = (1..5).collect { encode(codec, new Author(name: "author $it")) }
        then:
            registry.lookups == lookups
            documents*.getString("name")*.value == (1..5).collect { "author $it".toString() }
            documents.every { !it.containsKey("books") }
    }

    void "decoding a to-one relation and generated ids looks up codecs only the first time"() {
        given:
            Codec<Book> codec = dataCodecRegistry.get(Book, registry)
            decode(codec, bookDocument(0))
            int lookups = registry.lookups
        when:
            def books = (1..5).collect { decode(codec, bookDocument(it)) }
        then:
            registry.lookups == lookups
            books*.title == (1..5).collect { "book $it".toString() }
            books*.author*.name == (1..5).collect { "author $it".toString() }
            books.every { it.id ==~ /[0-9a-f]{24}/ && it.author.id ==~ /[0-9a-f]{24}/ }
    }

    private static BsonDocument encode(Codec<Author> codec, Author author) {
        def document = new BsonDocument()
        codec.encode(new BsonDocumentWriter(document), author, EncoderContext.builder().build())
        return document
    }

    private static Book decode(Codec<Book> codec, BsonDocument document) {
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build())
    }

    // A book as the joined query returns it, with the author document embedded
    private static BsonDocument bookDocument(int i) {
        return new BsonDocument()
            .append("_id", new BsonObjectId(new ObjectId()))
            .append("title", new BsonString("book $i"))
            .append("totalPages", new BsonInt32(100 + i))
            .append("author", new BsonDocument()
                .append("_id", new BsonObjectId(new ObjectId()))
                .append("name", new BsonString("author $i")))
    }

    /**
     * Counts the codecs looked up through it.
     */
    static class CountingCodecRegistry implements CodecRegistry {

        final CodecRegistry delegate
        int lookups

        CountingCodecRegistry(CodecRegistry delegate) {
            this.delegate = delegate
        }

        @Override
        <T> Codec<T> get(Class<T> clazz) {
            lookups++
            return delegate.get(clazz)
        }

        @Override
        <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
            return delegate.get(clazz, registry)
        }
    }
}
