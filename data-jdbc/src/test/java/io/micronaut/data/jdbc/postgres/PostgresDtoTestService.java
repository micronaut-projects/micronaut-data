package io.micronaut.data.jdbc.postgres;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
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
    private final PostgresDtoTestRepository dtoTestRepository;

    public PostgresDtoTestService(JdbcOperations jdbcOperations, PostgresDtoTestRepository dtoTestRepository) {
        this.jdbcOperations = jdbcOperations;
        this.dtoTestRepository = dtoTestRepository;
    }

    @Transactional(readOnly = true)
    public <T> T getDto(long id, Class<T> clazz) {
        return jdbcOperations.prepareStatement(String.format("SELECT %d AS id, '{\"foo\", \"bar\"}'::text[] AS tags", id), statement -> {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return jdbcOperations.readDTO(resultSet, DummyEntity.class, clazz);
            }
            throw new EmptyResultException();
        });
    }

    public <T> T getDtoUsingRepository(long id, Class<T> clazz) {
        if (clazz.equals(DtoWithoutConstructor.class)) {
            return (T) dtoTestRepository.getDtoWithoutConstructor(id);
        }
        if (clazz.equals(DtoWithAllArgsConstructor.class)) {
            return (T) dtoTestRepository.getDtoWithAllArgsConstructor(id);
        }
        if (clazz.equals(DtoRecord.class)) {
            return (T) dtoTestRepository.getDtoRecord(id);
        }
        throw new RuntimeException("Unexpected class " + clazz);
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    interface PostgresDtoTestRepository extends GenericRepository<DummyEntity, Long> {

        @Query("SELECT :id AS id, '{\"foo\", \"bar\"}'::text[] AS tags")
        DtoWithoutConstructor getDtoWithoutConstructor(long id);

        @Query("SELECT :id AS id, '{\"foo\", \"bar\"}'::text[] AS tags")
        DtoWithAllArgsConstructor getDtoWithAllArgsConstructor(long id);

        @Query("SELECT :id AS id, '{\"foo\", \"bar\"}'::text[] AS tags")
        DtoRecord getDtoRecord(long id);
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
