/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.runtime.multitenancy;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.runtime.multitenancy.TenantResolver;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A routing {@link DataSource} that delegates to a tenant-specific datasource resolved
 * at connection access time.
 *
 * <p>If no tenant identifier is available, the {@code defaultDataSource} is used.
 * When a tenant identifier is present, resolution is delegated to
 * {@link TenantDataSourceResolver}. The resolver can provide a preconfigured datasource
 * or lazily create and cache one for the tenant.</p>
 *
 * <p>If datasource resolution is expensive, {@link TenantDataSourceResolver} implementations
 * should cache per-tenant datasources. This class resolves the datasource on each delegated
 * {@link DataSource} call and does not maintain an internal tenant datasource cache.</p>
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Experimental
public final class TenantRoutingDataSource implements DataSource {

    private final DataSource defaultDataSource;
    private final TenantResolver tenantResolver;
    private final TenantDataSourceResolver tenantDataSourceResolver;

    /**
     * @param defaultDataSource The datasource to use when no tenant is resolved
     * @param tenantResolver The tenant resolver
     * @param tenantDataSourceResolver The tenant datasource resolver
     */
    public TenantRoutingDataSource(@NonNull DataSource defaultDataSource,
                                   @NonNull TenantResolver tenantResolver,
                                   @NonNull TenantDataSourceResolver tenantDataSourceResolver) {
        this.defaultDataSource = Objects.requireNonNull(defaultDataSource, "defaultDataSource cannot be null");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver cannot be null");
        this.tenantDataSourceResolver = Objects.requireNonNull(tenantDataSourceResolver, "tenantDataSourceResolver cannot be null");
    }

    /**
     * Resolve the current datasource.
     *
     * @return The current datasource
     */
    @NonNull
    public DataSource resolveCurrentDataSource() {
        Serializable tenantId = tenantResolver.resolveTenantIdentifier();
        if (tenantId == null) {
            return defaultDataSource;
        }
        return tenantDataSourceResolver.resolveTenantDataSource(tenantId.toString());
    }

    /**
     * Resolve the current tenant identifier.
     *
     * @return The current tenant identifier
     */
    @Nullable
    public String resolveCurrentTenantId() {
        Serializable tenantId = tenantResolver.resolveTenantIdentifier();
        if (tenantId == null) {
            return null;
        }
        return tenantId.toString();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return resolveCurrentDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return resolveCurrentDataSource().getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return resolveCurrentDataSource().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return resolveCurrentDataSource().isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return resolveCurrentDataSource().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        resolveCurrentDataSource().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        resolveCurrentDataSource().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return resolveCurrentDataSource().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return resolveCurrentDataSource().getParentLogger();
    }
}
