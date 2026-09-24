package io.micronaut.data.jdbc.postgres.one2one

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.JoinColumn
import spock.lang.Specification

import java.sql.Connection

@MicronautTest
class PostgresOneToOneSharedSequenceIdentitySpec extends Specification implements PostgresTestPropertyProvider {

    @Inject
    Connection connection

    @Inject
    SharedSequenceAssetRepository sharedSequenceAssetRepository

    @Override
    List<String> packages() {
        return List.of("io.micronaut.data.jdbc.postgres.one2one")
    }

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    void setup() {
        connection.prepareStatement('''
DROP TABLE IF EXISTS sequence_assetmetadata;
DROP TABLE IF EXISTS sequence_asset;
DROP SEQUENCE IF EXISTS sequence_asset_seq;

CREATE SEQUENCE sequence_asset_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE sequence_asset (
  id BIGINT NOT NULL PRIMARY KEY,
  title VARCHAR(255)
);

CREATE TABLE sequence_assetmetadata (
  id BIGINT NOT NULL PRIMARY KEY,
  author VARCHAR(255)
);
''').withCloseable { it.executeUpdate() }
    }

    void 'save shared-key one-to-one with sequence identity reuses the physical id column'() {
        when:
        def saved = sharedSequenceAssetRepository.save(new SharedSequenceAsset(title: 'title'))

        then:
        saved.id == 1L
        sharedSequenceAssetRepository.findById(saved.id).orElse(null)?.title == 'title'
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface SharedSequenceAssetRepository extends CrudRepository<SharedSequenceAsset, Long> {
}

@MappedEntity("sequence_asset")
class SharedSequenceAsset {

    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "sequence_asset_seq")
    Long id

    String title

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
