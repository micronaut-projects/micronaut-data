package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.data.tck.entities.AccountRecord;
import io.micronaut.data.tck.repositories.AccountRecordRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteDiscriminatorMultitenancyRecordTest {

    @Test
    void testDiscriminatorMultitenancy() {
        Map<String, Object> properties = new HashMap<>(createProperties());
        properties.put("accountRepositoryClass", SQLiteAccountRecordRepository.class.getName());
        properties.put("spec.name", "discriminator-multitenancy-record");
        properties.put("micronaut.data.multi-tenancy.mode", "DISCRIMINATOR");
        properties.put("micronaut.multitenancy.tenantresolver.httpheader.enabled", "true");
        properties.put("datasource.default.schema-generate", "create-drop");

        try (EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST)) {
            ApplicationContext context = embeddedServer.getApplicationContext();
            FooAccountRecordClient fooAccountClient = context.getBean(FooAccountRecordClient.class);
            BarAccountRecordClient barAccountClient = context.getBean(BarAccountRecordClient.class);

            fooAccountClient.deleteAll();
            barAccountClient.deleteAll();

            AccountRecordDto fooAccount = fooAccountClient.save("The Stand");
            assertNotNull(fooAccount.getId());

            fooAccount = fooAccountClient.findOne(fooAccount.getId()).orElse(null);
            assertNotNull(fooAccount);
            assertEquals("The Stand", fooAccount.getName());
            assertEquals("foo", fooAccount.getTenancy());
            assertEquals(1, fooAccountClient.findAll().size());
            assertEquals(0, barAccountClient.findAll().size());

            fooAccountClient.updateTenancy(fooAccount.getId(), "bar");
            assertEquals(0, fooAccountClient.findAll().size());
            assertEquals(1, barAccountClient.findAll().size());
            assertTrue(fooAccountClient.findOne(fooAccount.getId()).isEmpty());
            assertTrue(barAccountClient.findOne(fooAccount.getId()).isPresent());

            barAccountClient.updateTenancy(fooAccount.getId(), "foo");
            assertEquals(1, fooAccountClient.findAll().size());
            assertEquals(0, barAccountClient.findAll().size());
            assertTrue(fooAccountClient.findOne(fooAccount.getId()).isPresent());
            assertTrue(barAccountClient.findOne(fooAccount.getId()).isEmpty());

            AccountRecordDto barAccount = barAccountClient.save("The Bar");
            List<AccountRecordDto> allAccounts = barAccountClient.findAllTenants();
            Long fooAccountId = fooAccount.getId();

            assertEquals("bar", barAccount.getTenancy());
            assertEquals(2, allAccounts.size());
            assertEquals("bar", allAccounts.stream().filter(account -> account.getId().equals(barAccount.getId())).findFirst().orElseThrow().getTenancy());
            assertEquals("foo", allAccounts.stream().filter(account -> account.getId().equals(fooAccountId)).findFirst().orElseThrow().getTenancy());
            assertEquals(allAccounts, fooAccountClient.findAllTenants());

            List<AccountRecordDto> barAccounts = barAccountClient.findAllBarTenants();
            assertEquals(1, barAccounts.size());
            assertEquals(barAccount.getId(), barAccounts.getFirst().getId());
            assertEquals("bar", barAccounts.getFirst().getTenancy());
            assertEquals(barAccounts, fooAccountClient.findAllBarTenants());

            List<AccountRecordDto> fooAccounts = barAccountClient.findAllFooTenants();
            assertEquals(1, fooAccounts.size());
            assertEquals(fooAccountId, fooAccounts.getFirst().getId());
            assertEquals("foo", fooAccounts.getFirst().getTenancy());
            assertEquals(fooAccounts, fooAccountClient.findAllFooTenants());

            List<AccountRecordDto> exp = barAccountClient.findTenantExpression();
            assertEquals(1, exp.size());
            assertEquals("bar", exp.getFirst().getTenancy());
            assertEquals(exp, fooAccountClient.findTenantExpression());

            barAccountClient.deleteAll();
            assertEquals(1, fooAccountClient.findAll().size());

            fooAccountClient.deleteAll();
            assertEquals(0, fooAccountClient.findAll().size());
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqlitediscriminatormultitenancyrecord", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.sqlite");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@Requires(property = "spec.name", value = "discriminator-multitenancy-record")
@ExecuteOn(TaskExecutors.IO)
@Controller("/accounts")
class AccountRecordController {

    private final AccountRecordRepository accountRepository;

    AccountRecordController(ApplicationContext beanContext) throws ClassNotFoundException {
        String className = beanContext.getProperty("accountRepositoryClass", String.class).orElseThrow();
        this.accountRepository = (AccountRecordRepository) beanContext.getBean(Class.forName(className));
    }

    @Post
    AccountRecordDto save(String name) {
        AccountRecord newAccount = new AccountRecord(null, name, null);
        AccountRecord account = accountRepository.save(newAccount);
        return new AccountRecordDto(account);
    }

    @Put("/{id}/tenancy")
    void updateTenancy(Long id, String tenancy) {
        AccountRecord account = accountRepository.findById(id).orElseThrow();
        accountRepository.update(new AccountRecord(account.id(), account.name(), tenancy));
    }

    @Get("/{id}")
    Optional<AccountRecordDto> findOne(Long id) {
        return accountRepository.findById(id).map(AccountRecordDto::new);
    }

    @Get
    List<AccountRecordDto> findAll() {
        return findAll0();
    }

    @Get("/alltenants")
    List<AccountRecordDto> findAllTenants() {
        return accountRepository.findAll$withAllTenants().stream().map(AccountRecordDto::new).toList();
    }

    @Get("/foo")
    List<AccountRecordDto> findAllFooTenants() {
        return accountRepository.findAll$withTenantFoo().stream().map(AccountRecordDto::new).toList();
    }

    @Get("/bar")
    List<AccountRecordDto> findAllBarTenants() {
        return accountRepository.findAll$withTenantBar().stream().map(AccountRecordDto::new).toList();
    }

    @Get("/expression")
    List<AccountRecordDto> findTenantExpression() {
        return accountRepository.findAll$withTenantExpression().stream().map(AccountRecordDto::new).toList();
    }

    @Connectable
    protected List<AccountRecordDto> findAll0() {
        return findAll1();
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.MANDATORY)
    protected List<AccountRecordDto> findAll1() {
        return accountRepository.findAll().stream().map(AccountRecordDto::new).toList();
    }

    @Delete
    void deleteAll() {
        deleteAll0();
    }

    @Transactional
    protected void deleteAll0() {
        deleteAll1();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    protected void deleteAll1() {
        accountRepository.deleteAll();
    }
}

@Introspected
class AccountRecordDto {
    private Long id;
    private String name;
    private String tenancy;

    AccountRecordDto() {
    }

    AccountRecordDto(AccountRecord account) {
        this.id = account.id();
        this.name = account.name();
        this.tenancy = account.tenancy();
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getTenancy() {
        return tenancy;
    }

    void setTenancy(String tenancy) {
        this.tenancy = tenancy;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AccountRecordDto that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(tenancy, that.tenancy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, tenancy);
    }
}

@Requires(property = "spec.name", value = "discriminator-multitenancy-record")
@Client("/accounts")
interface AccountRecordClient {

    @Post
    AccountRecordDto save(String name);

    @Put("/{id}/tenancy")
    void updateTenancy(Long id, String tenancy);

    @Get("/{id}")
    Optional<AccountRecordDto> findOne(Long id);

    @Get
    List<AccountRecordDto> findAll();

    @Get("/alltenants")
    List<AccountRecordDto> findAllTenants();

    @Get("/foo")
    List<AccountRecordDto> findAllFooTenants();

    @Get("/bar")
    List<AccountRecordDto> findAllBarTenants();

    @Get("/expression")
    List<AccountRecordDto> findTenantExpression();

    @Delete
    void deleteAll();
}

@Requires(property = "spec.name", value = "discriminator-multitenancy-record")
@Header(name = "tenantId", value = "foo")
@Client("/accounts")
interface FooAccountRecordClient extends AccountRecordClient {
}

@Requires(property = "spec.name", value = "discriminator-multitenancy-record")
@Header(name = "tenantId", value = "bar")
@Client("/accounts")
interface BarAccountRecordClient extends AccountRecordClient {
}
