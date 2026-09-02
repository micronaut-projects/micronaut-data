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
package benchmark;

import example.WideBook;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.AbstractDelegatingResultReader;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Measures {@link SqlResultEntityTypeMapper} reading a result set of a wide entity.
 *
 * <p>The {@code columnIndexes} parameter switches the column ordinal caching on and off: when off the mapper is
 * given a reader that reports no column index reader, which is how the mapper behaved before the ordinals were
 * cached. Both arms therefore run the same binary and differ only in that one flag.</p>
 *
 * <p>The query is executed once and re-read with a scrollable result set, so the measured work is the mapping of
 * the rows rather than the execution of the statement. A mapper is created per invocation, the way the repository
 * operations create one per query execution.</p>
 *
 * <p>By default this runs against in-memory H2, which resolves a column label with a map lookup. Point it at
 * another database to measure a driver with a more expensive resolution, for example the Oracle driver which
 * quotes the identifier on every lookup:</p>
 *
 * <pre>{@code -Dbench.jdbc.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
 * -Dbench.jdbc.user=system -Dbench.jdbc.password=...}</pre>
 */
@State(Scope.Benchmark)
public class ResultSetMapping {

    private static final String URL_PROPERTY = "bench.jdbc.url";
    private static final String USER_PROPERTY = "bench.jdbc.user";
    private static final String PASSWORD_PROPERTY = "bench.jdbc.password";
    private static final String DEFAULT_URL = "jdbc:h2:mem:mapperbench;DB_CLOSE_DELAY=-1";

    @Param({"1", "10", "100"})
    int rows;

    @Param({"true", "false"})
    boolean columnIndexes;

    private ApplicationContext applicationContext;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private RuntimePersistentEntity<WideBook> entity;
    private ResultReader<ResultSet, String> resultReader;
    private DataConversionService conversionService;
    private String quote;
    private boolean oracle;

    @Setup
    public void prepare() throws SQLException {
        applicationContext = ApplicationContext.run();
        conversionService = applicationContext.getBean(DataConversionService.class);
        entity = new RuntimePersistentEntity<>(WideBook.class);

        ColumnNameResultSetReader nameReader = new ColumnNameResultSetReader(conversionService);
        resultReader = columnIndexes ? nameReader : new NoColumnIndexReader(nameReader);

        connection = DriverManager.getConnection(
            System.getProperty(URL_PROPERTY, DEFAULT_URL),
            System.getProperty(USER_PROPERTY, "sa"),
            System.getProperty(PASSWORD_PROPERTY, ""));
        // Quote every identifier so the result set labels match the names the mapper asks for exactly
        quote = connection.getMetaData().getIdentifierQuoteString();
        oracle = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("oracle");
        createTable();
        insertRows();

        statement = connection.prepareStatement(
            "SELECT * FROM " + quoted(entity.getPersistedName()),
            ResultSet.TYPE_SCROLL_INSENSITIVE,
            ResultSet.CONCUR_READ_ONLY);
        resultSet = statement.executeQuery();
    }

    @TearDown
    public void cleanup() throws SQLException {
        resultSet.close();
        statement.close();
        dropTable();
        connection.close();
        applicationContext.close();
    }

    /**
     * Maps every row of the result set with a mapper created for this result set.
     */
    @Benchmark
    public List<WideBook> mapRows() throws SQLException {
        resultSet.beforeFirst();
        SqlResultEntityTypeMapper.PushingMapper<ResultSet, List<WideBook>> mapper =
            new SqlResultEntityTypeMapper<ResultSet, WideBook>(entity, resultReader, Set.of(), null, conversionService)
                .readManyMapper();
        while (resultSet.next()) {
            mapper.processRow(resultSet);
        }
        return mapper.getResult();
    }

    private void createTable() throws SQLException {
        dropTable();
        StringJoiner columns = new StringJoiner(", ");
        for (RuntimePersistentProperty<WideBook> property : allProperties()) {
            columns.add(quoted(property.getPersistedName()) + " " + sqlType(property));
        }
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE " + quoted(entity.getPersistedName()) + " (" + columns + ")");
        }
    }

    private void dropTable() {
        try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE " + quoted(entity.getPersistedName()));
        } catch (SQLException e) {
            // The table doesn't exist yet
        }
    }

    private void insertRows() throws SQLException {
        List<RuntimePersistentProperty<WideBook>> properties = allProperties();
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner values = new StringJoiner(", ");
        for (RuntimePersistentProperty<WideBook> property : properties) {
            columns.add(quoted(property.getPersistedName()));
            values.add("?");
        }
        String sql = "INSERT INTO " + quoted(entity.getPersistedName())
            + " (" + columns + ") VALUES (" + values + ")";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            for (int row = 0; row < rows; row++) {
                for (int i = 0; i < properties.size(); i++) {
                    RuntimePersistentProperty<WideBook> property = properties.get(i);
                    if (property.getType() == String.class) {
                        s.setString(i + 1, property.getName() + "-" + row);
                    } else {
                        s.setLong(i + 1, row);
                    }
                }
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private List<RuntimePersistentProperty<WideBook>> allProperties() {
        List<RuntimePersistentProperty<WideBook>> properties = new ArrayList<>();
        properties.add(entity.getIdentity());
        properties.addAll(entity.getPersistentProperties());
        return properties;
    }

    private String quoted(String identifier) {
        return quote + identifier + quote;
    }

    private String sqlType(RuntimePersistentProperty<WideBook> property) {
        Class<?> type = property.getType();
        if (type == String.class) {
            return oracle ? "VARCHAR2(255)" : "VARCHAR(255)";
        }
        if (type == Long.class || type == long.class) {
            return oracle ? "NUMBER(19)" : "BIGINT";
        }
        return oracle ? "NUMBER(10)" : "INT";
    }

    /**
     * A reader that hides the column index support of its delegate, reproducing the by-name reads the mapper
     * performed before the column ordinals were cached.
     */
    private static final class NoColumnIndexReader extends AbstractDelegatingResultReader<ResultSet, String> {

        NoColumnIndexReader(ResultReader<ResultSet, String> delegate) {
            super(delegate);
        }

        @Override
        public int findColumnIndex(ResultSet resultSet, String columnName) {
            return -1;
        }

        @Override
        public ResultReader<ResultSet, Integer> getColumnIndexReader() {
            return null;
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(".*" + ResultSetMapping.class.getSimpleName() + ".*")
            .warmupIterations(3)
            .measurementIterations(4)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
