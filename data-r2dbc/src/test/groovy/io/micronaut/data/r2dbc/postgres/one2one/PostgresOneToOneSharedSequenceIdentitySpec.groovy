package io.micronaut.data.r2dbc.postgres.one2one

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import jakarta.persistence.JoinColumn
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

class PostgresOneToOneSharedSequenceIdentitySpec extends Specification implements PostgresTestPropertyProvider {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5)

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    @Shared
    SharedSequenceAssetRepository sharedSequenceAssetRepository = context.getBean(SharedSequenceAssetRepository)

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    void setup() {
        executeStatements([
            'DROP TABLE IF EXISTS sequence_assetmetadata',
            'DROP TABLE IF EXISTS sequence_asset',
            'DROP SEQUENCE IF EXISTS sequence_asset_seq',
            'CREATE SEQUENCE sequence_asset_seq START WITH 1 INCREMENT BY 1',
            '''CREATE TABLE sequence_asset (
  id BIGINT NOT NULL PRIMARY KEY,
  title VARCHAR(255),
  version BIGINT NOT NULL
)''',
            '''CREATE TABLE sequence_assetmetadata (
  id BIGINT NOT NULL PRIMARY KEY,
  author VARCHAR(255)
)'''
        ])
    }

    void 'save shared-key one-to-one with sequence identity reuses the physical id column'() {
        when:
        def saved = sharedSequenceAssetRepository.save(new SharedSequenceAsset(title: 'title')).block(BLOCK_TIMEOUT)

        then:
        saved != null
        saved.id == 1L
        saved.version != null
        sharedSequenceAssetRepository.findById(saved.id).block(BLOCK_TIMEOUT)?.title == 'title'
    }

    void 'fetch join shared-key one-to-one uses the shared id column'() {
        given:
        executeStatements([
            "INSERT INTO sequence_assetmetadata (id, author) VALUES (7, 'chris')",
            "INSERT INTO sequence_asset (id, title, version) VALUES (7, 'title', 0)"
        ])

        when:
        def asset = sharedSequenceAssetRepository.findById(7L).block(BLOCK_TIMEOUT)

        then:
        asset != null
        asset.metadata != null
        asset.metadata.author == 'chris'
    }

    private void executeStatements(List<String> statements) {
        Mono.usingWhen(
            Mono.from(connectionFactory.create()),
            { Connection connection ->
                Flux.fromIterable(statements)
                    .concatMap { String sql ->
                        Flux.from(connection.createStatement(sql).execute())
                            .flatMap(Result::getRowsUpdated)
                            .then()
                    }
                    .then()
            },
            Connection::close,
            { Connection connection, Throwable throwable -> connection.close() },
            Connection::close
        ).block(BLOCK_TIMEOUT)
    }
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface SharedSequenceAssetRepository extends ReactorCrudRepository<SharedSequenceAsset, Long> {

    @Join(value = 'metadata', type = Join.Type.LEFT_FETCH)
    @Override
    Mono<SharedSequenceAsset> findById(Long id)
}

@MappedEntity("sequence_asset")
class SharedSequenceAsset {

    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "sequence_asset_seq")
    Long id

    String title

    @Version
    Long version

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "id", referencedColumnName = "id")
    SharedSequenceAssetMetadata metadata
}

@MappedEntity("sequence_assetmetadata")
class SharedSequenceAssetMetadata {

    @Id
    Long id

    String author
}
