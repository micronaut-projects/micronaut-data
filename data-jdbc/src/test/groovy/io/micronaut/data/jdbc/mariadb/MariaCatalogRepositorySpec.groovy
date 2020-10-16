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
package io.micronaut.data.jdbc.mariadb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.mysql.MySqlCatalogRepository
import io.micronaut.data.tck.jdbc.entities.Catalog
import io.micronaut.test.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MariaCatalogRepositorySpec extends Specification implements MariaTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void "test save and retrieve catalog"() {
        when:"we save a new catalog with parent"
        def catalogRepository = context.getBean(MySqlCatalogRepository)
        def parentCatalog = new Catalog()
        parentCatalog.setName("Catalog")
        parentCatalog = catalogRepository.save(parentCatalog)

        def catalog = new Catalog()
        catalog.setName("Catalog with parent")
        catalog.setParent(parentCatalog)
        catalog = catalogRepository.save(catalog)

        then: "The ID of both catalogs is assigned"
        parentCatalog.getId() != null
        catalog.getId() != null

        when:"we retrieve catalog with parent"
        def retrievedCatalog = catalogRepository.findById(catalog.getId()).orElse(null)

        then: "The IDs of both catalogs are assigned"
        retrievedCatalog.getId() == catalog.getId()
        retrievedCatalog.getName() == catalog.getName()
        retrievedCatalog.getParent().getId() == parentCatalog.getId()

        when:"we delete catalog with parent"
        catalogRepository.delete(retrievedCatalog)
        retrievedCatalog = catalogRepository.findById(catalog.getId())

        then: "The catalog is deleted"
        !retrievedCatalog.isPresent()
    }
}

