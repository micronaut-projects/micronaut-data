package io.micronaut.data.jdbc.sqlite.embeddedNameMapping;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import jakarta.persistence.Embedded;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.embeddedNameMapping,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities")
class CustomEmbeddedNameMappingTest {

    private static ApplicationContext applicationContext;
    private static MyBookRepository myBookRepository;
    private static final Map<Class<?>, RuntimePersistentEntity<?>> ENTITIES = new HashMap<>();

    @BeforeAll
    static void setupContext() {
        applicationContext = ApplicationContext.run(createProperties());
        myBookRepository = applicationContext.getBean(MyBookRepository.class);
    }

    @AfterAll
    static void closeContext() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void testEmbeddedCustomNameStrategy() {
        MyBook book = new MyBook();
        book.setId("1");
        EmbeddedAuthor author = new EmbeddedAuthor();
        author.setFirstName("Jean-Jaques");
        author.setLastName("Rousseau");
        EmbeddedAuthorDetails details = new EmbeddedAuthorDetails();
        details.setNumberAge(33);
        author.setDetailsIncluded(details);
        book.setAuthor(author);

        myBookRepository.save(book);
        book = myBookRepository.findById("1").orElseThrow();

        assertNotNull(book.getAuthor());
        assertEquals("Jean-Jaques", book.getAuthor().getFirstName());
        assertEquals("Rousseau", book.getAuthor().getLastName());

        book.getAuthor().setLastName("Xyz");
        myBookRepository.update(book);
        book = myBookRepository.findById("1").orElseThrow();

        assertNotNull(book.getAuthor());
        assertEquals("Jean-Jaques", book.getAuthor().getFirstName());
        assertEquals("Xyz", book.getAuthor().getLastName());
    }

    @Test
    void testBuildCreate() {
        SqlQueryBuilder encoder = new SqlQueryBuilder(Dialect.SQLITE);
        var statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(MyBook.class));

        assertEquals(
            "CREATE TABLE \"MyBook\" (\"id\" VARCHAR(255) NOT NULL,\"firstName\" VARCHAR(255) NOT NULL,\"lastName\" VARCHAR(255) NOT NULL,\"numberAge\" INT NOT NULL, PRIMARY KEY(\"id\"));",
            String.join("\n", statements)
        );
    }

    @Test
    void testBuildInsert() {
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder();
        var result = builder.createCriteriaInsert(MyBook.class).build(new SqlQueryBuilder(Dialect.SQLITE));

        assertEquals("INSERT INTO \"MyBook\" (\"firstName\",\"lastName\",\"numberAge\",\"id\") VALUES (?,?,?,?)", result.getQuery());
    }

    @Test
    void testUpdate() {
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder();
        var query = builder.createCriteriaUpdate(MyBook.class);
        query.set("id", builder.parameter(Object.class));
        query.set("author.firstName", builder.parameter(Object.class));
        query.set("author.lastName", builder.parameter(Object.class));
        query.set("author.detailsIncluded.numberAge", builder.parameter(Object.class));
        query.where(builder.equal(query.getRoot().id(), builder.parameter(Object.class)));
        var result = query.build(new SqlQueryBuilder(Dialect.SQLITE));

        assertEquals("UPDATE \"MyBook\" SET \"id\"=?,\"firstName\"=?,\"lastName\"=?,\"numberAge\"=? WHERE (\"id\" = ?)", result.getQuery());
        assertEquals(
            Map.of(
                "1", "id",
                "2", "author.firstName",
                "3", "author.lastName",
                "4", "author.detailsIncluded.numberAge",
                "5", "id"
            ),
            result.getParameters()
        );
    }

    @Test
    void testBuildQuery() {
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder();
        var query = builder.createQuery(MyBook.class);
        var root = query.from(MyBook.class);
        query.where(builder.equal(root.id(), builder.parameter(Object.class)));
        var result = query.build(new SqlQueryBuilder(Dialect.SQLITE));

        assertEquals(
            "SELECT my_book_.\"id\",my_book_.\"firstName\",my_book_.\"lastName\",my_book_.\"numberAge\" FROM \"MyBook\" my_book_ WHERE (my_book_.\"id\" = ?)",
            result.getQuery()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RuntimePersistentEntity getRuntimePersistentEntity(Class<?> type) {
        RuntimePersistentEntity<?> entity = ENTITIES.get(type);
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity<?> getEntity(Class t) {
                    return getRuntimePersistentEntity(t);
                }
            };
            ENTITIES.put(type, entity);
        }
        return entity;
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("customembeddednamemapping", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite.embeddedNameMapping,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface MyBookRepository extends CrudRepository<MyBook, String> {
}

@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
class MyBook {
    @Id
    private String id;

    @Embedded
    private EmbeddedAuthor author;

    String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    EmbeddedAuthor getAuthor() {
        return author;
    }

    void setAuthor(EmbeddedAuthor author) {
        this.author = author;
    }
}

@Embeddable
class EmbeddedAuthor {
    private String firstName;
    private String lastName;

    @Embedded
    private EmbeddedAuthorDetails detailsIncluded;

    String getFirstName() {
        return firstName;
    }

    void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    String getLastName() {
        return lastName;
    }

    void setLastName(String lastName) {
        this.lastName = lastName;
    }

    EmbeddedAuthorDetails getDetailsIncluded() {
        return detailsIncluded;
    }

    void setDetailsIncluded(EmbeddedAuthorDetails detailsIncluded) {
        this.detailsIncluded = detailsIncluded;
    }
}

@Embeddable
class EmbeddedAuthorDetails {
    private int numberAge;

    int getNumberAge() {
        return numberAge;
    }

    void setNumberAge(int numberAge) {
        this.numberAge = numberAge;
    }
}
