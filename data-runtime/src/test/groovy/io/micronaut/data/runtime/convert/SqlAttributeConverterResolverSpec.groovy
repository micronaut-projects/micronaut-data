package io.micronaut.data.runtime.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter
import io.micronaut.data.model.runtime.convert.GeometryWktConverter
import spock.lang.AutoCleanup
import spock.lang.Specification

class SqlAttributeConverterResolverSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "selects the spatial representation for #databaseType without changing the configured converter"() {
        given:
        def configured = context.getBean(GeometryJsonConverter)
        def point = new Point(1d, 2.5d)
        def json = '{"type":"Point","coordinates":[1.0,2.5]}'

        when:
        def resolved = SqlAttributeConverterResolver.resolve(configured, databaseType)

        then:
        resolved.convertToPersistedValue(point, ConversionContext.DEFAULT) == expectedValue
        resolved.convertToEntityValue(expectedValue, ConversionContext.DEFAULT) == point
        resolved.convertToPersistedValue(null, ConversionContext.DEFAULT) == null
        resolved.convertToEntityValue(null, ConversionContext.DEFAULT) == null
        resolved.convertToEntityValue('', ConversionContext.DEFAULT) == null

        and: "selection for another database still uses the original converter"
        SqlAttributeConverterResolver.resolve(configured, DatabaseType.POSTGRES).is(configured)
        configured.convertToPersistedValue(point, ConversionContext.DEFAULT) == json

        where:
        databaseType << DatabaseType.values()
        expectedValue = databaseType == DatabaseType.SQL_SERVER ? 'POINT (1 2.5)' : '{"type":"Point","coordinates":[1.0,2.5]}'
    }

    void "preserves explicit WKT and custom converters for #databaseType"() {
        given:
        def wkt = new GeometryWktConverter()
        def custom = Stub(AttributeConverter)

        expect:
        SqlAttributeConverterResolver.resolve(wkt, databaseType).is(wkt)
        SqlAttributeConverterResolver.resolve(custom, databaseType).is(custom)

        where:
        databaseType << DatabaseType.values()
    }
}
