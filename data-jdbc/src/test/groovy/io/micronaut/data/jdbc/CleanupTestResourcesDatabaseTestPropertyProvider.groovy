package io.micronaut.data.jdbc

import io.micronaut.test.extensions.junit5.annotation.ScopeNamingStrategy
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope

@TestResourcesScope(namingStrategy = ScopeNamingStrategy.TestClassName)
interface CleanupTestResourcesDatabaseTestPropertyProvider {

}
