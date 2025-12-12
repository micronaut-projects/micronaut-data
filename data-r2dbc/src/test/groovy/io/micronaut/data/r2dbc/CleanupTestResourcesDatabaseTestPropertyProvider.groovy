package io.micronaut.data.r2dbc

import io.micronaut.test.extensions.junit5.annotation.ScopeNamingStrategy
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope

@TestResourcesScope(namingStrategy = ScopeNamingStrategy.TestClassName)
trait CleanupTestResourcesDatabaseTestPropertyProvider {

}
