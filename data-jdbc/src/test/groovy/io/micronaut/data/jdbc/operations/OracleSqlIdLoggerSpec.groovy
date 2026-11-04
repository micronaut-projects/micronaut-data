package io.micronaut.data.jdbc.operations

import oracle.jdbc.OracleStatement
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger

class OracleSqlIdLoggerSpec extends Specification {

    boolean supported

    def setup() {
        supported = OracleSqlIdLogger.sqlIdSupported
    }

    def cleanup() {
        // shared with the other specs of the JVM
        OracleSqlIdLogger.sqlIdSupported = supported
    }

    void "the SQL ID of an Oracle statement is read"() {
        expect:
        OracleSqlIdLogger.sqlId(statement({ '6973n8gn5dwvt' }, new AtomicInteger())) == '6973n8gn5dwvt'
    }

    void "a driver older than 23 without getSqlId is ignored and not tried again"() {
        given:
        AtomicInteger calls = new AtomicInteger()
        Statement statement = statement({ throw new NoSuchMethodError("'java.lang.String oracle.jdbc.OracleStatement.getSqlId()'") }, calls)

        when:
        String first = OracleSqlIdLogger.sqlId(statement)
        String second = OracleSqlIdLogger.sqlId(statement)

        then: "no error reaches the statement preparation"
        first == null
        second == null
        calls.get() == 1
        !OracleSqlIdLogger.sqlIdSupported
    }

    void "a statement of another driver has no SQL ID"() {
        given:
        Statement statement = (Statement) Proxy.newProxyInstance(getClass().classLoader, [Statement] as Class[], { proxy, Method method, Object[] args ->
            method.name == 'isWrapperFor' ? false : null
        } as InvocationHandler)

        expect:
        OracleSqlIdLogger.sqlId(statement) == null
        OracleSqlIdLogger.sqlIdSupported
    }

    private Statement statement(Closure<String> sqlId, AtomicInteger calls) {
        OracleStatement oracle = (OracleStatement) Proxy.newProxyInstance(getClass().classLoader, [OracleStatement] as Class[], { proxy, Method method, Object[] args ->
            if (method.name == 'getSqlId') {
                calls.incrementAndGet()
                return sqlId.call()
            }
            return null
        } as InvocationHandler)
        return (Statement) Proxy.newProxyInstance(getClass().classLoader, [Statement] as Class[], { proxy, Method method, Object[] args ->
            switch (method.name) {
                case 'isWrapperFor': return true
                case 'unwrap': return oracle
                default: return null
            }
        } as InvocationHandler)
    }
}
