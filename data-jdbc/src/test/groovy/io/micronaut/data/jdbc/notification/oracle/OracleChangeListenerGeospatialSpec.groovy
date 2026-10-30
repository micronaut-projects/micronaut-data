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
package io.micronaut.data.jdbc.notification.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.runtime.convert.GeometryWktConverter
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import org.jspecify.annotations.Nullable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@TestResourcesScope("oracle-notification-geospatial-reload")
class OracleChangeListenerGeospatialSpec extends Specification implements OracleTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    JdbcRepositoryOperations operations

    @Shared
    boolean tableCreated

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        [
            (prefix + '.db-type'): dbType(),
            (prefix + '.schema-generate'): 'NONE',
            (prefix + '.dialect'): dialect().name(),
            (prefix + '.enabled'): 'true',
            'test-resources.containers.oracle.image-name': 'gvenzl/oracle-free',
            'test-resources.containers.oracle.image-tag': 'latest'
        ]
    }

    void setupSpec() {
        context = ApplicationContext.run(properties)
        operations = context.getBean(DefaultJdbcRepositoryOperations)
        operations.execute { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute('''
                    CREATE TABLE NOTIFICATION_GEO_RELOAD (
                        ID NUMBER(19) PRIMARY KEY,
                        JSON_LOCATION MDSYS.SDO_GEOMETRY,
                        WKT_LOCATION MDSYS.SDO_GEOMETRY
                    )
                ''')
                tableCreated = true
                statement.execute('''
                    INSERT INTO NOTIFICATION_GEO_RELOAD (ID, JSON_LOCATION, WKT_LOCATION)
                    VALUES (
                        1,
                        MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(12.5, 43.75, NULL), NULL, NULL),
                        MDSYS.SDO_GEOMETRY(2001, 4326, MDSYS.SDO_POINT_TYPE(-73.5, 40.25, NULL), NULL, NULL)
                    )
                ''')
                statement.execute('INSERT INTO NOTIFICATION_GEO_RELOAD (ID) VALUES (2)')
            }
            true
        }
    }

    void cleanupSpec() {
        if (tableCreated) {
            operations.execute { connection ->
                connection.createStatement().withCloseable { statement ->
                    statement.execute('DROP TABLE NOTIFICATION_GEO_RELOAD PURGE')
                }
                true
            }
        }
    }

    void "runtime ROWID fallback loads GeoJSON and WKT geometry for row #id"() {
        given:
        def method = Stub(ExecutableMethod) {
            getAnnotation(OracleChangeNotification) >> AnnotationValue.builder(OracleChangeNotification).build()
            stringValue(OracleChangeListenerQuery) >> Optional.empty()
        }
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(SpatialEntity))
        def definition = new OracleChangeListenerDefinitionFactory(operations).create(listener)
        String rowId = rowId(id)

        when:
        SpatialEntity loaded = definition.entityLoader().reload(rowId)

        then:
        loaded != null
        loaded.id == id
        loaded.jsonLocation == expectedJsonLocation
        loaded.wktLocation == expectedWktLocation

        where:
        id | expectedJsonLocation    | expectedWktLocation
        1L | new Point(12.5, 43.75)  | new Point(-73.5, 40.25)
        2L | null                    | null
    }

    private String rowId(long id) {
        operations.execute { connection ->
            connection.prepareStatement('SELECT ROWIDTOCHAR(ROWID) FROM NOTIFICATION_GEO_RELOAD WHERE ID = ?')
                .withCloseable { statement ->
                    statement.setLong(1, id)
                    statement.executeQuery().withCloseable { rows ->
                        assert rows.next()
                        rows.getString(1)
                    }
                }
        }
    }

    @MappedEntity(value = "notification_geo_reload", alias = "geo_")
    static class SpatialEntity {
        @Id
        Long id

        @Nullable
        @MappedProperty("json_location")
        Point jsonLocation

        @Nullable
        @MappedProperty(value = "wkt_location", converter = GeometryWktConverter)
        Point wktLocation
    }
}

