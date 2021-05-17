/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@JdbcRepository(dialect = Dialect.H2)
public abstract class H2PersonRepository implements io.micronaut.data.tck.repositories.PersonRepository {

    private final JdbcOperations jdbcOperations;

    public H2PersonRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    public abstract Person save(String name, int age);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    public abstract int saveCustom(String name, int age);

    public Stream<Map<String, Object>> findAllAndStream() {
        return jdbcOperations.prepareStatement("SELECT * from person order by name asc", statement -> {
            statement.setFetchSize(5000);
            ResultSet resultSet = statement.executeQuery();
            return StreamSupport.stream(new ResultsetSpliterator(resultSet), false);
        });
    }
}


class ResultsetSpliterator extends Spliterators.AbstractSpliterator<Map<String, Object>> {

    private final ResultSet resultSet;

    public ResultsetSpliterator(final ResultSet resultSet) {
        super(Long.MAX_VALUE, Spliterator.ORDERED);
        this.resultSet = resultSet;
    }

    @Override
    public boolean tryAdvance(Consumer action) {
        if (next()) {
            try {
                action.accept(getMap());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return true;
        } else {
            try {
                resultSet.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return false;
        }
    }

    private boolean next() {
        try {
            return resultSet.next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> getMap() throws SQLException {
        Map<String, Object> record = new HashMap<>();
        for (int i = 0; i < resultSet.getMetaData().getColumnCount(); i++) {
            record.put(resultSet.getMetaData().getColumnName(i + 1), resultSet.getObject(i + 1));
        }
        return record;
    }
}