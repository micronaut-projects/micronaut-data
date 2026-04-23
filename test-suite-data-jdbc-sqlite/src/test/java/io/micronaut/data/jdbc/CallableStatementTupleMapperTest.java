package io.micronaut.data.jdbc;

import io.micronaut.data.jdbc.mapper.CallableStatementTupleMapper;
import jakarta.persistence.Tuple;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CallableStatementTupleMapperTest {

    @Test
    void mapsAllRegisteredOutParametersIntoTupleOrder() {
        CallableStatement callableStatement = (CallableStatement) Proxy.newProxyInstance(
            CallableStatement.class.getClassLoader(),
            new Class[]{CallableStatement.class},
            (proxy, method, args) -> {
                if ("getObject".equals(method.getName()) && args != null && args.length == 1) {
                    if (Integer.valueOf(4).equals(args[0])) {
                        return "Oracle DTO Title";
                    }
                    if (Integer.valueOf(5).equals(args[0])) {
                        return 777;
                    }
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) {
                    return false;
                }
                if (returnType == byte.class || returnType == short.class || returnType == int.class || returnType == long.class) {
                    return 0;
                }
                if (returnType == float.class || returnType == double.class) {
                    return 0.0;
                }
                if (returnType == char.class) {
                    return '\0';
                }
                return null;
            }
        );
        LinkedHashMap<String, Integer> columnIndexesByName = new LinkedHashMap<>();
        columnIndexesByName.put("title", 4);
        columnIndexesByName.put("total_pages", 5);
        var mapper = new CallableStatementTupleMapper(
            io.micronaut.core.convert.ConversionService.SHARED,
            columnIndexesByName
        );

        Tuple tuple = mapper.map(callableStatement, Tuple.class);

        assertArrayEquals(new Object[]{"Oracle DTO Title", 777}, tuple.toArray());
        assertEquals("Oracle DTO Title", tuple.get("title", String.class));
        assertEquals(777, tuple.get("total_pages", Integer.class));
    }
}
