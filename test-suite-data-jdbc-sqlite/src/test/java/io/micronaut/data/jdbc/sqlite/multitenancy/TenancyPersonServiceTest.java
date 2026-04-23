package io.micronaut.data.jdbc.sqlite.multitenancy;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.url", value = "jdbc:sqlite:file:devDb?mode=memory&cache=shared")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.dialect", value = "ANSI")
@Property(name = "datasources.default.db-type", value = "sqlite")
@Property(name = "datasources.default.driver-class-name", value = "org.sqlite.JDBC")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.jdbc.sqlite.multitenancy")
@Property(name = "micronaut.data.multi-tenancy.mode", value = "DISCRIMINATOR")
@Property(name = "micronaut.multitenancy.tenantresolver.systemproperty.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "TenancyPersonServiceSpec")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenancyPersonServiceTest {

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS persons (
            id INT NOT NULL,
            firstName VARCHAR(50) NOT NULL,
            lastName VARCHAR(50) NOT NULL,
            tenantId VARCHAR(10) NOT NULL
        );
    """;

    private static final String TENANT_ID = "THE_TENANT";

    @Inject
    TenancyPersonService tenancyPersonService;

    @Inject
    JdbcOperations jdbcOperations;

    @BeforeAll
    void setupSpec() {
        jdbcOperations.execute(connection -> {
            var ps = connection.prepareStatement(CREATE_TABLE_SQL);
            ps.execute();
            return null;
        });
    }

    @BeforeEach
    void setup() {
        System.setProperty("tenantId", TENANT_ID);
    }

    @AfterEach
    void cleanup() {
        tenancyPersonService.deleteAll();
        System.clearProperty("tenantId");
    }

    @Test
    void saveAndFind() {
        TenancyPerson person = new TenancyPerson(1, "First", "Last", TENANT_ID);
        tenancyPersonService.save(person);

        assertTrue(tenancyPersonService.findById(1).isPresent());
    }

    @Test
    void saveAllAndFindAll() {
        TenancyPerson p1 = new TenancyPerson(2, "First", "Last", TENANT_ID);
        TenancyPerson p2 = new TenancyPerson(3, "Second", "Last", TENANT_ID);

        tenancyPersonService.saveAll(List.of(p1, p2));
        List<TenancyPerson> result = tenancyPersonService.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void insertWithQueryAndFindAll() {
        TenancyPerson p1 = new TenancyPerson(4, "First", "Last", TENANT_ID);
        TenancyPerson p2 = new TenancyPerson(5, "Second", "Last", TENANT_ID);

        Integer insertCount = tenancyPersonService.insertWithQuery(List.of(p1, p2));
        List<TenancyPerson> result = tenancyPersonService.findAll();

        assertEquals(2, insertCount);
        assertEquals(2, result.size());
    }

    @Test
    void insertWithQuerySingleInsertAndFindAll() {
        TenancyPerson p1 = new TenancyPerson(6, "First", "Last", TENANT_ID);
        TenancyPerson p2 = new TenancyPerson(7, "Second", "Last", TENANT_ID);

        tenancyPersonService.insertWithQuerySingle(p1);
        tenancyPersonService.insertWithQuerySingle(p2);
        List<TenancyPerson> result = tenancyPersonService.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void insertWithQueryTheLongWayAndFindAll() {
        TenancyPerson p1 = new TenancyPerson(8, "First", "Last", TENANT_ID);
        TenancyPerson p2 = new TenancyPerson(9, "Second", "Last", TENANT_ID);

        tenancyPersonService.insertWithQueryTheLongWay(List.of(p1, p2));
        List<TenancyPerson> result = tenancyPersonService.findAll();

        assertEquals(2, result.size());
    }
}
