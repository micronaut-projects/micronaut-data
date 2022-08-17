package io.micronaut.data.jdbc

import io.micronaut.testresources.client.TestResourcesClient
import io.micronaut.testresources.client.TestResourcesClientFactory

import java.lang.reflect.Method

trait SharedTestResourcesDatabaseTestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    abstract int sharedSpecsCount()

    def cleanupSpec() {
        int testsCompleted = DbHolder.DB_TYPE_TEST_COUNT.compute(dbType(), (dbType, val) -> val ? val + 1 : 1)
        if (testsCompleted == sharedSpecsCount()) {
            URL config = SharedTestResourcesDatabaseTestPropertyProvider.class.getResource("/test-resources.properties")
            try {
                Method m = TestResourcesClientFactory.class.getDeclaredMethod("configuredAt", URL.class)
                m.setAccessible(true)
                TestResourcesClient testResourcesClient = (TestResourcesClient) m.invoke(null, config)
                testResourcesClient.closeScope(dbType())
            } catch (Exception e) {
                // Ignore
            }
        }
    }

}

