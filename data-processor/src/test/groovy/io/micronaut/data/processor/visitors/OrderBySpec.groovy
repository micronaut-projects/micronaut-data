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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.tck.entities.Company

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class OrderBySpec extends AbstractDataSpec {

    void "test sort embedded"() {
        given:
            def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Shipment;
import io.micronaut.data.tck.entities.ShipmentDto;
import io.micronaut.data.tck.entities.ShipmentId;
@Repository
interface TestRepository extends GenericRepository<Shipment, ShipmentId> {
    List<Shipment> findAllOrderByShipmentIdCity();
}
''')
        when:
            def queryFindByText = getQuery(repository.getRequiredMethod("findAllOrderByShipmentIdCity"))
        then:
            queryFindByText == 'SELECT shipment_ FROM io.micronaut.data.tck.entities.Shipment AS shipment_ ORDER BY shipment_.shipmentId.city ASC'
    }

    void "test sort embedded JDBC"() {
        given:
            def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Shipment;
import io.micronaut.data.tck.entities.ShipmentDto;
import io.micronaut.data.tck.entities.ShipmentId;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TestRepository extends GenericRepository<Shipment, ShipmentId> {
    List<Shipment> findAllOrderByShipmentIdCity();
}
''')
        when:
            def queryFindByText = getQuery(repository.getRequiredMethod("findAllOrderByShipmentIdCity"))
        then:
            queryFindByText == 'SELECT shipment_."sp_country",shipment_."sp_city",shipment_."field" FROM "Shipment1" shipment_ ORDER BY shipment_."sp_city" ASC'
    }

    void "test order by date created"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Company, Long> {

    Company $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value() == query

        where:
        method                         | arguments     | query
        "findByNameOrderByDateCreated" | "String name" | "SELECT company_ FROM $Company.name AS company_ WHERE (company_.name = :p1) ORDER BY company_.dateCreated ASC"
        "findByNameOrderByDateCreatedAndName" | "String name" | "SELECT company_ FROM $Company.name AS company_ WHERE (company_.name = :p1) ORDER BY company_.dateCreated ASC,company_.name ASC"
        "findByNameSortByDateCreated" | "String name" | "SELECT company_ FROM $Company.name AS company_ WHERE (company_.name = :p1) ORDER BY company_.dateCreated ASC"
        "findByNameSortByDateCreatedAndName" | "String name" | "SELECT company_ FROM $Company.name AS company_ WHERE (company_.name = :p1) ORDER BY company_.dateCreated ASC,company_.name ASC"
    }

    void "test order by date created - sql"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Company, Long> {

    Company $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value().endsWith(query)

        where:
        method                         | arguments     | query
        "findByNameOrderByDateCreated" | "String name" | 'ORDER BY company_."date_created" ASC'
        "findByNameOrderByDateCreatedAndName" | "String name" | 'ORDER BY company_."date_created" ASC,company_."name" ASC'
        "findByNameSortByDateCreated" | "String name" | 'ORDER BY company_."date_created" ASC'
        "findByNameSortByDateCreatedAndName" | "String name" | 'ORDER BY company_."date_created" ASC,company_."name" ASC'
    }
}
