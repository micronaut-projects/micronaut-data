package io.micronaut.data.jdbc


import org.testcontainers.containers.JdbcDatabaseContainer

trait SharedDatabaseContainerTestPropertyProvider implements DatabaseTestPropertyProvider {

    abstract int sharedSpecsCount()

    @Override
    JdbcDatabaseContainer getDatabaseContainer(String driverName) {
        return DbHolder.getContainerOrCreate(driverName, () -> super.getDatabaseContainer(driverName))
    }

    def cleanupSpec() {
        DbHolder.cleanup(driverName(), sharedSpecsCount())
    }

}

