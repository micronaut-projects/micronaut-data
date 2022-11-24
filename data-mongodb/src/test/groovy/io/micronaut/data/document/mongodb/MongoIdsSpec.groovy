/*
 * Copyright 2017-2020 original authors
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
import groovy.transform.CompileStatic
import io.micronaut.aop.InvocationContext
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.runtime.InsertOperation
import io.micronaut.data.model.runtime.StoredQuery
import io.micronaut.data.mongodb.operations.DefaultMongoRepositoryOperations
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonDocument
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import spock.lang.Specification

@MicronautTest(transactional = false)
class MongoIdsSpec extends Specification implements MongoTestPropertyProvider {

    @Inject
    DefaultMongoRepositoryOperations dbRepositoryOperations

    @Inject
    MongoClient mongoClient

    void "test ObjectId id"() {
        given:
            def database = mongoClient.getDatabase("test")
        when:
            def persisted = dbRepositoryOperations.persist(insertOperation(new EntityObjectId(value: "Xyz")))
        then:
            persisted.myId
        when:
            def one = dbRepositoryOperations.findOne(EntityObjectId, persisted.myId)
        then:
            one.myId == persisted.myId
        when:
            def found = database.getCollection("entities_object_ids", BsonDocument).find().first()
        then:
            found._id instanceof BsonObjectId
            found.value instanceof BsonString
            found.size() == 2
    }

    void "test simple UUID id"() {
        given:
            def database = mongoClient.getDatabase("test")
        when:
            def persisted = dbRepositoryOperations.persist(insertOperation(new EntityUUID(value: "Xyz")))
        then:
            persisted.myId
        when:
            def one = dbRepositoryOperations.findOne(EntityUUID, persisted.myId)
        then:
            one.myId == persisted.myId
            when:
            def found = database.getCollection("entities_simple_uuid", BsonDocument).find().first()
        then:
            found._id instanceof BsonString
            found.value instanceof BsonString
            found.size() == 2
    }

    void "test Long id"() {
        given:
            def database = mongoClient.getDatabase("test")
            def id = 123L
        when:
            dbRepositoryOperations.persist(insertOperation(new EntityLongId(myId: id, value: "Xyz")))
            def one = dbRepositoryOperations.findOne(EntityLongId, id)
        then:
            one.myId == id
        when:
            def found = database.getCollection("entities_long_ids", BsonDocument).find().first()
        then:
            found._id instanceof BsonInt64
            found.value instanceof BsonString
            found.size() == 2
    }

    void "test composite id"() {
        given:
            def database = mongoClient.getDatabase("test")
            def id = new CustomId(a: 1L, b: 2L)
        when:
            dbRepositoryOperations.persist(insertOperation(new EntityCustomId(myId: id, value: "Xyz")))
            def one = dbRepositoryOperations.findOne(EntityCustomId, id)
        then:
            one.myId.a == 1
            one.myId.b == 2
        when:
            def found = database.getCollection("entities_custom_id", BsonDocument).find().first()
        then:
            found._id instanceof BsonDocument
            (found._id as BsonDocument).get("a") instanceof BsonInt64
            (found._id as BsonDocument).get("b") instanceof BsonInt64
            found.value instanceof BsonString
            found.size() == 2
    }

    @MappedEntity(value = "entities_object_ids")
    static class EntityObjectId {

        @GeneratedValue
        @Id
        ObjectId myId

        String value

    }

    @MappedEntity(value = "entities_long_ids")
    static class EntityLongId {

        @Id
        Long myId

        String value

    }

    @MappedEntity(value = "entities_custom_id")
    static class EntityCustomId {

        @Id
        CustomId myId

        String value
    }

    @MappedEntity(value = "entities_simple_uuid")
    static class EntityUUID {

        @BsonRepresentation(BsonType.STRING)
        @AutoPopulated
        @Id
        UUID myId

        String value

    }

    @Serdeable
    static class CustomId implements Serializable {
        Long a
        Long b
    }

    @CompileStatic
    <T> InsertOperation<T> insertOperation(T instance) {
        return new InsertOperation<T>() {
            @Override
            T getEntity() {
                return instance
            }

            @Override
            Class<T> getRootEntity() {
                return instance.getClass() as Class<T>
            }

            @Override
            Class<?> getRepositoryType() {
                return Object.class
            }

            @Override
            StoredQuery<T, ?> getStoredQuery() {
                return null
            }

            @Override
            InvocationContext<?, ?> getInvocationContext() {
                return null
            }

            @Override
            String getName() {
                return instance.getClass().name
            }

            @Override
            ConvertibleValues<Object> getAttributes() {
                return ConvertibleValues.EMPTY
            }

            @Override
            Argument<T> getResultArgument() {
                return Argument.of(instance.getClass()) as Argument<T>
            }
        }
    }

}
