package io.micronaut.data.r2dbc

import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.testresources.client.TestResourcesClient
import io.micronaut.testresources.client.TestResourcesClientFactory

import java.lang.reflect.Method

trait CleanupTestResourcesDatabaseTestPropertyProvider implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return ['micronaut.test.resources.scope': getClass().getSimpleName()]
    }

    def cleanupSpec() {
        URL config = CleanupTestResourcesDatabaseTestPropertyProvider.class.getResource("/test-resources.properties")
        try {
            Method m = TestResourcesClientFactory.class.getDeclaredMethod("configuredAt", URL.class)
            m.setAccessible(true)
            TestResourcesClient testResourcesClient = (TestResourcesClient) m.invoke(null, config)
            testResourcesClient.closeScope(getClass().getSimpleName())
        } catch (Exception e) {
            // Ignore
        }
    }

}

