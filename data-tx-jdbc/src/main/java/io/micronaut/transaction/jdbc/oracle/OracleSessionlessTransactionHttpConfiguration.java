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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;

/**
 * Configuration for HTTP propagation of Oracle sessionless transaction ids.
 *
 * @since 5.2.0
 */
@Experimental
@ConfigurationProperties(OracleSessionlessTransactionHttpConfiguration.PREFIX)
public final class OracleSessionlessTransactionHttpConfiguration {

    /**
     * The configuration prefix for Oracle sessionless transaction HTTP propagation.
     */
    public static final String PREFIX = "micronaut.data.oracle.sessionless.http";

    /**
     * The default HTTP header that carries the encoded sessionless transaction id.
     */
    public static final String DEFAULT_HEADER_NAME = "Oracle-Sessionless-Transaction-Id";

    private boolean propagationEnabled;
    private String headerName = DEFAULT_HEADER_NAME;

    /**
     * @return Whether HTTP propagation of Oracle sessionless transaction ids is enabled.
     */
    public boolean isPropagationEnabled() {
        return propagationEnabled;
    }

    /**
     * Sets whether HTTP propagation of Oracle sessionless transaction ids is enabled.
     *
     * @param propagationEnabled Whether HTTP propagation is enabled
     */
    public void setPropagationEnabled(boolean propagationEnabled) {
        this.propagationEnabled = propagationEnabled;
    }

    /**
     * @return The HTTP header that carries the encoded sessionless transaction id.
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * Sets the HTTP header that carries the encoded sessionless transaction id.
     *
     * @param headerName The header name
     */
    public void setHeaderName(String headerName) {
        if (StringUtils.isNotEmpty(headerName)) {
            this.headerName = headerName;
        }
    }
}
