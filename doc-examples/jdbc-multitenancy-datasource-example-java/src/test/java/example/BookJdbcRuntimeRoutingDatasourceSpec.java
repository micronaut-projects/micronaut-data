package example;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(environments = "runtime-multitenancy", startApplication = false, transactional = false)
class BookJdbcRuntimeRoutingDatasourceSpec {

    @Inject
    RuntimeRoutingBookRepository runtimeRoutingBookRepository;

    @Inject
    RuntimeRoutingTenantDataSourceResolver tenantDataSourceResolver;

    @AfterEach
    void cleanup() {
        withTenant(null, () -> {
            runtimeRoutingBookRepository.deleteAll();
            return null;
        });
        withTenant("foo", () -> {
            runtimeRoutingBookRepository.deleteAll();
            return null;
        });
        withTenant("bar", () -> {
            runtimeRoutingBookRepository.deleteAll();
            return null;
        });
    }

    @Test
    void testRuntimeRouting() {
        withTenant(null, () -> runtimeRoutingBookRepository.save(new Book("Default", 100)));
        withTenant("foo", () -> {
            runtimeRoutingBookRepository.save(new Book("Foo One", 200));
            runtimeRoutingBookRepository.save(new Book("Foo Two", 300));
            return null;
        });
        withTenant("bar", () -> runtimeRoutingBookRepository.save(new Book("Bar", 400)));

        assertEquals(1, withTenant(null, () -> runtimeRoutingBookRepository.findAll().size()));
        assertEquals(List.of("Default"), withTenant(null, this::titles));

        assertEquals(2, withTenant("foo", () -> runtimeRoutingBookRepository.findAll().size()));
        assertEquals(List.of("Foo One", "Foo Two"), withTenant("foo", this::titles));

        assertEquals(1, withTenant("bar", () -> runtimeRoutingBookRepository.findAll().size()));
        assertEquals(List.of("Bar"), withTenant("bar", this::titles));

        assertEquals(1, tenantDataSourceResolver.getCreationCount("foo"));
        assertEquals(1, tenantDataSourceResolver.getCreationCount("bar"));
    }

    private List<String> titles() {
        return runtimeRoutingBookRepository.findAll().stream().map(Book::getTitle).sorted().toList();
    }

    private static <T> T withTenant(String tenantId, SupplierWithException<T> supplier) {
        return PropagatedContext.getOrEmpty()
            .plus(new RuntimeRoutingTenantContext(tenantId))
            .propagate(supplier::get);
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
