package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

@MicronautTest
public class MyTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    MyMainEntityRepository repository;

    @BeforeEach
    void setup() throws Exception {
        DataSource dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource.class));
        Connection connection = dataSource.getConnection();
        connection.prepareStatement("DROP TABLE IF EXISTS my_main_entity").execute();
        connection.prepareStatement("""
                                        CREATE TABLE my_main_entity (
                                            id bigint primary key not null,
                                            example text,
                                            part_text text);
                                         """).execute();
    }

    @AfterEach
    void stop() throws Exception {
        repository.deleteAll();
        DataSource dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource.class));
        Connection connection = dataSource.getConnection();
        connection.prepareStatement("DROP TABLE IF EXISTS my_main_entity");
    }

    @Test
    void testIt() {
        // should not update field 'example'
        repository.save(new MyMainEntity(1L, "Test", null));
        MyMainEntity persistedEntity = repository.findById(1L).orElse(null);
        Assertions.assertNotNull(persistedEntity);
        Assertions.assertNull(persistedEntity.getExample());
        repository.update(new MyMainEntity(1L, "Changed", null));
        MyMainEntity updatedEntity = repository.findById(1L).orElse(null);
        Assertions.assertNotNull(updatedEntity);
        Assertions.assertNull(updatedEntity.getExample());

        // should not update field 'part_text'
        repository.save(new MyMainEntity(2L, null, new MyPart("Test")));
        persistedEntity = repository.findById(2L).orElse(null);
        Assertions.assertNotNull(persistedEntity);
        Assertions.assertNull(persistedEntity.getPart().getText());

        repository.update(new MyMainEntity(2L, null, new MyPart("Changed")));
        updatedEntity = repository.findById(2L).orElse(null);
        Assertions.assertNotNull(updatedEntity);
        Assertions.assertNull(updatedEntity.getPart().getText());
    }
}
