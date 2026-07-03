package io.micronaut.data.jdbc.sqlite.jakarta_data.utilities;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * <p> This enum represents the different test properties used within this TCK.
 * Each one is given a description and documentation will automatically be created in the TCK distribution. </p>
 *
 * <p> When a test property is requested from the client, we expect these properties to be available from the system.</p>
 */
public enum TestProperty {
    //Java properties that should always be set by the JVM
    javaHome    (true,  "java.home",
            "Path to the java executable used to create the current JVM"),
    javaSpecVer (true,  "java.specification.version",
            "Specification version of the java executable"),
    javaTempDir (true,  "java.io.tmpdir",
            "The path to a temporary directory where a copy of the signature file will be created"),
    javaVer     (true,  "java.version",
            "Full version of the java executable"),

    //TCK specific properties
    skipDeployment (false,  "jakarta.tck.skip.deployment",
            "If true, run in SE mode and do not use Arquillian deployment, if false run in EE mode and use Arquillian deployments. "
            + "Default: false", "false"),
    pollFrequency  (false, "jakarta.tck.poll.frequency",
            "Time in seconds between polls of the repository to verify read-only data was successfully written. "
            + "Default: 1 second", "1"),
    pollTimeout    (false, "jakarta.tck.poll.timeout",
            "Time in seconds when we will stop polling to verify read-only data was successfully written. "
            + "Default: 60 seconds", "60"),
    delay          (false, "jakarta.tck.consistency.delay",
            "Time in seconds after verifying read-only data was successfully written to respository "
            + "for repository to have consistency. "
            + "Default: none"),
    databaseType   (false, "jakarta.tck.database.type",
            "The type of database being used. Valid values are " + Arrays.asList(DatabaseType.values()).toString() + " (case insensitive). "
            + "The database type is used to make assertions based on the keywords supported by the underlying database. "
            + "Default: OTHER", "OTHER"),
    databaseName   (false, "jakarta.tck.database.name",
            "The name of database being used. The database name is used to make assertions based on the underlying database. "
            + "Default: none"),

    //Signature testing properties
    signatureClasspath (false,  "signature.sigTestClasspath", "The path to the Jakarta Data API JAR used by your implementation. "
            + "Required for standalone testing, but optional when testing on a Jakarta EE profile. "
            + "Default: none"),
    signatureImageDir  (true,   "jimage.dir",                 "The path to a directory that is readable and writable that "
            + "the signature test will cache Java SE modules as classes. "
            + "Default: none");

    private boolean required;
    private String key;
    private String value;
    private String description;

    // CONSTRUCTORS
    private TestProperty(boolean required, String key, String description) {
        this(required, key, description, null);
    }

    private TestProperty(boolean required, String key, String description, String defaultValue) {
        this.required = required;
        this.key = key;
        this.description = description;
        this.value = getValue(defaultValue);
    }

    // GETTERS
    public boolean isRequired() {
        return required;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    // COMPARISONS
    public boolean equals(String expectedValue) {
        return getValue().equalsIgnoreCase(expectedValue);
    }

    public boolean isSet() {
        if(value == null)
            return false;
        if(value.isBlank() || value.isEmpty()) {
            return false;
        }
        return true;
    }

    // CONVERTERS
    public long getLong() throws IllegalStateException, NumberFormatException {
        return Long.parseLong(value);
    }

    public int getInt() throws IllegalStateException, NumberFormatException {
        return Integer.parseInt(value);
    }

    public boolean getBoolean() {
        return Boolean.parseBoolean(value);
    }

    public DatabaseType getDatabaseType() {
        return DatabaseType.valueOfIgnoreCase(value);
    }

    /**
     * Get the test property value.
     *
     * @return the property value
     * @throws IllegalStateException if required and no property was found
     */
    public String getValue() {
        if(required && value == null)
            throw new IllegalStateException("Could not obtain a value for system property: " + key);

        return value;
    }

    private String getValue(String defaultVal) throws IllegalStateException {
        final Logger log = Logger.getLogger(TestProperty.class.getCanonicalName());

        String valueLocal = null;
        log.fine("Searching for property: " + key);

        // Client: get property from system
        if(valueLocal == null) {
            valueLocal = System.getProperty(key);
            log.fine("Value from system: " + valueLocal);
        }

        //Container: get property from properties file
        if(valueLocal == null) {
            valueLocal = TestPropertyHandler.loadProperties().getProperty(key);
            log.fine("Value from resource file: " + valueLocal);
        }

        //Default: get default property
        if(valueLocal == null) {
            valueLocal = defaultVal;
            log.fine("Value set to default: " + valueLocal);
        }

        if (valueLocal == null) {
            log.fine("Property was not set, value: " + null);
        }

        return valueLocal;
    }
}
