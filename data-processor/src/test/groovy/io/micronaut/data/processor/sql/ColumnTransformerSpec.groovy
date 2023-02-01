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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.DataTransformer
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.jdbc.entities.Project
import io.micronaut.data.tck.jdbc.entities.Transform
import spock.lang.Requires

class ColumnTransformerSpec extends AbstractDataSpec {

    @Requires({ javaVersion <= 1.8 })
    void "test mapping"() {
        given:
        def entity = buildJpaEntity('test.Project', '''
import java.time.LocalDateTime;
import io.micronaut.data.jdbc.annotation.ColumnTransformer;

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
        def entity = PersistentEntity.of(Project)
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity).query

        expect:
        sql == 'INSERT INTO "project" ("name","db_name","org","project_id_department_id","project_id_project_id") VALUES (UPPER(?),?,?,?,?)'

    }

    void "test build update with column writer"() {
        given:
        def entity = PersistentEntity.of(Project)
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildUpdate(QueryModel.from(entity), Collections.singletonList("name")).query

        expect:
        sql == 'UPDATE "project" SET "name"=UPPER(?)'
    }

    void "test build query with column reader"() {
        given:
        def entity = PersistentEntity.of(Project)
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildQuery(QueryModel.from(entity)).query

        expect:
        sql == 'SELECT project_."project_id_department_id",project_."project_id_project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_'
    }
    void "test build query with column reader in where"() {
        given:
        def entity = PersistentEntity.of(Project)
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildQuery(QueryModel.from(entity).eq("name", new QueryParameter("xyz"))).query

        expect:
        sql == 'SELECT project_."project_id_department_id",project_."project_id_project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_ WHERE (project_."name" = UPPER(?))'
    }

    void "test update query with column readers and writers"() {
        given:
        def entity = PersistentEntity.of(Project)
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildUpdate(
                QueryModel.from(entity)
                    .eq("name", new QueryParameter("abc"))
                    .eq("org", new QueryParameter("xyz")),
                Arrays.asList("name", "org")
        ).query

        expect:
        sql == 'UPDATE "project" SET "name"=UPPER(?),"org"=? WHERE ("name" = UPPER(?) AND "org" = ?)'
    }


    void "test build insert with column writer2"() {
        given:
            def entity = PersistentEntity.of(Transform)
            SqlQueryBuilder builder = new SqlQueryBuilder()
            def sql = builder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity).query

        expect:
            sql == 'INSERT INTO "transform" ("xyz","project_id_department_id","project_id_project_id") VALUES (LOWER(?),?,?)'

    }

    void "test build update with column writer2"() {
        given:
            def entity = PersistentEntity.of(Transform)
            SqlQueryBuilder builder = new SqlQueryBuilder()
            def sql = builder.buildUpdate(QueryModel.from(entity), Collections.singletonList("xyz")).query

        expect:
            sql == 'UPDATE "transform" SET "xyz"=LOWER(?)'
    }

    void "test build query with column reader2"() {
        given:
            def entity = PersistentEntity.of(Transform)
            SqlQueryBuilder builder = new SqlQueryBuilder()
            def sql = builder.buildQuery(QueryModel.from(entity)).query

        expect:
            sql == 'SELECT transform_."project_id_department_id",transform_."project_id_project_id",UPPER(xyz@abc) AS xyz FROM "transform" transform_'
    }

    void "test build query with column reader in where2"() {
        given:
            def entity = PersistentEntity.of(Transform)
            SqlQueryBuilder builder = new SqlQueryBuilder()
            def sql = builder.buildQuery(QueryModel.from(entity).eq("xyz", new QueryParameter("xyz"))).query

        expect:
            sql == 'SELECT transform_."project_id_department_id",transform_."project_id_project_id",UPPER(xyz@abc) AS xyz FROM "transform" transform_ WHERE (transform_."xyz" = LOWER(?))'
    }
}
