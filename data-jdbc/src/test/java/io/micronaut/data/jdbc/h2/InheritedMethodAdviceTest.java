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
package io.micronaut.data.jdbc.h2;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Beans that inherit one method and declare different transaction or connection managers must each use their own.
 * Each test starts its own context, so the first call populates the interceptor caches.
 */
class InheritedMethodAdviceTest {

    private static final String SPEC_NAME = "InheritedMethodAdviceTest";

    @Test
    void inheritedTransactionalMethodUsesTheTransactionManagerOfEachBean() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            TransactionOperations<?> defaultTx = context.getBean(TransactionOperations.class, Qualifiers.byName("default"));
            TransactionOperations<?> otherTx = context.getBean(TransactionOperations.class, Qualifiers.byName("other"));
            DefaultTransactionalService defaultService = context.getBean(DefaultTransactionalService.class);
            OtherTransactionalService otherService = context.getBean(OtherTransactionalService.class);

            assertEquals(List.of("other read-write"), otherService.activeTransactions(defaultTx, otherTx));
            assertEquals(List.of("default read-only"), defaultService.activeTransactions(defaultTx, otherTx));
            assertEquals(List.of("other read-write"), otherService.activeTransactions(defaultTx, otherTx));
        }
    }

    @Test
    void transactionalFactoryBeansOfOneTypeUseTheirOwnTransactionManager() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            TransactionOperations<?> defaultTx = context.getBean(TransactionOperations.class, Qualifiers.byName("default"));
            TransactionOperations<?> otherTx = context.getBean(TransactionOperations.class, Qualifiers.byName("other"));
            ProducedTransactionalService defaultService = context.getBean(ProducedTransactionalService.class, Qualifiers.byName("default"));
            ProducedTransactionalService otherService = context.getBean(ProducedTransactionalService.class, Qualifiers.byName("other"));

            assertEquals(List.of("other read-write"), otherService.activeTransactions(defaultTx, otherTx));
            assertEquals(List.of("default read-only"), defaultService.activeTransactions(defaultTx, otherTx));
        }
    }

    @Test
    void inheritedConnectableMethodUsesTheConnectionManagerOfEachBean() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            ConnectionOperations<?> defaultConnection = context.getBean(ConnectionOperations.class, Qualifiers.byName("default"));
            ConnectionOperations<?> otherConnection = context.getBean(ConnectionOperations.class, Qualifiers.byName("other"));
            DefaultConnectableService defaultService = context.getBean(DefaultConnectableService.class);
            OtherConnectableService otherService = context.getBean(OtherConnectableService.class);

            assertEquals(List.of("other read-write"), otherService.activeConnections(defaultConnection, otherConnection));
            assertEquals(List.of("default read-only"), defaultService.activeConnections(defaultConnection, otherConnection));
            assertEquals(List.of("other read-write"), otherService.activeConnections(defaultConnection, otherConnection));
        }
    }

    private static Map<String, Object> properties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spec.name", SPEC_NAME);
        for (String dataSource : List.of("default", "other")) {
            String prefix = "datasources." + dataSource;
            properties.put(prefix + ".url", "jdbc:h2:mem:" + SPEC_NAME + dataSource + ";DB_CLOSE_ON_EXIT=FALSE");
            properties.put(prefix + ".driver-class-name", "org.h2.Driver");
            properties.put(prefix + ".username", "");
            properties.put(prefix + ".password", "");
            properties.put(prefix + ".dialect", "H2");
        }
        return properties;
    }

    abstract static class AbstractTransactionalService {

        public List<String> activeTransactions(TransactionOperations<?> defaultTx, TransactionOperations<?> otherTx) {
            List<String> active = new ArrayList<>();
            defaultTx.findTransactionStatus().ifPresent(status -> active.add("default " + mode(status.getTransactionDefinition().isReadOnly().orElse(false))));
            otherTx.findTransactionStatus().ifPresent(status -> active.add("other " + mode(status.getTransactionDefinition().isReadOnly().orElse(false))));
            return active;
        }
    }

    abstract static class AbstractConnectableService {

        public List<String> activeConnections(ConnectionOperations<?> defaultConnection, ConnectionOperations<?> otherConnection) {
            List<String> active = new ArrayList<>();
            defaultConnection.findConnectionStatus().ifPresent(status -> active.add("default " + mode(status.getDefinition().isReadOnly().orElse(false))));
            otherConnection.findConnectionStatus().ifPresent(status -> active.add("other " + mode(status.getDefinition().isReadOnly().orElse(false))));
            return active;
        }
    }

    private static String mode(boolean readOnly) {
        return readOnly ? "read-only" : "read-write";
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Transactional(readOnly = true)
    static class DefaultTransactionalService extends AbstractTransactionalService {
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Transactional("other")
    static class OtherTransactionalService extends AbstractTransactionalService {
    }

    static class ProducedTransactionalService extends AbstractTransactionalService {
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Factory
    static class TransactionalServiceFactory {

        @Singleton
        @Named("default")
        @Transactional(readOnly = true)
        ProducedTransactionalService defaultService() {
            return new ProducedTransactionalService();
        }

        @Singleton
        @Named("other")
        @Transactional("other")
        ProducedTransactionalService otherService() {
            return new ProducedTransactionalService();
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Connectable(readOnly = true)
    static class DefaultConnectableService extends AbstractConnectableService {
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Connectable("other")
    static class OtherConnectableService extends AbstractConnectableService {
    }
}
