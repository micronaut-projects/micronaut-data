package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.transaction-manager", value = "springHibernate")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class BookRepositorySpec {

    @Inject
    BookRepository bookRepository;

    @Inject
    BookManager bookManager;


    @AfterEach
    public void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testBooks() {
        Assertions.assertEquals(0, bookManager.getEvents().size());
        Assertions.assertEquals(0, bookRepository.count());
        bookManager.saveBook("GoT", 3000);
// TODO:       Assertions.assertEquals(1, bookManager.getEvents().size());
        Assertions.assertEquals(1, bookRepository.count());
    }

}
