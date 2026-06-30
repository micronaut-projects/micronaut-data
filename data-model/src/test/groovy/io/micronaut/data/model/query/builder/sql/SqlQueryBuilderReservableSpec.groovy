package io.micronaut.data.model.query.builder.sql

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.model.DataType
import io.micronaut.data.model.schema.sql.SqlColumnMapping
import io.micronaut.data.model.schema.sql.SqlColumnMapping.ReservableOptions
import io.micronaut.data.model.schema.sql.SqlColumnMapping.SqlCheckConstraint
import io.micronaut.data.model.schema.sql.SqlDbType
import spock.lang.Specification

class SqlQueryBuilderReservableSpec extends Specification {

    def "reservable column rendering is rejected for non Oracle dialects"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.H2)
        def column = reservableColumn()

        when:
        appendReservableAndCheckConstraints(builder, "balance INTEGER", column, false)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Reservable columns are only supported for Oracle"
    }

    def "reservable column rendering is supported for Oracle dialect"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.ORACLE)
        def column = reservableColumn()

        expect:
        appendReservableAndCheckConstraints(builder, '"BALANCE" NUMBER(19)', column, true) ==
                '"BALANCE" NUMBER(19) RESERVABLE CONSTRAINT "CHK_BALANCE_POSITIVE" CHECK ("BALANCE" >= 0)'
    }

    private static SqlColumnMapping reservableColumn() {
        new SqlColumnMapping(
                "BALANCE",
                DataType.LONG,
                SqlDbType.BIGINT,
                null,
                null,
                null,
                false,
                false,
                GeneratedValue.Type.AUTO,
                null,
                null,
                new ReservableOptions(true, [new SqlCheckConstraint("CHK_BALANCE_POSITIVE", ">=", "0")])
        )
    }

    private static String appendReservableAndCheckConstraints(SqlQueryBuilder builder,
                                                              String column,
                                                              SqlColumnMapping columnMapping,
                                                              boolean escape) {
        def method = SqlQueryBuilder.getDeclaredMethod("appendReservableAndCheckConstraints", String, SqlColumnMapping, Boolean.TYPE)
        method.accessible = true
        try {
            return method.invoke(builder, column, columnMapping, escape)
        } catch (ReflectiveOperationException e) {
            throw e.cause
        }
    }
}
