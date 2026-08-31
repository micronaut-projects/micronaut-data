package io.micronaut.data.jdbc.sqlite.jakarta_data.utilities;

import java.util.Arrays;

/**
 * This enum represents the configured DatabaseType based on the {@link TestProperty} databaseType
 */
public enum DatabaseType {
    OTHER(Integer.MAX_VALUE), //No database type was configured
    RELATIONAL(100),
    GRAPH(50),
    DOCUMENT(40),
    TIME_SERIES(30),
    COLUMN(20),
    KEY_VALUE(10);

    private int flexibility;

    private DatabaseType(int flexibility) {
        this.flexibility = flexibility;
    }

    public static DatabaseType valueOfIgnoreCase(String value) {
        return Arrays.stream(DatabaseType.values()).filter(type -> type.name().equalsIgnoreCase(value)).findAny().orElse(DatabaseType.OTHER);
    }

    public boolean isKeywordSupportAtOrBelow(DatabaseType benchmark) {
        return this.flexibility <= benchmark.flexibility;
    }
}
