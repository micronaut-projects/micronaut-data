/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.hibernate.connection

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.data.tck.repositories.SimpleBookRepository
import io.micronaut.data.tck.tests.AbstractConnectableSpec

class HibernateConnectableSpec extends AbstractConnectableSpec {

    @Override
    String[] getPackages() {
        return ["io.micronaut.data.tck.entities"]
    }

    @Override
    Class<? extends SimpleBookRepository> getBookRepositoryClass() {
        return HibernateBookRepositoryImpl.class
    }

    @Override
    protected ConnectionOperations<?> getConnectionOperations() {
        return context.getBean(HibernateConnectionOperations)
    }

    @Override
    protected SynchronousConnectionManager<?> getSynchronousConnectionManager() {
        return context.getBean(HibernateConnectionOperations)
    }

    @Override
    Map<String, String> getProperties() {
        return [
                "datasources.default.name"                     : "mydb",
                "datasources.default.db-type"                  : "postgresql",
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'create-drop'
        ]
    }
}
