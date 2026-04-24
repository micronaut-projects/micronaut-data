package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "datasources.default.url", value = "jdbc:sqlite:file:mydb_person?mode=memory&cache=shared")
@Property(name = "datasources.default.driver-class-name", value = "org.sqlite.JDBC")
@Property(name = "datasources.default.dialect", value = "ANSI")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@MicronautTest(transactional = false)
class PersonRepositoryTest {

    @Test
    void batchInsertSQLite(PersonRepository personRepository) {
        long count = personRepository.count();
        personRepository.save(new Person(null, "Sergio", 43));
        assertEquals(1 + count, personRepository.count());
        count = personRepository.count();
        personRepository.saveAll(List.of(
            new Person(null, "John Ternus", 51),
            new Person(null, "Tim Cook", 65)
        ));
        assertEquals(2 + count, personRepository.count());
    }
}
