package io.micronaut.data.jdbc.h2.one2one

import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.Connection
import java.util.UUID

@MicronautTest
@H2DBProperties(packages = "io.micronaut.data.jdbc.h2.one2one", schemaGenerate = "NONE")
class OneToOneEmbeddedIdJoinColumnRecordSpec extends Specification {

    @Inject
    RecordAssetRepository recordAssetRepository

    @Inject
    Connection connection

    void setup() {
        try (def s = connection.createStatement()) {
            s.execute('''
DROP TABLE IF EXISTS record_asset;
DROP TABLE IF EXISTS record_assetmetadata;

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

INSERT INTO record_assetmetadata (container_id, asset_id, author) VALUES ('6f8d3ed4-46e3-4656-9e89-cd61ac1e4cf8', 1, 'chris');
INSERT INTO record_asset (container_id, asset_id, title) VALUES ('6f8d3ed4-46e3-4656-9e89-cd61ac1e4cf8', 1, 'Llama Llama');
''')
        }
    }

    void 'fetch join owning one-to-one with composite join columns and embedded id records'() {
        given:
        def id = new RecordAssetId(UUID.fromString('6f8d3ed4-46e3-4656-9e89-cd61ac1e4cf8'), 1)

        when:
        def asset = recordAssetRepository.findById(id).orElse(null)

        then:
        asset != null
        asset.metadata() != null
        asset.metadata().author() == 'chris'
        asset.metadata().id() == id
    }
}
