package io.micronaut.data.jdbc.sqlite.jakarta_data.utilities;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * This utility class handles the caching and loading of test properties between the
 * client and container when tests are run inside an Arquillian container.
 */
public class TestPropertyHandler {

    private static final Logger log = Logger.getLogger(TestPropertyHandler.class.getCanonicalName());

    private static final String PROP_FILE = "tck.properties";
    private static Properties foundProperties;

    private TestPropertyHandler() {
        //UTILITY CLASS
    }

    /**
     * Container: Load properties from the TestProperty cache file, and return a properties object.
     * If any error occurs in finding the cache file, or loading the properties,
     * then an empty properties object is returned.
     *
     * @return - the cached properties, or an empty properties object.
     */
    static Properties loadProperties() {
        if (foundProperties != null) {
            return foundProperties;
        }

        //Try to load property file
        foundProperties = new Properties();
        InputStream propsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROP_FILE);
        if (propsStream != null) {
            try {
                foundProperties.load(propsStream);
            } catch (Exception e) {
                log.info("Attempted to load properties from resource " + PROP_FILE + " but failed. Because: " + e.getLocalizedMessage());
            }
        }

        return foundProperties;
    }
}
