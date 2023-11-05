package io.micronaut.data.jdbc.postgres;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.util.List;

/**
 * Service used for testing deserialization of DTOs with collection in Postgres.
 */
@Singleton
public class PostgresDtoTestService {

    private final JdbcOperations jdbcOperations;

    public PostgresDtoTestService(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Transactional(readOnly = true)
    public <T> T getDto(long id, Class<T> clazz) {
        return jdbcOperations.prepareStatement(String.format("SELECT %d AS id, '{\"foo\", \"bar\"}'::text[] aS tags;", id), statement -> {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return jdbcOperations.readDTO(resultSet, DummyEntity.class, clazz);
            }
            throw new EmptyResultException();
        });
    }

    static abstract class BaseModel {
        private long id;

        private List<String> tags;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @MappedEntity
    static class DummyEntity extends BaseModel {
    }

    @Introspected
    static class DtoWithoutConstructor extends BaseModel {
    }

    @Introspected
    static class DtoWithAllArgsConstructor extends BaseModel {

        DtoWithAllArgsConstructor(long id, List<String> tags) {
            setId(id);
            setTags(tags);
        }
    }

    @Introspected
    record DtoRecord(long id, List<String> tags) {}
}
