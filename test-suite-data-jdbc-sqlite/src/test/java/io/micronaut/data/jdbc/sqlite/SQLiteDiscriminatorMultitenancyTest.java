package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.data.tck.tests.AccountDto;
import io.micronaut.data.tck.tests.BarAccountClient;
import io.micronaut.data.tck.tests.FooAccountClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteDiscriminatorMultitenancyTest {

    @Test
    void testDiscriminatorMultitenancy() {
        Map<String, Object> properties = new HashMap<>(createProperties());
        properties.put("accountRepositoryClass", SQLiteAccountRepository.class.getName());
        properties.put("spec.name", "discriminator-multitenancy");
        properties.put("micronaut.data.multi-tenancy.mode", "DISCRIMINATOR");
        properties.put("micronaut.multitenancy.tenantresolver.httpheader.enabled", "true");
        properties.put("datasource.default.schema-generate", "create-drop");

        try (EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST)) {
            ApplicationContext context = embeddedServer.getApplicationContext();
            FooAccountClient fooAccountClient = context.getBean(FooAccountClient.class);
            BarAccountClient barAccountClient = context.getBean(BarAccountClient.class);

            fooAccountClient.deleteAll();
            barAccountClient.deleteAll();

            AccountDto fooAccount = fooAccountClient.save("The Stand");
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

            AccountDto barAccount = barAccountClient.save("The Bar");
            List<AccountDto> allAccounts = barAccountClient.findAllTenants();
            Long fooAccountId = fooAccount.getId();
            assertEquals("bar", barAccount.getTenancy());
            assertEquals(2, allAccounts.size());
            assertEquals("bar", allAccounts.stream().filter(account -> account.getId().equals(barAccount.getId())).findFirst().orElseThrow().getTenancy());
            assertEquals("foo", allAccounts.stream().filter(account -> account.getId().equals(fooAccountId)).findFirst().orElseThrow().getTenancy());
            assertEquals(allAccounts, fooAccountClient.findAllTenants());

            List<AccountDto> barAccounts = barAccountClient.findAllBarTenants();
            assertEquals(1, barAccounts.size());
            assertEquals(barAccount.getId(), barAccounts.getFirst().getId());
            assertEquals("bar", barAccounts.getFirst().getTenancy());
            assertEquals(barAccounts, fooAccountClient.findAllBarTenants());

            List<AccountDto> fooAccounts = barAccountClient.findAllFooTenants();
            assertEquals(1, fooAccounts.size());
            assertEquals(fooAccountId, fooAccounts.getFirst().getId());
            assertEquals("foo", fooAccounts.getFirst().getTenancy());
            assertEquals(fooAccounts, fooAccountClient.findAllFooTenants());

            List<AccountDto> exp = barAccountClient.findTenantExpression();
            assertEquals(1, exp.size());
            assertEquals("bar", exp.getFirst().getTenancy());
            assertEquals(exp, fooAccountClient.findTenantExpression());

            barAccountClient.deleteAll();
            assertEquals(1, fooAccountClient.findAll().size());

            fooAccountClient.deleteAll();
            assertEquals(0, fooAccountClient.findAll().size());
            assertFalse(fooAccountClient.findAll().size() > 0);
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqlitediscriminatormultitenancy", ".sqlite").toFile();
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
