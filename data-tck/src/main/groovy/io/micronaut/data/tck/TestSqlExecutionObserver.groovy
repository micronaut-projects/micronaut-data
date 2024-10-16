package io.micronaut.data.tck

import io.micronaut.data.model.DataType
import io.micronaut.data.runtime.operations.internal.sql.SqlExecutionObserver
import jakarta.inject.Singleton

@Singleton
class TestSqlExecutionObserver implements SqlExecutionObserver {
    public List<Invocation> invocations = new ArrayList<>()

    @Override
    void query(String query) {
        invocations.add(new Invocation(query))
    }

    @Override
    void parameter(int index, Object value, DataType datatype) {
        invocations.last().parameters[index] = value
    }

    @Override
    void updatedRecords(Number result) {
        invocations.last().affected = result
    }

    void clear() {
        invocations.clear()
    }

    class Invocation {
        String query
        Map<Integer, Object> parameters = [:]
        Number affected

        Invocation(String query) {
            this.query = query
        }
    }
}
