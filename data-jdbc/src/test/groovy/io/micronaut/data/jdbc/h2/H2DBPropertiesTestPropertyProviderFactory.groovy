package io.micronaut.data.jdbc.h2

import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.test.support.TestPropertyProviderFactory

class H2DBPropertiesTestPropertyProviderFactory implements TestPropertyProviderFactory {

    @Override
    TestPropertyProvider create(Map<String, Object> availableProperties, Class<?> testClass) {
        H2DBProperties h2DbProperties = testClass.getAnnotation(H2DBProperties)
        if (h2DbProperties == null) {
            return Collections::emptyMap
        }
        return () -> [
                'datasources.default.name'           : h2DbProperties.name(),
                'datasources.default.packages'       : h2DbProperties.packages(),
                'datasources.default.schema-generate': h2DbProperties.schemaGenerate(),
                'datasources.default.dialect'        : h2DbProperties.dialect(),
                'datasources.default.driverClassName': h2DbProperties.driverClassName(),
                'datasources.default.url'            : h2DbProperties.url(),
                'datasources.default.username'       : h2DbProperties.username(),
                'datasources.default.password'       : h2DbProperties.password(),
                'micronaut.data.save-assigned-id-fallback-to-update': 'true'
        ] as Map<String, String>
    }
}
