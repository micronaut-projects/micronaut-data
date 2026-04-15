package io.micronaut.data.jdbc.mapper

import spock.lang.Specification

import java.sql.CallableStatement

class CallableStatementTupleMapperSpec extends Specification {

    void "maps all registered out parameters into tuple order"() {
        given:
        CallableStatement callableStatement = Mock()
        def mapper = new CallableStatementTupleMapper(
                io.micronaut.core.convert.ConversionService.SHARED,
                new LinkedHashMap<>([
                        title      : 4,
                        total_pages: 5,
                ])
        )

        when:
        def tuple = mapper.map(callableStatement, jakarta.persistence.Tuple)

        then:
        tuple.toArray() == ["Oracle DTO Title", 777] as Object[]
        tuple.get("title", String) == "Oracle DTO Title"
        tuple.get("total_pages", Integer) == 777

        1 * callableStatement.getObject(4) >> "Oracle DTO Title"
        1 * callableStatement.getObject(5) >> 777
    }
}
