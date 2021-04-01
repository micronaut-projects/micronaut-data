package io.micronaut.data.r2dbc

import org.testcontainers.containers.JdbcDatabaseContainer

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class DbHolder {

    private static Map<String, JdbcDatabaseContainer> dbs = new HashMap<>()
    private static Map<String, AtomicInteger> usedCounter = new HashMap<>()

    static <T> T getContainerOrCreate(String dbName, Supplier<T> create) {
        return dbs.computeIfAbsent(dbName, { x -> create.get() }) as T
    }

    static void cleanup(String dbName, int cleanupOnCount) {
        if (usedCounter.computeIfAbsent(dbName, x -> new AtomicInteger(0)).incrementAndGet() == cleanupOnCount) {
            dbs.remove(dbName).stop()
        }
    }

}
