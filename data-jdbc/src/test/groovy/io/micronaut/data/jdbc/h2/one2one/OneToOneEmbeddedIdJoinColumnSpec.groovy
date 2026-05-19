package io.micronaut.data.jdbc.h2.one2one

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.JoinColumn
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.util.UUID

@MicronautTest
@H2DBProperties(packages = "io.micronaut.data.jdbc.h2.one2one", schemaGenerate = "NONE")
class OneToOneEmbeddedIdJoinColumnSpec extends Specification {

    @Shared
    @Inject
    AssetRepository assetRepository

    @Shared
    @Inject
    Connection connection

    void setup() {
        try (def s = connection.createStatement()) {
            s.execute('''
DROP TABLE IF EXISTS asset;
DROP TABLE IF EXISTS assetmetadata;

CREATE TABLE asset (
    container_id UUID NOT NULL,
    asset_id INTEGER NOT NULL,
    title VARCHAR(255),
    PRIMARY KEY (container_id, asset_id)
);

CREATE TABLE assetmetadata (
    container_id UUID NOT NULL,
    asset_id INTEGER NOT NULL,
    author VARCHAR(255),
    PRIMARY KEY (container_id, asset_id)
);
''')
        }
    }

    void 'save owning one-to-one with composite join columns and embedded id'() {
        given:
        def id = new AssetId(containerId: UUID.randomUUID(), assetId: 1)

        when:
        assetRepository.save(new Asset(id: id, title: 'title'))
        def saved = assetRepository.findById(id).orElse(null)

        then:
        saved != null
        saved.id.containerId == id.containerId
        saved.id.assetId == id.assetId
        saved.title == 'title'
    }

    void 'fetch join owning one-to-one with composite join columns and embedded id'() {
        given:
        def id = new AssetId(containerId: UUID.fromString('6f8d3ed4-46e3-4656-9e89-cd61ac1e4cf8'), assetId: 1)
        try (def s = connection.createStatement()) {
            s.execute("""
INSERT INTO assetmetadata (container_id, asset_id, author) VALUES ('${id.containerId}', ${id.assetId}, 'chris');
INSERT INTO asset (container_id, asset_id, title) VALUES ('${id.containerId}', ${id.assetId}, 'Llama Llama');
""")
        }

        when:
        def asset = assetRepository.findById(id).orElse(null)

        then:
        asset != null
        asset.metadata != null
        asset.metadata.author == 'chris'
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface AssetRepository extends CrudRepository<Asset, AssetId> {

    @Join(value = "metadata", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<Asset> findById(AssetId id)
}

@Embeddable
class AssetId {

    @MappedProperty("container_id")
    UUID containerId

    @MappedProperty("asset_id")
    Integer assetId
}

@MappedEntity("asset")
class Asset {

    @EmbeddedId
    AssetId id

    String title

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "container_id", referencedColumnName = "container_id")
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
    AssetMetadata metadata
}

@MappedEntity("assetmetadata")
class AssetMetadata {

    @EmbeddedId
    AssetId id

    String author
}
