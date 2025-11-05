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
package io.micronaut.data.processor.sql


import io.micronaut.data.annotation.DataTransformer
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.jdbc.entities.Project
import io.micronaut.data.tck.jdbc.entities.Transform
import spock.lang.Shared

class ColumnTransformerSpec extends AbstractDataSpec {

    @Shared
    def builder = new RuntimeCriteriaBuilder()
    @Shared
    def queryBuilder = new SqlQueryBuilder()

    void "test mapping"() {
        given:
        def entity = buildJpaEntity('test.Project', '''
import io.micronaut.data.annotation.sql.ColumnTransformer;

@Entity
class Project {
    @ColumnTransformer(
            read = "UPPER(org)"
    )
    private String name;

    public Project(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
''')

        expect:
        entity.getPropertyByName("name")
                .annotationMetadata
                .stringValue(DataTransformer, "read")
                .get() == 'UPPER(org)'
    }

    void "test build insert with column writer"() {
        given:
        def builder = new RuntimeCriteriaBuilder()
        def sql = builder.createCriteriaInsert(Project).build(new SqlQueryBuilder()).query

        expect:
        sql == 'INSERT INTO "project" ("name","db_name","org","department_id","project_id") VALUES (UPPER(?),?,?,?,?)'
    }

    void "test build update with column writer"() {
        given:
        def query = builder.createCriteriaUpdate(Project)
                .set("name", builder.parameter(String))
        def sql = query.build(queryBuilder).query

        expect:
        sql == 'UPDATE "project" SET "name"=UPPER(?)'
    }

    void "test build query with column reader"() {
        given:
        def query = builder.createQuery(Project)
        def sql = query.build(queryBuilder).query

        expect:
        sql == 'SELECT project_."department_id",project_."project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_'
    }
    void "test build query with column reader in where"() {
        given:
        def query = builder.createQuery(Project)
        def root = query.from(Project)
        def sql = query.where(builder.equal(root.get("name"), builder.parameter(String))).build(queryBuilder).query

        expect:
        sql == 'SELECT project_."department_id",project_."project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_ WHERE (project_."name" = UPPER(?))'
    }

    void "test update query with column readers and writers"() {
        given:
        def query = builder.createCriteriaUpdate(Project)
        def root = query.from(Project)
        def sql = query
                .set(root.get("name"), builder.parameter(String))
                .set(root.get("org"), builder.parameter(String))
                .where(
                        builder.equal(root.get("name"), builder.parameter(String)),
                        builder.equal(root.get("org"), builder.parameter(String))
                ).build(queryBuilder).query

        expect:
        sql == 'UPDATE "project" SET "name"=UPPER(?),"org"=? WHERE ("name" = UPPER(?) AND "org" = ?)'
    }

    void "test build insert with column writer2"() {
        given:
            def query = builder.createCriteriaInsert(Transform)
            def sql = query.build(new SqlQueryBuilder()).query

        expect:
            sql == 'INSERT INTO "transform" ("xyz","department_id","project_id") VALUES (LOWER(?),?,?)'
    }

    void "test build update with column writer2"() {
        given:
            def query = builder.createCriteriaUpdate(Transform)
                    .set("xyz", builder.parameter(String))
            def sql = query.build(queryBuilder).query
        expect:
            sql == 'UPDATE "transform" SET "xyz"=LOWER(?)'
    }

    void "test build query with column reader2"() {
        given:
            def query = builder.createQuery(Transform)
            def sql = query.build(new SqlQueryBuilder()).query

        expect:
            sql == 'SELECT transform_."department_id",transform_."project_id",UPPER(xyz@abc) AS xyz FROM "transform" transform_'
    }

    void "test build query with column reader in where2"() {
        given:
            def query = builder.createQuery(Transform)
            def root = query.from(Transform)
            def sql = query.where(builder.equal(root.get("xyz"), builder.parameter(String))).build(queryBuilder).query

        expect:
            sql == 'SELECT transform_."department_id",transform_."project_id",UPPER(xyz@abc) AS xyz FROM "transform" transform_ WHERE (transform_."xyz" = LOWER(?))'
    }
}
