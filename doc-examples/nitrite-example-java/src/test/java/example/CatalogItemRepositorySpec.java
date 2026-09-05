package example;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.dizitart.no2.exceptions.UniqueConstraintException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
class CatalogItemRepositorySpec {

    @Inject
    CatalogItemRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    // tag::unique-index-usage[]
    @Test
    void testUniqueIndexRejectsDuplicate() {
        repository.save(new CatalogItem("SKU-100", "Widget"));

        CatalogItem duplicate = new CatalogItem("SKU-100", "Different Widget");

        DataAccessException e = assertThrows(DataAccessException.class, () -> repository.save(duplicate));
        assertInstanceOf(UniqueConstraintException.class, e.getCause());
    }
    // end::unique-index-usage[]
}
