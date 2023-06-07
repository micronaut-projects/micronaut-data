package io.micronaut.data.jdbc

import io.micronaut.testresources.client.TestResourcesClient
import io.micronaut.testresources.client.TestResourcesClientFactory

trait SharedTestResourcesDatabaseTestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    abstract int sharedSpecsCount()

    def cleanupSpec() {
        int testsCompleted = DbHolder.DB_TYPE_TEST_COUNT.compute(dbType(), (dbType, val) -> val ? val + 1 : 1)
        if (testsCompleted == sharedSpecsCount()) {
            try {
                TestResourcesClient testResourcesClient = TestResourcesClientFactory.fromSystemProperties().get()
                testResourcesClient.closeScope(dbType())
            } catch (Exception e) {
                // Ignore
            }
        }
    }

}

