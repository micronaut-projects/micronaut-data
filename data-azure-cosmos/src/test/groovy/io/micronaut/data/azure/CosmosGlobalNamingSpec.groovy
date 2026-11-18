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
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.fasterxml.jackson.databind.node.ObjectNode
import io.micronaut.context.ApplicationContext
import io.micronaut.data.cosmos.operations.entities.PlainUserIdEntity
import io.micronaut.data.cosmos.operations.entities.PlainUserIdEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CosmosGlobalNamingSpec extends Specification implements AzureCosmosTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Override
    Map<String, String> getDbInitProperties() {
        return [
                'azure.cosmos.database.packages'            : 'io.micronaut.data.cosmos.operations.entities',
                'azure.cosmos.database.update-policy'       : 'CREATE_IF_NOT_EXISTS',
                'micronaut.serde.property-naming-strategy'  : 'SNAKE_CASE'
        ]
    }

    void "an id that is not named id keeps its field with a global naming strategy"() {
        given:
        def repository = context.getBean(PlainUserIdEntityRepository)
        def entity = new PlainUserIdEntity("user-1", "Fred")

        when:
        repository.save(entity)
        def stored = context.getBean(CosmosClient)
                .getDatabase("mydb")
                .getContainer("plain_user_id_entity")
                .queryItems("SELECT * FROM c", new CosmosQueryRequestOptions(), ObjectNode)
                .toList()

        then:
        stored.size() == 1
        stored[0].get("userId").asText() == "user-1"
        !stored[0].has("user_id")

        and:
        repository.findById("user-1").get() == entity
        repository.findByUserId("user-1").get() == entity
        repository.findAll().toList() == [entity]

        when:
        repository.deleteById("user-1")

        then:
        repository.findAll().toList().empty
    }
}
