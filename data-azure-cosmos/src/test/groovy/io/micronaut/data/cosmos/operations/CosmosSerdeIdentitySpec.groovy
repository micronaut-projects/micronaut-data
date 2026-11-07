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
package io.micronaut.data.cosmos.operations

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.data.cosmos.common.CosmosEntity
import io.micronaut.data.cosmos.operations.entities.ExplicitNameUserIdEntity
import io.micronaut.data.cosmos.operations.entities.PlainUserIdEntity
import io.micronaut.data.cosmos.operations.entities.RuntimeNamingUserIdEntity
import io.micronaut.data.cosmos.operations.entities.SnakeCaseUserIdEntity
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CosmosSerdeIdentitySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    ApplicationContext globalNaming = ApplicationContext.run(["micronaut.serde.property-naming-strategy": "SNAKE_CASE"])

    void "a global naming strategy does not rename the id field"() {
        when:
        def document = serialize(globalNaming, PlainUserIdEntity, new PlainUserIdEntity("user-1", "Fred"))

        then:
        keys(document) == ["id", "userId", "first_name"] as Set
        document.get("userId").asText() == "user-1"
        document.get("id").asText() == "user-1"

        and:
        deserialize(globalNaming, PlainUserIdEntity, document) == new PlainUserIdEntity("user-1", "Fred")
    }

    void "an existing document is read with a global naming strategy"() {
        given:
        def document = JsonNodeFactory.instance.objectNode()
                .put("id", "user-1")
                .put("userId", "user-1")
                .put("first_name", "Fred")

        expect:
        deserialize(globalNaming, PlainUserIdEntity, document) == new PlainUserIdEntity("user-1", "Fred")
    }

    void "a query result is read with a global naming strategy"() {
        given:
        def document = JsonNodeFactory.instance.objectNode()
                .put("id", "user-1")
                .put("userId", "user-1")
                .put("first_name", "Fred")
        def serde = globalNaming.getBean(CosmosSerde)
        def entity = globalNaming.getBean(RuntimeEntityRegistry).getEntity(PlainUserIdEntity)

        expect:
        serde.deserialize(serde.withSerializedIdentity(entity, document), Argument.of(PlainUserIdEntity)) == new PlainUserIdEntity("user-1", "Fred")
    }

    void "a naming strategy looked up at runtime does not rename the id field"() {
        when:
        def document = serialize(context, RuntimeNamingUserIdEntity, new RuntimeNamingUserIdEntity("user-1", "Fred"))

        then:
        keys(document) == ["id", "userId", "x_firstName"] as Set

        and:
        deserialize(context, RuntimeNamingUserIdEntity, document) == new RuntimeNamingUserIdEntity("user-1", "Fred")
    }

    void "a naming strategy resolved during annotation processing names the id field"() {
        when:
        def document = serialize(context, SnakeCaseUserIdEntity, new SnakeCaseUserIdEntity("user-1", "Fred"))

        then:
        keys(document) == ["id", "user_id", "first_name"] as Set

        and:
        deserialize(context, SnakeCaseUserIdEntity, document) == new SnakeCaseUserIdEntity("user-1", "Fred")
    }

    void "an explicit id name is kept with a global naming strategy"() {
        when:
        def document = serialize(globalNaming, ExplicitNameUserIdEntity, new ExplicitNameUserIdEntity("user-1", "Fred"))

        then:
        keys(document) == ["id", "user_ref", "first_name"] as Set

        and:
        deserialize(globalNaming, ExplicitNameUserIdEntity, document) == new ExplicitNameUserIdEntity("user-1", "Fred")
    }

    private static Set<String> keys(ObjectNode document) {
        return document.fieldNames().toList() as Set
    }

    private static <T> ObjectNode serialize(ApplicationContext context, Class<T> type, T value) {
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(type)
        CosmosEntity.create(entity, null)
        return context.getBean(CosmosSerde).serialize(entity, value, Argument.of(type))
    }

    private static <T> T deserialize(ApplicationContext context, Class<T> type, ObjectNode document) {
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(type)
        CosmosEntity.create(entity, null)
        return context.getBean(CosmosSerde).deserialize(entity, document, Argument.of(type))
    }
}
