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
    RecordAssetRepository recordAssetRepository

    @Shared
    @Inject
    PartialAssetRepository partialAssetRepository

    @Shared
    @Inject
    Connection connection

    void setup() {
        try (def s = connection.createStatement()) {
            s.execute('''
DROP TABLE IF EXISTS asset;
DROP TABLE IF EXISTS assetmetadata;
DROP TABLE IF EXISTS record_asset;
DROP TABLE IF EXISTS record_assetmetadata;
DROP TABLE IF EXISTS partial_asset;
DROP TABLE IF EXISTS partial_asset_metadata;

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

CREATE TABLE record_asset (
    container_id UUID NOT NULL,
    asset_id INTEGER NOT NULL,
    title VARCHAR(255),
    PRIMARY KEY (container_id, asset_id)
);

CREATE TABLE record_assetmetadata (
    container_id UUID NOT NULL,
    asset_id INTEGER NOT NULL,
    author VARCHAR(255),
    PRIMARY KEY (container_id, asset_id)
);

CREATE TABLE partial_asset (
    container_id UUID NOT NULL,
    asset_id INTEGER NOT NULL,
    title VARCHAR(255),
    metadata_asset_id INTEGER,
    PRIMARY KEY (container_id, asset_id)
);

CREATE TABLE partial_asset_metadata (
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

    void 'update owning one-to-one does not write shared identity from relation path'() {
        given:
        def id = new AssetId(containerId: UUID.randomUUID(), assetId: 1)
        assetRepository.save(new Asset(id: id, title: 'title'))

        when:
        assetRepository.update(new Asset(id: id, title: 'updated', metadata: null))
        def updated = assetRepository.findById(id).orElse(null)

        then:
        updated != null
        updated.id.containerId == id.containerId
        updated.id.assetId == id.assetId
        updated.title == 'updated'
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

    void 'fetch join owning one-to-one with composite join columns and embedded id records'() {
        given:
        def id = new RecordAssetId(UUID.fromString('6f8d3ed4-46e3-4656-9e89-cd61ac1e4cf8'), 1)
        try (def s = connection.createStatement()) {
            s.execute("""
INSERT INTO record_assetmetadata (container_id, asset_id, author) VALUES ('${id.containerId()}', ${id.assetId()}, 'chris');
INSERT INTO record_asset (container_id, asset_id, title) VALUES ('${id.containerId()}', ${id.assetId()}, 'Llama Llama');
""")
        }

        when:
        def asset = recordAssetRepository.findById(id).orElse(null)

        then:
        asset != null
        asset.metadata() != null
        asset.metadata().author() == 'chris'
        asset.metadata().id() == id
    }

    void 'save, fetch and update one-to-one whose join columns partially reuse the identity'() {
        given:
        def containerId = UUID.randomUUID()
        try (def s = connection.createStatement()) {
            s.execute("""
INSERT INTO partial_asset_metadata (container_id, asset_id, author) VALUES ('$containerId', 1, 'first');
INSERT INTO partial_asset_metadata (container_id, asset_id, author) VALUES ('$containerId', 2, 'second');
""")
        }
        def id = new PartialAssetId(containerId: containerId, assetId: 1)

        when: 'the relation points to a different metadata row of the same container'
        partialAssetRepository.save(new PartialAsset(id: id, title: 'title', metadata: metadataRef(containerId, 2)))
        def saved = partialAssetRepository.findById(id).orElse(null)

        then:
        saved.title == 'title'
        saved.metadata.author == 'second'
        storedMetadataAssetId(id) == 2

        when: 'the relation is changed by an update'
        partialAssetRepository.update(new PartialAsset(id: id, title: 'updated', metadata: metadataRef(containerId, 1)))
        def updated = partialAssetRepository.findById(id).orElse(null)

        then:
        updated.title == 'updated'
        updated.metadata.author == 'first'
        storedMetadataAssetId(id) == 1
    }

    private static PartialAssetMetadata metadataRef(UUID containerId, Integer assetId) {
        return new PartialAssetMetadata(id: new PartialAssetId(containerId: containerId, assetId: assetId))
    }

    private Integer storedMetadataAssetId(PartialAssetId id) {
        try (def s = connection.prepareStatement('SELECT metadata_asset_id FROM partial_asset WHERE container_id = ? AND asset_id = ?')) {
            s.setObject(1, id.containerId)
            s.setInt(2, id.assetId)
            try (def rs = s.executeQuery()) {
                rs.next()
                return rs.getInt(1)
            }
        }
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

@JdbcRepository(dialect = Dialect.H2)
interface PartialAssetRepository extends CrudRepository<PartialAsset, PartialAssetId> {

    @Join(value = "metadata", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<PartialAsset> findById(PartialAssetId id)
}

@Embeddable
class PartialAssetId {

    @MappedProperty("container_id")
    UUID containerId

    @MappedProperty("asset_id")
    Integer assetId
}

@MappedEntity("partial_asset")
class PartialAsset {

    @EmbeddedId
    PartialAssetId id

    String title

    // container_id is shared with the identity, metadata_asset_id is a separate column
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "container_id", referencedColumnName = "container_id")
    @JoinColumn(name = "metadata_asset_id", referencedColumnName = "asset_id")
    PartialAssetMetadata metadata
}

@MappedEntity("partial_asset_metadata")
class PartialAssetMetadata {

    @EmbeddedId
    PartialAssetId id

    String author
}
