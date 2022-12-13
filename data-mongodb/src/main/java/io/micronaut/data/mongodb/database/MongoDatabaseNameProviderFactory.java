/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.database;

import com.mongodb.ConnectionString;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.mongodb.operations.DefaultMongoDatabaseNameProvider;
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import jakarta.inject.Singleton;

/**
 * MongoDB {@link MongoDatabaseNameProvider} factory.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Factory
final class MongoDatabaseNameProviderFactory {

    @Primary
    @Singleton
    MongoDatabaseNameProvider primaryMongoDatabaseNameProvider(DefaultMongoConfiguration mongoConfiguration,
                                                               BeanContext beanContext,
                                                               RuntimeEntityRegistry runtimeEntityRegistry,
                                                               @Nullable
                                                               SchemaTenantResolver tenantResolver) {
        String defaultDatabaseName = mongoConfiguration.getConnectionString()
            .map(ConnectionString::getDatabase)
            .filter(StringUtils::isNotEmpty)
            .orElse(null);
        return new DefaultMongoDatabaseNameProvider(beanContext, null, runtimeEntityRegistry, defaultDatabaseName, tenantResolver);
    }

    @EachBean(NamedMongoConfiguration.class)
    @Singleton
    MongoDatabaseNameProvider namedMongoDatabaseNameProvider(NamedMongoConfiguration mongoConfiguration,
                                                             @Parameter String server,
                                                             BeanContext beanContext,
                                                             RuntimeEntityRegistry runtimeEntityRegistry,
                                                             @Nullable
                                                             SchemaTenantResolver tenantResolver) {
        String defaultDatabaseName = mongoConfiguration.getConnectionString()
            .map(ConnectionString::getDatabase)
            .filter(StringUtils::isNotEmpty)
            .orElse(null);
        return new DefaultMongoDatabaseNameProvider(beanContext, server, runtimeEntityRegistry, defaultDatabaseName, tenantResolver);
    }

}
