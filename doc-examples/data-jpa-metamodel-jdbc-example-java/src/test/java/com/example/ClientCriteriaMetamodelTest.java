/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.example;

import com.example.repository.CategoryRepository;
import com.example.repository.ClientRepository;
import com.example.repository.specification.ClientSpecification;
import io.micronaut.entities.Category;
import io.micronaut.entities.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@MicronautTest
public class ClientCriteriaMetamodelTest {

    final ClientRepository clientRepository;
    final CategoryRepository categoryRepository;

    public ClientCriteriaMetamodelTest(ClientRepository clientRepository,
                                       CategoryRepository categoryRepository) {
        this.clientRepository = clientRepository;
        this.categoryRepository = categoryRepository;
    }

    @BeforeEach
    void cleanup() {
        clientRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void canQueryBySingularAttributes_andEnum_usingStaticMetamodel() {
        Client c1 = new Client();
        c1.setId(1L);
        c1.setName("Alice");
        c1.setTier(Client.Tier.PRO);
        c1.setCreatedAt(Instant.now());
        c1.setBillingAddress(new Client.Address("street", "city"));


        Client c2 = new Client();
        c2.setId(2L);
        c2.setName("Bob");
        c2.setTier(Client.Tier.BASIC);
        c2.setCreatedAt(Instant.now());
        c2.setBillingAddress(new Client.Address("street", "city"));

        clientRepository.saveAll(List.of(c1, c2));

        List<Client> result = clientRepository.findAll(ClientSpecification.tierEquals(Client.Tier.PRO).and(ClientSpecification.nameEquals("Alice")));

        assertEquals(1, result.size());
        Assertions.assertEquals(1L, result.getFirst().getId());
        Assertions.assertEquals("Alice", result.getFirst().getName());
        Assertions.assertEquals(Client.Tier.PRO, result.getFirst().getTier());
    }

    @Test
    void canJoinListRelationship_usingStaticMetamodel() {
        Category fiction = new Category(10L, "Fiction", null, new byte[]{});
        Category sciFi = new Category(11L, "Sci-Fi", null, new byte[]{});

        Client c = new Client();
        c.setId(3L);
        c.setName("Carol");
        c.setBillingAddress(new Client.Address("street", "city"));

        categoryRepository.saveAll(List.of(fiction, sciFi));
        c.setCategoriesList(List.of(fiction, sciFi));

        clientRepository.save(c);

        List<Long> result = clientRepository.findAll(ClientSpecification.withCategoryListName("Sci-Fi"))
            .stream().map(Client::getId).toList();
        assertEquals(List.of(3L), result);
    }

    @Test
    void canJoinSetRelationship_usingStaticMetamodel() {
        Category c1 = new Category(12L, "History", null, new byte[]{});

        Client client = new Client();
        client.setId(4L);
        client.setName("Dan");
        client.setBillingAddress(new Client.Address("street", "city"));

        categoryRepository.save(c1);
        client.setCategoriesSet(new HashSet<>(Set.of(c1)));

        clientRepository.save(client);

        List<Client> result = clientRepository.findAll(ClientSpecification.withCategorySetName("History"));
        assertFalse(result.isEmpty());
    }

    @Test
    void canFilterByManyToOne_usingStaticMetamodel() {
        Category main = new Category(20L, "Main", null, new byte[]{});

        Client client = new Client();
        client.setId(5L);
        client.setName("Eve");
        client.setMainCategory(main);
        client.setBillingAddress(new Client.Address("street", "city"));


        clientRepository.save(client);

        List<Client> result = clientRepository.findAll(ClientSpecification.mainCategoryIdEquals(20L));
        assertEquals(1, result.size());
        Assertions.assertEquals(5L, result.getFirst().getId());
    }

}
