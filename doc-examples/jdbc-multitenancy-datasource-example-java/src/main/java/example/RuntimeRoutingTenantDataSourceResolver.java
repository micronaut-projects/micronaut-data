package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.runtime.multitenancy.TenantDataSourceResolver;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(property = "example.runtime-routing.enabled", value = "true")
public class RuntimeRoutingTenantDataSourceResolver implements TenantDataSourceResolver {

    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> creationCounts = new ConcurrentHashMap<>();

    // tag::resolver[]
    @Override
    public DataSource resolveTenantDataSource(String tenantId) {
        return tenantDataSources.computeIfAbsent(tenantId, this::createTenantDataSource);
    }
    // end::resolver[]

    int getCreationCount(String tenantId) {
        return creationCounts.getOrDefault(tenantId, new AtomicInteger()).get();
    }

    private DataSource createTenantDataSource(String tenantId) {
        creationCounts.computeIfAbsent(tenantId, ignore -> new AtomicInteger()).incrementAndGet();
        return RuntimeRoutingDataSourceFactory.buildPooledDataSource(tenantId);
    }
}
