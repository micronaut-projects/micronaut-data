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

    void "test a sharded table gets IF NOT EXISTS on Oracle 23ai"() {
        given:
        def entity = buildEntity('test.Customer', '''
import io.micronaut.data.annotation.Sharded;

@Sharded(tablespaceSet = "TS_CUSTOMERS")
@MappedEntity
record Customer(@Id String id, String name) {}
''')

        when:
        def sql = new SqlQueryBuilder(Dialect.ORACLE, "23.26").buildCreateTableStatements(entity)

        then:
        sql == ['CREATE SHARDED TABLE IF NOT EXISTS "CUSTOMER" ("ID" VARCHAR(255) NOT NULL,"NAME" VARCHAR(255) NOT NULL, PRIMARY KEY("ID")) PARTITION BY CONSISTENT HASH ("ID") PARTITIONS AUTO TABLESPACE SET TS_CUSTOMERS'] as String[]
    }

    void "test the sharding key of a composite primary key"() {
        given:
        def entity = buildEntity('test.Order', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.Sharded;

@Sharded(key = "id.customerId", tablespaceSet = "TS_ORDERS")
@MappedEntity(value = "orders", escape = false)
record Order(@EmbeddedId OrderKey id, String status) {}

@Embeddable
record OrderKey(String customerId, Long orderNo) {}
''')

        when:
        def sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        sql == ['CREATE SHARDED TABLE orders (customer_id VARCHAR(255) NOT NULL,order_no NUMBER(19) NOT NULL,status VARCHAR(255) NOT NULL, PRIMARY KEY(customer_id,order_no)) PARTITION BY CONSISTENT HASH (customer_id) PARTITIONS AUTO TABLESPACE SET TS_ORDERS'] as String[]
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

    void "test indexes of a sharded table are local"() {
        given:
        def entity = buildEntity('test.Customer', """
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Sharded;

@Sharded(tablespaceSet = "TS_CUSTOMERS")
@MappedEntity(escape = false)
@Indexes({@Index(columns = "name"), @Index(columns = {"id", "email"}, unique = true)})
record Customer(@Id String id, String name, String email) {}
""")

        when:
        def sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        sql[1] == 'CREATE INDEX idx_customer_name ON customer (name) LOCAL'
        sql[2] == 'CREATE UNIQUE INDEX idx_customer_id_email ON customer (id, email) LOCAL'
    }

    void "test indexes of a duplicated table are not local"() {
        given:
        def entity = buildEntity('test.Product', """
import io.micronaut.data.annotation.Duplicated;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;

@Duplicated
@MappedEntity(escape = false)
@Indexes(@Index(columns = "name"))
record Product(@Id Long id, String name) {}
""")

        expect:
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)[1] == 'CREATE INDEX idx_product_name ON product (name)'
    }

    void "test a unique index of a sharded table must contain the sharding key"() {
        given:
        def entity = buildEntity('test.Customer', """
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Sharded;

@Sharded(tablespaceSet = "TS_CUSTOMERS")
@MappedEntity
@Indexes(@Index(columns = "email", unique = true))
record Customer(@Id String id, String email) {}
""")

        when:
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        def e = thrown(MappingException)
        e.message == 'The unique index on [email] of sharded entity [test.Customer] must contain the sharding key columns [id]'
    }

    void "test a duplicated table can have an identity column"() {
        given:
        def entity = buildEntity('test.Product', """
import io.micronaut.data.annotation.Duplicated;
import io.micronaut.data.annotation.GeneratedValue;

@Duplicated
@MappedEntity(escape = false)
record Product(@Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id, String name) {}
""")

        expect:
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)[0].contains('GENERATED BY DEFAULT ON NULL AS IDENTITY')
    }

    void "test a generated column that is not the sharding key uses a sharded sequence"() {
        given:
        def entity = buildEntity('test.Order', """
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Sharded;

@Sharded(key = "id.customerId", tablespaceSet = "TS_ORDERS")
@MappedEntity(value = "orders", escape = false)
record Order(@EmbeddedId OrderKey id) {}

@Embeddable
record OrderKey(String customerId, @GeneratedValue(GeneratedValue.Type.SEQUENCE) ${type} orderNo) {}
""")

        when:
        def sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        sql.any { it.startsWith('CREATE SHARDED TABLE orders') }
        sql.contains(sequence)

        where:
        type         | sequence
        'Long'       | 'CREATE SEQUENCE orders_seq MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE MAXVALUE 999999999999999999 SHARD'
        'Integer'    | 'CREATE SEQUENCE orders_seq MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE MAXVALUE 999999999 SHARD'
    }

    void "test the sequence of a duplicated table is not sharded"() {
        given:
        def entity = buildEntity('test.Product', """
import io.micronaut.data.annotation.Duplicated;
import io.micronaut.data.annotation.GeneratedValue;

@Duplicated
@MappedEntity(escape = false)
record Product(@Id @GeneratedValue(GeneratedValue.Type.SEQUENCE) Long id, String name) {}
""")

        expect:
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity).contains('CREATE SEQUENCE product_seq MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE')
    }

    void "test invalid sharding annotations fail compilation"() {
        when:
        buildEntity('test.Order', """
import io.micronaut.data.annotation.Duplicated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Sharded;
import java.util.List;

@MappedEntity
record Plain(@Id String id) {}

@Sharded(tablespaceSet = "TS")
@MappedEntity
record Root(@Id String rootId) {}

@Sharded(parent = Root.class, tablespaceSet = "TS", key = "id")
@MappedEntity
record Child(@Id String id) {}

${annotations}
@MappedEntity
record Order(${members}) {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        annotations                                              | members                                                                    | message
        '@Sharded(tablespaceSet = "TS") @Duplicated'             | '@Id String id'                                                            | 'Entity [test.Order] cannot be both @Sharded and @Duplicated'
        '@Sharded(tablespaceSet = "")'                           | '@Id String id'                                                            | 'Sharded entity [test.Order] must name a tablespace set'
        '@Sharded(key = "customer", tablespaceSet = "TS")'       | '@Id String id'                                                            | 'The sharding key property [customer] does not exist in entity [test.Order]'
        '@Sharded(key = "customerId", tablespaceSet = "TS")'     | '@Id Long id, String customerId'                                           | 'The sharding key column [customer_id] of entity [test.Order] must be part of its primary key'
        '@Sharded(parent = Plain.class, tablespaceSet = "TS")'   | '@Id String id'                                                            | 'The parent [test.Plain] of sharded entity [test.Order] must be @Sharded'
        '@Sharded(parent = Child.class, tablespaceSet = "TS")'   | '@Id String id'                                                            | 'The parent [test.Child] of sharded entity [test.Order] must be the root table of the table family, without a parent'
        '@Sharded(parent = Root.class, tablespaceSet = "TS")'    | '@Id String id'                                                            | 'The sharding key column [root_id] of entity [test.Order] must be part of its primary key'
        '@Sharded(tablespaceSet = "TS")'                         | '@Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id'                | 'The property [id] of sharded entity [test.Order] cannot use an identity column'
        '@Sharded(tablespaceSet = "TS")'                         | '@Id @GeneratedValue Long id'                                              | 'The sharding key column [id] of entity [test.Order] cannot be generated by the database'
        '@Sharded(tablespaceSet = "TS")'                         | '@Id String id, @Relation(Relation.Kind.MANY_TO_MANY) List<Plain> plains'  | 'The association [plains] of entity [test.Order] needs a join table'
        '@Duplicated'                                            | '@Id String id, @Relation(Relation.Kind.MANY_TO_MANY) List<Plain> plains'  | 'The association [plains] of entity [test.Order] needs a join table'
    }
}
