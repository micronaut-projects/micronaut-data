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
package io.micronaut.data.neo4j.session;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.BookmarkManager;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

/**
 * Builds the configuration of the sessions opened on one driver: the database, which is the tenant's database when
 * multi-tenancy uses a schema per tenant, and the bookmark manager shared by the sessions of the driver so that each
 * session sees the writes of the sessions before it.
 *
 * <p>The database of the default driver is set with {@code micronaut.data.neo4j.database}, and that of a driver named
 * {@code name} with {@code micronaut.data.neo4j.databases.name}. Bookmarks are shared unless
 * {@code micronaut.data.neo4j.bookmarks} is {@code false}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(Neo4jDriverRegistration.class)
@Internal
public final class Neo4jSessionConfigurer {

    private final String name;
    private final @Nullable String database;
    private final @Nullable BookmarkManager bookmarkManager;
    private final @Nullable SchemaTenantResolver tenantResolver;

    Neo4jSessionConfigurer(@Parameter Neo4jDriverRegistration registration,
                           PropertyResolver properties,
                           @Nullable SchemaTenantResolver tenantResolver) {
        this.name = registration.name();
        Driver driver = registration.driver();
        String key = "default".equals(this.name) ? "micronaut.data.neo4j.database" : "micronaut.data.neo4j.databases." + this.name;
        this.database = properties.get(key, String.class).filter(value -> !value.isBlank()).orElse(null);
        boolean bookmarks = properties.get("micronaut.data.neo4j.bookmarks", Boolean.class).orElse(true);
        this.bookmarkManager = bookmarks ? driver.executableQueryBookmarkManager() : null;
        this.tenantResolver = tenantResolver;
    }

    /**
     * @return The name of the driver
     */
    public String getName() {
        return name;
    }

    /**
     * @return The configuration of a new session
     */
    public SessionConfig sessionConfig() {
        SessionConfig.Builder builder = SessionConfig.builder();
        String tenantDatabase = tenantResolver == null ? null : tenantResolver.resolveTenantSchemaName();
        String sessionDatabase = tenantDatabase != null ? tenantDatabase : database;
        if (sessionDatabase != null) {
            builder.withDatabase(sessionDatabase);
        }
        if (bookmarkManager != null) {
            builder.withBookmarkManager(bookmarkManager);
        }
        return builder.build();
    }
}
