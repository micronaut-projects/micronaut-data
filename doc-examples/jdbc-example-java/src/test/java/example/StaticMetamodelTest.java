package example;

import example.metamodel.Category_;
import example.metamodel.Client;
import example.metamodel.Client_;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.JoinType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class StaticMetamodelTest {
    @Inject
    RuntimeCriteriaBuilder runtimeCriteriaBuilder;

    @BeforeEach
    void cleanup() {
        Client_.categoriesCollection = null;
        Client_.categoriesList = null;
        Client_.categoriesSet = null;
        Client_.id = null;
        Client_.name = null;
    }

    @Test
    void testMetamodel() {
        Assertions.assertNull(Client_.categoriesCollection);
        Assertions.assertNull(Client_.categoriesList);
        Assertions.assertNull(Client_.categoriesSet);
        Assertions.assertNull(Client_.id);
        Assertions.assertNull(Client_.name);
        Assertions.assertNull(Category_.id);
        Assertions.assertNull(Category_.name);

        PersistentEntityCriteriaQuery<Client> query = runtimeCriteriaBuilder.createQuery(Client.class);
        PersistentEntityRoot<Client> entityRoot = query
            .from(Client.class);

        Assertions.assertNotNull(Client_.categoriesCollection);
        Assertions.assertNotNull(Client_.categoriesList);
        Assertions.assertNotNull(Client_.categoriesSet);
        Assertions.assertNotNull(Client_.id);
        Assertions.assertNotNull(Client_.name);
        Assertions.assertNotNull(Category_.id);
        Assertions.assertNotNull(Category_.name);

        entityRoot.join(Client_.categoriesCollection, JoinType.LEFT);

        QueryResultPersistentEntityCriteriaQuery criteriaQuery = (QueryResultPersistentEntityCriteriaQuery) query;
        String q = criteriaQuery.buildQuery(new SqlQueryBuilder()).getQuery();

        Assertions.assertEquals(q, "SELECT client_.\"id\",client_.\"name\",client_.\"main_category_id\" FROM \"client\" client_ LEFT JOIN \"client_category\" client_categories_collection_client_category_ ON client_.\"id\"=client_categories_collection_client_category_.\"client_id\"  LEFT JOIN \"category\" client_categories_collection_ ON client_categories_collection_client_category_.\"category_id\"=client_categories_collection_.\"id\"");
    }
}
