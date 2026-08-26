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
package io.micronaut.transaction.support;


import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.sessionless.SessionlessTransactionContext;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

/**
 * Transaction utils.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class TransactionUtil {

    private TransactionUtil() {
    }

    /**
     * Creates a transaction definition from a given name and annotation metadata provider.
     *
     * @param name                       The name
     * @param annotationMetadataProvider The annotation metadata
     * @return the transaction definition
     */
    @NonNull
    public static TransactionDefinition getTransactionDefinition(String name, AnnotationMetadataProvider annotationMetadataProvider) {
        AnnotationValue<Transactional> annotation = annotationMetadataProvider.getAnnotation(Transactional.class);
        if (annotation == null) {
            return TransactionDefinition.DEFAULT;
        }

        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName(name);
        definition.setReadOnly(annotation.isTrue("readOnly"));
        annotation.intValue("timeout").ifPresent(timeout -> definition.setTimeout(Duration.ofSeconds(timeout)));
        final Class[] rollbackFor = annotation.classValues("rollbackFor");
        //noinspection unchecked
        definition.setRollbackOn(Arrays.asList(rollbackFor));
        final Class[] noRollbackFors = annotation.classValues("noRollbackFor");
        //noinspection unchecked
        definition.setDontRollbackOn(Arrays.asList(noRollbackFors));
        annotation.enumValue("propagation", TransactionDefinition.Propagation.class)
                .ifPresent(definition::setPropagationBehavior);
        annotation.enumValue("isolation", TransactionDefinition.Isolation.class)
                .ifPresent(definition::setIsolationLevel);

        AnnotationValue<OracleTransactional> oracleTransactional = annotationMetadataProvider.getAnnotation(OracleTransactional.class);
        if (oracleTransactional != null) {
            OracleTransactional.Priority priority = oracleTransactional.enumValue("priority", OracleTransactional.Priority.class)
                .orElse(OracleTransactional.Priority.HIGH);
            definition.putProperty(OracleTransactional.ORACLE_PRIORITY, priority);
            OracleTransactional.Sessionless sessionless = oracleTransactional.enumValue("sessionless", OracleTransactional.Sessionless.class)
                .orElse(OracleTransactional.Sessionless.NONE);
            if (sessionless != OracleTransactional.Sessionless.NONE) {
                definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, sessionless);
            }
        }

        return definition;
    }

    /**
     * Resolves Oracle transaction priority from a transaction definition.
     *
     * @param definition The transaction definition
     * @return The Oracle transaction priority, or {@code null} if none is present
     */
    public static OracleTransactional.@Nullable Priority getOraclePriority(TransactionDefinition definition) {
        Object value = definition.getProperties().get(OracleTransactional.ORACLE_PRIORITY);
        if (value instanceof OracleTransactional.Priority priority) {
            return priority;
        }
        if (value instanceof String priority) {
            return parseOraclePriority(priority);
        }
        if (value instanceof Enum<?> priority) {
            return parseOraclePriority(priority.name());
        }
        return null;
    }

    /**
     * Resolves Oracle sessionless transaction mode from a transaction definition.
     *
     * @param definition The transaction definition
     * @return The Oracle sessionless transaction mode, or {@code null} if none is present
     */
    public static OracleTransactional.@Nullable Sessionless getOracleSessionlessMode(TransactionDefinition definition) {
        Object value = definition.getProperties().get(OracleTransactional.ORACLE_SESSIONLESS_MODE);
        OracleTransactional.Sessionless mode = null;
        if (value instanceof OracleTransactional.Sessionless sessionless) {
            mode = sessionless;
        } else if (value instanceof String sessionless) {
            mode = parseOracleSessionlessMode(sessionless);
        } else if (value instanceof Enum<?> sessionless) {
            mode = parseOracleSessionlessMode(sessionless.name());
        }
        return mode == OracleTransactional.Sessionless.NONE ? null : mode;
    }

    /**
     * Validates the propagation of a transaction definition that uses Oracle sessionless transaction mode.
     *
     * @param definition The transaction definition
     */
    public static void validateOracleSessionlessPropagation(TransactionDefinition definition) {
        OracleTransactional.Sessionless mode = getOracleSessionlessMode(definition);
        if (mode == null) {
            return;
        }
        if (definition.getPropagationBehavior() != TransactionDefinition.Propagation.REQUIRED) {
            throw new TransactionUsageException(
                "Oracle sessionless transaction mode '" + mode + "' requires propagation 'REQUIRED'"
            );
        }
    }

    /**
     * Rejects a transaction definition that carries an Oracle sessionless transaction mode but was not
     * started by the transactional advice.
     *
     * <p>Sessionless mode is applied by {@code TransactionalInterceptor}, not by any transaction manager.
     * A definition built by hand and passed straight to
     * {@link io.micronaut.transaction.TransactionOperations#execute} would otherwise run as an ordinary
     * transaction with the mode silently ignored.</p>
     *
     * @param definition The transaction definition
     */
    public static void rejectUnmanagedOracleSessionlessMode(TransactionDefinition definition) {
        OracleTransactional.Sessionless mode = getOracleSessionlessMode(definition);
        if (mode == null || SessionlessTransactionContext.isActive()) {
            return;
        }
        throw new TransactionUsageException(
            "Oracle sessionless transaction mode '" + mode + "' is only applied to methods annotated with "
                + "@OracleTransactional; it cannot be requested through a programmatic transaction definition"
        );
    }

    private static OracleTransactional.Priority parseOraclePriority(String priority) {
        try {
            return OracleTransactional.Priority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CannotCreateTransactionException("Invalid Oracle transaction priority: " + priority, e);
        }
    }

    private static OracleTransactional.Sessionless parseOracleSessionlessMode(String mode) {
        try {
            return OracleTransactional.Sessionless.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CannotCreateTransactionException("Invalid Oracle sessionless transaction mode: " + mode, e);
        }
    }

}
