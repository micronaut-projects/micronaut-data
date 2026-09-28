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
package io.micronaut.data.jdbc.config

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.oraclexe.jsonview.AirplaneView
import io.micronaut.data.jdbc.oraclexe.jsonview.ApartmentView
import io.micronaut.data.jdbc.oraclexe.jsonview.BuildingView
import io.micronaut.data.jdbc.oraclexe.jsonview.CarView
import io.micronaut.data.jdbc.oraclexe.jsonview.CrocodileView
import io.micronaut.data.jdbc.oraclexe.jsonview.PersonView
import io.micronaut.data.jdbc.oraclexe.jsonview.StudentView
import io.micronaut.data.jdbc.oraclexe.jsonview.TeacherView
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationResult
import io.micronaut.data.model.query.builder.sql.validation.SqlJsonViewValidator
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Validates the JSON relational duality views created by the schema generation of the {@code oracle-jsonview} environment
 * against the metadata read from the Oracle dictionary: the view table trees must match without any difference.
 */
@MicronautTest(environments = ["oracle-jsonview"])
class OracleJsonViewMetadataSpec extends Specification {

    @Inject
    DataSource dataSource

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    void 'created JSON view #viewType.simpleName matches its mapping without warnings'() {
        given:
        def mapping = SqlSchemaUtils.getSqlJsonViewMapping(runtimeEntityRegistry.getEntity(viewType))
        def result = new SchemaValidationResult()

        when:
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            def views = new JdbcSchemaMetadataReader(connection, Dialect.ORACLE).readJsonDualityViews(null)
            SqlJsonViewValidator.validate(mapping, views[mapping.name().toLowerCase()], result)
        }

        then:
        result.errors.isEmpty()
        result.warnings.isEmpty()

        where:
        viewType << [StudentView, TeacherView, CarView, BuildingView, CrocodileView, PersonView, AirplaneView, ApartmentView]
    }

    void 'the sub view tree of the view entity mirrors the created view'() {
        when:
        def root = SqlSchemaUtils.getSqlJsonViewMapping(runtimeEntityRegistry.getEntity(BuildingView)).root()

        then:"An embedded id many-to-many is an array nested in the unnested join table"
        root.children().size() == 1
        root.children()[0].key() == null
        !root.children()[0].nested()
        root.children()[0].children()[0].key() == 'apartments'
        root.children()[0].children()[0].nested()
    }
}
