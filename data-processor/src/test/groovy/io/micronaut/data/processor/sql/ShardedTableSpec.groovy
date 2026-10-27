/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.processor.sql

import io.micronaut.data.exceptions.MappingException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec

class ShardedTableSpec extends AbstractDataSpec {

    void "test a sharded table is partitioned by consistent hash of its primary key"() {
        given:
        def entity = buildEntity('test.Customer', '''
import io.micronaut.data.annotation.Sharded;

@Sharded(tablespaceSet = "TS_CUSTOMERS")
@MappedEntity
record Customer(@Id String id, String name) {}
''')

        when:
        def sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        sql == ['CREATE SHARDED TABLE "CUSTOMER" ("ID" VARCHAR(255) NOT NULL,"NAME" VARCHAR(255) NOT NULL, PRIMARY KEY("ID")) PARTITION BY CONSISTENT HASH ("ID") PARTITIONS AUTO TABLESPACE SET TS_CUSTOMERS'] as String[]
    }

    void "test the sharding key of a composite primary key"() {
        given:
        def entity = buildEntity('test.Order', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.Sharded;

@Sharded(key = "id.customerId")
@MappedEntity(value = "orders", escape = false)
record Order(@EmbeddedId OrderKey id, String status) {}

@Embeddable
record OrderKey(String customerId, Long orderNo) {}
''')

        when:
        def sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        sql == ['CREATE SHARDED TABLE orders (customer_id VARCHAR(255) NOT NULL,order_no NUMBER(19) NOT NULL,status VARCHAR(255) NOT NULL, PRIMARY KEY(customer_id,order_no)) PARTITION BY CONSISTENT HASH (customer_id) PARTITIONS AUTO'] as String[]
    }

    void "test a duplicated table"() {
        given:
        def entity = buildEntity('test.Product', """
import io.micronaut.data.annotation.Duplicated;

@Duplicated${tablespace}
@MappedEntity(escape = false)
record Product(@Id Long id, String name) {}
""")

        expect:
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity) == [sql] as String[]

        where:
        tablespace                       | sql
        ''                               | 'CREATE DUPLICATED TABLE product (id NUMBER(19) NOT NULL,name VARCHAR(255) NOT NULL, PRIMARY KEY(id))'
        '(tablespace = "PRODUCTS_TSP")' | 'CREATE DUPLICATED TABLE product (id NUMBER(19) NOT NULL,name VARCHAR(255) NOT NULL, PRIMARY KEY(id)) TABLESPACE PRODUCTS_TSP'
    }

    void "test other dialects generate plain tables"() {
        given:
        def entity = buildEntity('test.Customer', '''
import io.micronaut.data.annotation.Sharded;

@Sharded(tablespaceSet = "TS_CUSTOMERS")
@MappedEntity(escape = false)
record Customer(@Id String id, String name) {}
''')

        expect:
        new SqlQueryBuilder(Dialect.H2).buildCreateTableStatements(entity) == ['CREATE TABLE customer (id VARCHAR(255) NOT NULL,name VARCHAR(255) NOT NULL, PRIMARY KEY(id));'] as String[]
    }

    void "test the sharding key must be part of the primary key"() {
        given:
        def entity = buildEntity('test.Order', '''
import io.micronaut.data.annotation.Sharded;

@Sharded(key = "customerId")
@MappedEntity
record Order(@Id Long id, String customerId) {}
''')

        when:
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        def e = thrown(MappingException)
        e.message == 'The sharding key column [customer_id] of entity [test.Order] must be part of its primary key'
    }

    void "test invalid sharding annotations fail compilation"() {
        when:
        buildEntity('test.Order', """
import io.micronaut.data.annotation.Duplicated;
import io.micronaut.data.annotation.Sharded;

@MappedEntity
record Customer(@Id String id) {}

${annotations}
@MappedEntity
record Order(@Id String id, String customerId) {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        annotations                        | message
        '@Sharded @Duplicated'             | 'Entity Order cannot be both @Sharded and @Duplicated'
        '@Sharded(key = "customer")'       | 'The sharding key property customer doesn\'t exist in the entity Order'
        '@Sharded(parent = Customer.class)' | 'The parent Customer of the sharded entity Order must be @Sharded'
    }
}
