package io.micronaut.data.r2dbc.postgres.one2one

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
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
import java.util.UUID

class PostgresOneToOneEmbeddedIdJoinColumnSpec extends Specification implements PostgresTestPropertyProvider {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5)

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    @Shared
    R2dbcAssetRepository assetRepository = context.getBean(R2dbcAssetRepository)

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    void setup() {
        executeStatements([
            'DROP TABLE IF EXISTS r2dbc_assetmetadata',
            'DROP TABLE IF EXISTS r2dbc_asset',
            '''CREATE TABLE r2dbc_asset (
  container_id UUID NOT NULL,
  asset_id INTEGER NOT NULL,
  title VARCHAR(255),
  PRIMARY KEY (container_id, asset_id)
)''',
            '''CREATE TABLE r2dbc_assetmetadata (
  container_id UUID NOT NULL,
  asset_id INTEGER NOT NULL,
  author VARCHAR(255),
  PRIMARY KEY (container_id, asset_id)
)'''
        ])
    }

    void 'save owning one-to-one with composite join columns and embedded id'() {
        given:
        def id = new R2dbcAssetId(containerId: UUID.randomUUID(), assetId: 1)

        when:
        assetRepository.save(new R2dbcAsset(id: id, title: 'title')).block(BLOCK_TIMEOUT)
        def saved = assetRepository.findById(id).block(BLOCK_TIMEOUT)

        then:
        saved != null
        saved.id.containerId == id.containerId
        saved.id.assetId == id.assetId
        saved.title == 'title'
    }

    void 'fetch join owning one-to-one with composite join columns and embedded id'() {
        given:
        def id = new R2dbcAssetId(containerId: UUID.fromString('6f8d3ed4-46e3-4656-9e89-cd61ac1e4cf8'), assetId: 1)
        executeStatements([
            "INSERT INTO r2dbc_assetmetadata (container_id, asset_id, author) VALUES ('${id.containerId}', ${id.assetId}, 'chris')",
            "INSERT INTO r2dbc_asset (container_id, asset_id, title) VALUES ('${id.containerId}', ${id.assetId}, 'Llama Llama')"
        ])

        when:
        def asset = assetRepository.findById(id).block(BLOCK_TIMEOUT)

        then:
        asset != null
        asset.metadata != null
        asset.metadata.author == 'chris'
        asset.metadata.id.containerId == id.containerId
        asset.metadata.id.assetId == id.assetId
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
            Connection::close
        ).block(BLOCK_TIMEOUT)
    }
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface R2dbcAssetRepository extends ReactorCrudRepository<R2dbcAsset, R2dbcAssetId> {

    @Join(value = 'metadata', type = Join.Type.LEFT_FETCH)
    @Override
    Mono<R2dbcAsset> findById(R2dbcAssetId id)
}

@Embeddable
class R2dbcAssetId {

    @MappedProperty("container_id")
    UUID containerId

    @MappedProperty("asset_id")
    Integer assetId
}

@MappedEntity("r2dbc_asset")
class R2dbcAsset {

    @EmbeddedId
    R2dbcAssetId id

    String title

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "container_id", referencedColumnName = "container_id")
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
    R2dbcAssetMetadata metadata
}

@MappedEntity("r2dbc_assetmetadata")
class R2dbcAssetMetadata {

    @EmbeddedId
    R2dbcAssetId id

    String author
}
