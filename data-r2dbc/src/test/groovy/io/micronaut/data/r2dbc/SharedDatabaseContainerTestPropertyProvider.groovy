package io.micronaut.data.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.testresources.client.TestResourcesClient
import io.micronaut.testresources.client.TestResourcesClientFactory

trait SharedTestResourcesDatabaseTestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    abstract ApplicationContext getApplicationContext()

    abstract int sharedSpecsCount()

    def cleanupSpec() {
        int testsCompleted = DbHolder.DB_TYPE_TEST_COUNT.compute(dbType(), (dbType, val) -> val ? val + 1 : 1)
        if (testsCompleted == sharedSpecsCount()) {
            try {
                TestResourcesClient testResourcesClient = TestResourcesClientFactory.extractFrom(getApplicationContext())
                testResourcesClient.closeScope(dbType())
            } catch (Exception e) {
                // Ignore
            }
        }
    }

}

