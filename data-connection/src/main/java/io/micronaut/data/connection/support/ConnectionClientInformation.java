package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Nullable;

import java.util.Properties;

/**
 * The client information that can be used to set to {@link java.sql.Connection#setClientInfo(Properties)}.
 *
 * @param clientId The client ID
 * @param module   The module
 * @param action   The action
 */
public record ConnectionClientInformation(@Nullable String clientId, String module, String action) {
}
