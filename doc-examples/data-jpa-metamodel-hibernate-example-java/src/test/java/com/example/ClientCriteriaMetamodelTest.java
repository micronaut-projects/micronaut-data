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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class ClientCriteriaMetamodelTest {

    final ClientRepository clientRepository;
    final CategoryRepository categoryRepository;
    final EntityManager entityManager;

    public ClientCriteriaMetamodelTest(ClientRepository clientRepository,
                                       CategoryRepository categoryRepository,
                                       EntityManager entityManager) {
        this.clientRepository = clientRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canQueryBySingularAttributes_andEnum_usingStaticMetamodel() {
        Client c1 = new Client();
        c1.setId(1L);
        c1.setName("Alice");
        c1.setTier(Client.Tier.PRO);
        c1.setCreatedAt(Instant.now());

        Client c2 = new Client();
        c2.setId(2L);
        c2.setName("Bob");
        c2.setTier(Client.Tier.BASIC);
        c2.setCreatedAt(Instant.now());

        clientRepository.save(c1);
        clientRepository.save(c2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Client> cq = cb.createQuery(Client.class);
        Root<Client> root = cq.from(Client.class);

        cq.select(root).where(cb.and(
            cb.equal(root.get(Client_.tier), Client.Tier.PRO),
            cb.equal(root.get(Client_.name), "Alice")
        ));

        List<Client> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Alice", result.get(0).getName());
        assertEquals(Client.Tier.PRO, result.get(0).getTier());
    }

    @Test
    void canJoinListRelationship_usingStaticMetamodel() {
        Category fiction = new Category();
        fiction.setId(10L);
        fiction.setName("Fiction");
        fiction = categoryRepository.save(fiction);

        Category scifi = new Category();
        scifi.setId(11L);
        scifi.setName("Sci-Fi");
        scifi = categoryRepository.save(scifi);

        Client c = new Client();
        c.setId(3L);
        c.setName("Carol");
        c.setCategoriesList(List.of(fiction, scifi));
        clientRepository.save(c);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Client> root = cq.from(Client.class);

        ListJoin<Client, Category> join = root.join(Client_.categoriesList, JoinType.INNER);

        cq.select(root.get(Client_.id))
            .where(cb.equal(join.get(Category_.name), "Sci-Fi"))
            .distinct(true);

        List<Long> result = entityManager.createQuery(cq).getResultList();
        assertEquals(List.of(3L), result);
    }

    @Test
    void canJoinSetRelationship_usingStaticMetamodel() {
        Category c1 = new Category();
        c1.setId(12L);
        c1.setName("History");
        categoryRepository.save(c1);

        Client client = new Client();
        client.setId(4L);
        client.setName("Dan");
        client.setCategoriesSet(new HashSet<>(Set.of(c1)));
        clientRepository.save(client);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Client> cq = cb.createQuery(Client.class);
        Root<Client> root = cq.from(Client.class);

        root.join(Client_.categoriesSet, JoinType.INNER);

        cq.select(root).distinct(true);

        List<Client> result = entityManager.createQuery(cq).getResultList();
        assertFalse(result.isEmpty());
    }

    @Test
    void canFilterByManyToOne_usingStaticMetamodel() {
        Category main = new Category();
        main.setId(20L);
        main.setName("Main");
        categoryRepository.save(main);

        Client a = new Client();
        a.setId(5L);
        a.setName("Eve");
        a.setMainCategory(main);
        clientRepository.save(a);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Client> cq = cb.createQuery(Client.class);
        Root<Client> root = cq.from(Client.class);

        cq.select(root).where(cb.equal(root.get(Client_.mainCategory).get(Category_.id), 20L));

        List<Client> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getId());
    }

    @Test
    void canJoinMapElementCollection_usingStaticMetamodel() {
        Client c = new Client();
        c.setId(6L);
        c.setName("Frank");

        HashMap<String, String> props = new HashMap<>();
        props.put("region", "EMEA");
        props.put("segment", "ENT");
        c.setProperties(props);

        clientRepository.save(c);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Client> root = cq.from(Client.class);

        MapJoin<Client, String, String> propsJoin = root.join(Client_.properties);

        cq.select(root.get(Client_.id))
            .where(cb.and(
                cb.equal(propsJoin.key(), "region"),
                cb.equal(propsJoin.value(), "EMEA")
            ))
            .distinct(true);

        List<Long> ids = entityManager.createQuery(cq).getResultList();
        assertEquals(List.of(6L), ids);
    }

    @Test
    void generatedMetamodelHasExpectedFields_andTypes() throws Exception {
        assertNotNull(Client_.class.getDeclaredField("id"));
        assertNotNull(Client_.class.getDeclaredField("name"));
        assertNotNull(Client_.class.getDeclaredField("version"));
        assertNotNull(Client_.class.getDeclaredField("tier"));
        assertNotNull(Client_.class.getDeclaredField("createdAt"));
        assertNotNull(Client_.class.getDeclaredField("billingAddress"));

        assertNotNull(Client_.class.getDeclaredField("categoriesCollection"));
        assertNotNull(Client_.class.getDeclaredField("categoriesList"));
        assertNotNull(Client_.class.getDeclaredField("categoriesSet"));
        assertNotNull(Client_.class.getDeclaredField("mainCategory"));
        assertNotNull(Client_.class.getDeclaredField("properties"));

        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("version").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("tier").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("createdAt").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("billingAddress").getType().getName());

        assertEquals(CollectionAttribute.class.getName(), Client_.class.getDeclaredField("categoriesCollection").getType().getName());
        assertEquals(ListAttribute.class.getName(), Client_.class.getDeclaredField("categoriesList").getType().getName());
        assertEquals(SetAttribute.class.getName(), Client_.class.getDeclaredField("categoriesSet").getType().getName());

        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("mainCategory").getType().getName());
        assertEquals(MapAttribute.class.getName(), Client_.class.getDeclaredField("properties").getType().getName());

        assertThrows(NoSuchFieldException.class, () -> Client_.class.getDeclaredField("nonPersistent"));
        MetamodelAssertions.assertClassFieldIsEntityType(Client_.class, EntityType.class, Client.class);
    }
}
