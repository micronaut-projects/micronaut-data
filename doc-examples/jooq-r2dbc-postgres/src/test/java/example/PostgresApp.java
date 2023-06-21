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
package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
@Property(name = "r2dbc.datasources.default.db-type", value = "postgres")
@Property(name = "jooq.r2dbc-datasources.default.sql-dialect", value = "postgres")
@Property(name = "r2dbc.datasources.default.options.driver", value = "pool")
@Property(name = "r2dbc.datasources.default.options.protocol", value = "postgresql")
@Property(name = "r2dbc.datasources.default.options.connectTimeout", value = "PT1M")
@Property(name = "r2dbc.datasources.default.options.statementTimeout", value = "PT1M")
@Property(name = "r2dbc.datasources.default.options.lockTimeout", value = "PT1M")
@Property(name = "test-resources.containers.postgres.image-name", value = "postgres:10")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgresApp implements TestPropertyProvider {

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("micronaut.test.resources.scope", getClass().getName());
        return properties;
    }

    @BeforeAll
    public void init() {
        HttpResponse<Void> response = client.toBlocking().exchange(HttpRequest.GET("/init"));
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @AfterAll
    public void cleanup() {
        HttpResponse<Void> response = client.toBlocking().exchange(HttpRequest.GET("/destroy"));
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    void shouldFetchOwners() {
        List<OwnerDto> results = client.toBlocking().retrieve(HttpRequest.GET("/owners"), Argument.listOf(OwnerDto.class));
        assertEquals(2, results.size());
        assertEquals("Fred", results.get(0).getName());
        assertEquals("Barney", results.get(1).getName());
    }

    @Test
    void shouldFetchOwnerByName() {
        Map result = client.toBlocking().retrieve(HttpRequest.GET("/owners/Fred"), Map.class);
        assertEquals("Fred", result.get("name"));
    }

    @Test
    void shouldFetchPets() {
        List<PetDto> results = client.toBlocking().retrieve(HttpRequest.GET("/pets"), Argument.listOf(PetDto.class));
        assertEquals(3, results.size());
        assertEquals("Dino", results.get(0).getName());
        assertEquals("Fred", results.get(0).getOwner().getName());
        assertEquals("Baby Puss", results.get(1).getName());
        assertEquals("Fred", results.get(1).getOwner().getName());
        assertEquals("Hoppy", results.get(2).getName());
        assertEquals("Barney", results.get(2).getOwner().getName());
    }

    @Test
    void shouldFetchPetByName() {
        Map result = client.toBlocking().retrieve(HttpRequest.GET("/pets/Dino"), Map.class);
        assertEquals("Dino", result.get("name"));
        assertEquals("Fred", ((Map) result.get("owner")).get("name"));
    }

    @Test
    void shouldFetchPetsParallel() {
        List<List<PetDto>> resultsList = Flux.range(1, 1000)
            .flatMap(it -> client.retrieve(HttpRequest.GET("/pets"), Argument.listOf(PetDto.class)))
            .collectList()
            .block();

        resultsList.forEach(it -> {
            assertEquals(3, it.size());
            assertEquals("Dino", it.get(0).getName());
            assertEquals("Fred", it.get(0).getOwner().getName());
            assertEquals("Baby Puss", it.get(1).getName());
            assertEquals("Fred", it.get(1).getOwner().getName());
            assertEquals("Hoppy", it.get(2).getName());
            assertEquals("Barney", it.get(2).getOwner().getName());
        });
    }

    @Test
    void shouldFetchPetByNameParallel() {
        List<Map> resultsList = Flux.range(1, 1000)
            .flatMap(it -> client.retrieve(HttpRequest.GET("/pets/Dino"), Map.class))
            .collectList()
            .block();

        resultsList.forEach(it -> assertEquals("Dino", it.get("name")));
    }
}
