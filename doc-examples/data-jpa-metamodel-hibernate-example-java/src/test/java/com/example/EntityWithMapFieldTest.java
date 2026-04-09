package com.example;

import com.example.entity.EntityWithMapField;
import com.example.entity.EntityWithMapField_;
import com.example.repository.EntityWithMapFieldRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class EntityWithMapFieldTest {

    final EntityWithMapFieldRepository entityWithMapFieldRepository;
    final EntityManager entityManager;

    @BeforeEach
    public void init() {
        entityWithMapFieldRepository.deleteAll();
    }

    public EntityWithMapFieldTest(EntityWithMapFieldRepository entityWithMapFieldRepository,
                                  EntityManager entityManager) {
        this.entityWithMapFieldRepository = entityWithMapFieldRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canJoinMapElementCollection_usingStaticMetamodel() {
        EntityWithMapField entityWithMapField = new EntityWithMapField();
        entityWithMapField.setId(1L);

        HashMap<String, String> props = new HashMap<>();
        props.put("region", "EMEA");
        props.put("segment", "ENT");
        entityWithMapField.setProperties(props);

        EntityWithMapField entityWithMapField1 = new EntityWithMapField();
        entityWithMapField1.setId(2L);

        entityWithMapFieldRepository.saveAll(List.of(entityWithMapField, entityWithMapField1));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<EntityWithMapField> root = cq.from(EntityWithMapField.class);

        MapJoin<EntityWithMapField, String, String> propsJoin = root.join(EntityWithMapField_.properties);

        cq.select(root.get(EntityWithMapField_.id))
            .where(cb.and(
                cb.equal(propsJoin.key(), "region"),
                cb.equal(propsJoin.value(), "EMEA")
            ))
            .distinct(true);

        List<Long> ids = entityManager.createQuery(cq).getResultList();
        assertEquals(List.of(1L), ids);
    }

}
