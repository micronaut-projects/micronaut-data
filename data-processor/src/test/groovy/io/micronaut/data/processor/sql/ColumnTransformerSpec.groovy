/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.jdbc.entities.Project
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
        sql == 'INSERT INTO "project" ("name","org","project_id_department_id","project_id_project_id") VALUES (UPPER(?),?,?,?)'

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
        sql == 'SELECT project_."project_id_department_id",project_."project_id_project_id",project_."name",UPPER(org) AS org FROM "project" project_'
    }
}
