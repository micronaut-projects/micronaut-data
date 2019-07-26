package io.micronaut.data.runtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent configuration interface.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface DataSettings {

    /**
     * Prefix for data related config.
     */
    String PREFIX = "micronaut.data";

    /**
     * The logger that should be used to log queries.
     */
    Logger QUERY_LOG = LoggerFactory.getLogger("io.micronaut.data.query");
}
