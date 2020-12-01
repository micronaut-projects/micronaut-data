/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(packages = "io.micronaut.data.tck.entities", rollback = false, transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'datasources.default.url', value = 'jdbc:tc:postgresql://localhost/testing')
@Property(name = 'datasources.default.driverClassName', value = 'org.testcontainers.jdbc.ContainerDatabaseDriver')
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = 'jpa.default.properties.hibernate.dialect', value = 'org.hibernate.dialect.PostgreSQL95Dialect')
class HibernatePostgresQuerySpec extends AbstractHibernateQuerySpec {
}