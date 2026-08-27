package io.micronaut.data.runtime.mapper.sql

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.mapper.ResultReader
import spock.lang.Specification

class SqlResultEntityTypeMapperInstantiationSpec extends Specification {

    void "InstantiationException during materialization is wrapped for an unmappable creator"() {
        given:
        def entity = new RuntimePersistentEntity(CountryWithoutNoArg)
        def resultReader = Stub(ResultReader) {
            getConversionService() >> ConversionService.SHARED
        }
        def mapper = new SqlResultEntityTypeMapper<Map, CountryWithoutNoArg>(
            null, entity, resultReader, null, Stub(DataConversionService))

        expect: "metadata accepts the type; instantiate() is deferred"
        entity.constructorArguments.length == 0

        when:
        mapper.map([:], CountryWithoutNoArg)

        then:
        DataAccessException e = thrown()
        e.cause instanceof InstantiationException
        e.message.contains('CountryWithoutNoArg')
        e.message.contains('no no-argument constructor is available')
    }
}
