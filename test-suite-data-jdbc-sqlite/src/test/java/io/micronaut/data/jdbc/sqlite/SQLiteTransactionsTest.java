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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;
import io.micronaut.data.tck.services.TxBookService;
import io.micronaut.data.tck.services.TxEventsService;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteTransactionsTest {

    private static final long CONNECTIONS = 1000;

    private ApplicationContext context;
    private DataSourceTransactionManager transactionOperations;
    private DefaultDataSourceConnectionOperations connectionOperations;
    private TxBookService txBookService;
    private TxEventsService txEventsService;

    @BeforeAll
    void setupContext() {
        context = ApplicationContext.run(createProperties());
        transactionOperations = context.getBean(DataSourceTransactionManager.class);
        connectionOperations = context.getBean(DefaultDataSourceConnectionOperations.class);
        txBookService = context.getBean(TxBookService.class);
        txEventsService = context.getBean(TxEventsService.class);
    }

    @AfterEach
    void cleanup() {
        bookService().cleanup();
        txEventsService.cleanup();
    }

    @AfterAll
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void connectableWithNestedTransaction() {
        assertDoesNotThrow(() -> {
            try {
                bookService().bookAddedInConnectableNestedTransaction();
                assertEquals(1, bookService().countBooksTransactional());
            } catch (NoClassDefFoundError ignored) {
            }
        });
    }

    @Test
    void customNameTransaction() {
        bookService().bookAddedCustomNamedTransaction(() -> {
            var status = transactionOperations.findTransactionStatus().orElseThrow();
            if (!"MyTx".equals(status.getTransactionDefinition().getName())) {
                throw new IllegalStateException("Expected a custom TX name!");
            }
        });

        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInReadOnlyTransactionNotThrowingError() {
        if (!supportsReadOnlyFlag() || failsInsertInReadOnlyTx()) {
            return;
        }

        assertDoesNotThrow(() -> bookService().bookAddedInReadOnlyTransaction());
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testReadOnlyTransactionAddingBookInInnerTransactionNotThrowingError() {
        if (!supportsReadOnlyFlag() || failsInsertInReadOnlyTx()) {
            return;
        }

        assertDoesNotThrow(() -> bookService().readOnlyTxCallingAddingBookInAnotherTransaction());
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInNeverPropagation() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        bookService().bookAddedInNeverPropagation(noTxCheck());

        assertEquals(supportsModificationInNonTransaction() ? 1 : 0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInNeverPropagationSync() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        bookService().bookAddedInNeverPropagationSync(noTxCheck());

        assertEquals(supportsModificationInNonTransaction() ? 1 : 0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInInnerNeverPropagation() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        Exception e = assertThrows(Exception.class, () -> bookService().bookAddedInInnerNeverPropagation(noTxCheck()));

        assertEquals("Existing transaction found for transaction marked with propagation 'never'", e.getMessage());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInInnerNeverPropagationSync() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        Exception e = assertThrows(Exception.class, () -> bookService().bookAddedInInnerNeverPropagationSync(noTxCheck()));

        assertEquals("Existing transaction found for transaction marked with propagation 'never'", e.getMessage());
        assertTrue(transactionOperations.findTransactionStatus().isEmpty());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInNotSupportedPropagation() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        bookService().bookAddedInNoSupportedPropagation(noTxCheck());

        assertEquals(supportsModificationInNonTransaction() ? 1 : 0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInNotSupportedPropagationAndFailed() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        assertThrows(Exception.class, () -> bookService().bookAddedInNoSupportedPropagationAndFailed(noTxCheck()));
        assertEquals(supportsModificationInNonTransaction() ? 1 : 0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInInnerNotSupportedPropagationAndFailedWithExceptionSuppressed() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        bookService().bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed(noTxCheck());

        assertEquals(supportsModificationInNonTransaction() ? 1 : 0, bookService().countBooksTransactional());
    }

    @Test
    void testBookAddedInInnerNotSupportedPropagation() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        bookService().bookAddedInInnerNoSupportedPropagation(noTxCheck());

        assertEquals(supportsModificationInNonTransaction() ? 1 : 0, bookService().countBooksTransactional());
    }

    @Test
    void testMandatoryTransactionMissing() {
        Exception e = assertThrows(Exception.class, () -> bookService().mandatoryTransaction());
        assertEquals("No existing transaction found for transaction marked with propagation 'mandatory'", e.getMessage());
    }

    @Test
    void testMandatoryTransactionMissingSync() {
        Exception e = assertThrows(Exception.class, () -> bookService().mandatoryTransactionSync());
        assertEquals("No existing transaction found for transaction marked with propagation 'mandatory'", e.getMessage());
        assertTrue(transactionOperations.findTransactionStatus().isEmpty());
    }

    @Test
    void testBookIsAddedInMandatoryTransaction() {
        bookService().bookAddedInMandatoryTransaction();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInMandatoryTransactionSync() {
        bookService().bookAddedInMandatoryTransactionSync();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testInnerTransactionWithSuppressedException() {
        Exception e = assertThrows(Exception.class, () -> bookService().innerTransactionHasSuppressedException());
        assertEquals("Transaction rolled back because it has been marked as rollback-only", e.getMessage());
    }

    @Test
    void testInnerTransactionWithSuppressedExceptionSync() {
        Exception e = assertThrows(Exception.class, () -> bookService().innerTransactionHasSuppressedExceptionSync());
        assertEquals("Transaction rolled back because it has been marked as rollback-only", e.getMessage());
        assertTrue(transactionOperations.findTransactionStatus().isEmpty());
    }

    @Test
    void testInnerTransactionWithSuppressedExceptionSync2() {
        Exception e = assertThrows(Exception.class, () -> bookService().innerTransactionHasSuppressedExceptionSync2());
        assertEquals("Transaction rolled back because it has been marked as rollback-only", e.getMessage());
        assertTrue(transactionOperations.findTransactionStatus().isEmpty());
    }

    @Test
    void testInnerTransactionMarkedForRollback() {
        Exception e = assertThrows(Exception.class, () -> bookService().innerTransactionMarkedForRollback(
            () -> transactionOperations.findTransactionStatus().orElseThrow().setRollbackOnly()
        ));
        assertEquals("Transaction rolled back because it has been marked as rollback-only", e.getMessage());
    }

    @Test
    void testTransactionMarkedForRollback() {
        bookService().saveAndMarkedForRollback(() -> transactionOperations.findTransactionStatus().orElseThrow().setRollbackOnly());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testTransactionMarkedForRollback2() {
        bookService().saveAndMarkedForRollback2(() -> transactionOperations.findTransactionStatus().orElseThrow().setRollbackOnly());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testInnerRequiresNewTransactionWithSuppressedException() {
        bookService().innerRequiresNewTransactionHasSuppressedException();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInAnotherRequiresNewTx() {
        bookService().bookIsAddedInAnotherRequiresNewTx();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInAnotherRequiresNewTxSync() {
        bookService().bookIsAddedInAnotherRequiresNewTxSync();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInAnotherRequiresNewTxWhichIsFailing() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bookService().bookIsAddedInAnotherRequiresNewTxWhichIsFailing());
        assertEquals("Big fail!", e.getMessage());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInTheMainTxAndAnotherRequiresNewTxIsFailing() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bookService().bookIsAddedAndAnotherRequiresNewTxIsFailing());
        assertEquals("Big fail!", e.getMessage());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInTheMainTxAndAnotherRequiresNewTxIsFailingSync() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bookService().bookIsAddedAndAnotherRequiresNewTxIsFailingSync());
        assertEquals("Big fail!", e.getMessage());
        assertEquals(0, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInNestedTx() {
        if (!supportsNestedTx()) {
            return;
        }

        bookService().bookAddedInNestedTransaction();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInNestedTxSync() {
        if (!supportsNestedTx()) {
            return;
        }

        bookService().bookAddedInNestedTransactionSync();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInAnotherNestedTx() {
        if (!supportsNestedTx()) {
            return;
        }

        bookService().bookAddedInAnotherNestedTransaction();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testBookIsAddedInAnotherNestedTxSync() {
        if (!supportsNestedTx()) {
            return;
        }

        bookService().bookAddedInAnotherNestedTransactionSync();
        assertEquals(1, bookService().countBooksTransactional());
    }

    @Test
    void testThatConnectionsAreNeverExhausted1() {
        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookIsAddedInTxMethod();
        }
        assertEquals(CONNECTIONS, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted2() {
        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookIsAddedInAnotherRequiresNewTxSync();
        }
        assertEquals(CONNECTIONS, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted3() {
        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().innerRequiresNewTransactionHasSuppressedException();
        }
        assertEquals(CONNECTIONS, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted4() {
        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookAddedInMandatoryTransaction();
        }
        assertEquals(CONNECTIONS, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted5() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookAddedInInnerNoSupportedPropagation(noTxCheck());
        }
        assertEquals(supportsModificationInNonTransaction() ? CONNECTIONS : 0, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted6() {
        if (!supportsNoTxProcessing()) {
            return;
        }

        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookAddedInNeverPropagation(noTxCheck());
        }
        assertEquals(supportsModificationInNonTransaction() ? CONNECTIONS : 0, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted7() {
        if (!supportsNestedTx()) {
            return;
        }

        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookAddedInNestedTransaction();
        }
        assertEquals(CONNECTIONS, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted8() {
        if (!supportsNestedTx()) {
            return;
        }

        for (int i = 0; i < CONNECTIONS; i++) {
            bookService().bookAddedInNestedTransactionSync();
        }
        assertEquals(CONNECTIONS, bookService().countBooks());
    }

    @Test
    void testThatConnectionsAreNeverExhausted9() {
        for (int i = 0; i < CONNECTIONS; i++) {
            try {
                bookService().innerTransactionHasSuppressedExceptionSync();
            } catch (Exception e) {
                assertEquals("Transaction rolled back because it has been marked as rollback-only", e.getMessage());
            }
        }
        assertEquals(0, bookService().countBooks());
    }

    @Test
    void testTransactionalEventsHandling() throws Exception {
        txEventsService.insertWithTransaction();

        assertEquals("The Stand", txEventsService.getLastEvent().title());
        assertEquals(1, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "BEFORE COMMIT: false",
            "BEFORE COMPLETION",
            "AFTER COMMIT",
            "AFTER COMPLETION: COMMITTED"
        ), txEventsService.getEvents());

        txEventsService.cleanup();
        RuntimeException runtime = assertThrows(RuntimeException.class, () -> txEventsService.insertAndRollback());
        assertEquals("Bad things happened", runtime.getMessage());
        assertNull(txEventsService.getLastEvent());
        assertEquals(0, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "BEFORE COMPLETION",
            "AFTER COMPLETION: ROLLED_BACK"
        ), txEventsService.getEvents());

        txEventsService.cleanup();
        runtime = assertThrows(RuntimeException.class, () -> txEventsService.insertAndRollbackWithOuterTransaction());
        assertEquals("Bad things happened", runtime.getMessage());
        assertNull(txEventsService.getLastEvent());
        assertEquals(0, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "ENTER INNER",
            "OUTER BEFORE COMPLETION",
            "BEFORE COMPLETION",
            "OUTER AFTER COMPLETION: ROLLED_BACK",
            "AFTER COMPLETION: ROLLED_BACK"
        ), txEventsService.getEvents());

        txEventsService.cleanup();
        Exception checked = assertThrows(Exception.class, () -> txEventsService.insertAndRollbackChecked());
        assertEquals("Bad things happened", checked.getMessage());
        assertNull(txEventsService.getLastEvent());
        assertEquals(0, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "BEFORE COMPLETION",
            "AFTER COMPLETION: ROLLED_BACK"
        ), txEventsService.getEvents());

        txEventsService.cleanup();
        checked = assertThrows(Exception.class, () -> txEventsService.insertAndRollbackCheckedWithOuterTransaction());
        assertEquals("Bad things happened", checked.getMessage());
        assertNull(txEventsService.getLastEvent());
        assertEquals(0, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "ENTER INNER",
            "OUTER BEFORE COMPLETION",
            "BEFORE COMPLETION",
            "OUTER AFTER COMPLETION: ROLLED_BACK",
            "AFTER COMPLETION: ROLLED_BACK"
        ), txEventsService.getEvents());

        txEventsService.cleanup();
        assertThrows(IOException.class, () -> txEventsService.insertAndRollbackDontRollbackOn());
        if (supportsDontRollbackOn()) {
            assertEquals(1, txEventsService.countBooksTransactional());
            assertTrue(txEventsService.getLastEvent() != null);
        } else {
            assertEquals(0, txEventsService.countBooksTransactional());
            assertNull(txEventsService.getLastEvent());
        }

        txEventsService.cleanup();
        txEventsService.insertWithOuterTransaction();
        assertEquals("The Stand", txEventsService.getLastEvent().title());
        assertEquals(1, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "ENTER INNER",
            "EXIT INNER",
            "OUTER BEFORE COMMIT: false",
            "BEFORE COMMIT: false",
            "OUTER BEFORE COMPLETION",
            "BEFORE COMPLETION",
            "OUTER AFTER COMMIT",
            "AFTER COMMIT",
            "OUTER AFTER COMPLETION: COMMITTED",
            "AFTER COMPLETION: COMMITTED"
        ), txEventsService.getEvents());

        txEventsService.cleanup();
        txEventsService.insertWithOuterNewTransaction();
        assertEquals("The Stand", txEventsService.getLastEvent().title());
        assertEquals(1, txEventsService.countBooksTransactional());
        assertEquals(List.of(
            "ENTER INNER",
            "BEFORE COMMIT: false",
            "BEFORE COMPLETION",
            "AFTER COMMIT",
            "AFTER COMPLETION: COMMITTED",
            "EXIT INNER",
            "OUTER BEFORE COMMIT: false",
            "OUTER BEFORE COMPLETION",
            "OUTER AFTER COMMIT",
            "OUTER AFTER COMPLETION: COMMITTED"
        ), txEventsService.getEvents());
    }

    @Test
    void testTxManaged() {
        assertTrue(transactionOperations.findTransactionStatus().isEmpty());
        bookService().checkInTransaction(() -> assertTrue(transactionOperations.findTransactionStatus().isPresent()));
        assertTrue(transactionOperations.findTransactionStatus().isEmpty());
    }

    private TxBookService bookService() {
        txBookService.transactionManager = castTransactionManager(transactionOperations);
        txBookService.connectionOperations = castConnectionOperations(connectionOperations);
        return txBookService;
    }

    private Runnable noTxCheck() {
        return () -> {
            var status = connectionOperations.findConnectionStatus();
            if (status.isEmpty()) {
                return;
            }
            Connection connection = (Connection) status.get().getConnection();
            try {
                assertTrue(connection.getAutoCommit());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private boolean supportsNoTxProcessing() {
        return true;
    }

    private boolean supportsModificationInNonTransaction() {
        return true;
    }

    private boolean supportsDontRollbackOn() {
        return true;
    }

    private boolean supportsReadOnlyFlag() {
        return false;
    }

    private boolean failsInsertInReadOnlyTx() {
        return false;
    }

    private boolean supportsNestedTx() {
        return true;
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqlitetransactions".toLowerCase(Locale.ENGLISH), ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("bookRepositoryClass", SQLiteBookRepository.class.getName());
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", List.of(
                "io.micronaut.data.jdbc.sqlite",
                "io.micronaut.data.tck.entities",
                "io.micronaut.data.tck.jdbc.entities"
            ));
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SynchronousTransactionManager<Object> castTransactionManager(DataSourceTransactionManager transactionManager) {
        return (SynchronousTransactionManager) transactionManager;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ConnectionOperations<Object> castConnectionOperations(DefaultDataSourceConnectionOperations connectionOperations) {
        return (ConnectionOperations) connectionOperations;
    }
}
