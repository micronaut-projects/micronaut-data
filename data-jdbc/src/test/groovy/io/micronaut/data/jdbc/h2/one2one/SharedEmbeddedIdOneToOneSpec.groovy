package io.micronaut.data.jdbc.h2.one2one

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Asset
import io.micronaut.data.tck.entities.AssetId
import io.micronaut.data.tck.entities.AssetMetadata
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@H2DBProperties
class SharedEmbeddedIdOneToOneSpec extends Specification implements H2TestPropertyProvider {

    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run(getProperties() + getH2DataSourceProperties("default"))

    @Inject AssetRepository assetRepository = ctx.getBean(AssetRepository)
    @Inject AssetMetadataRepository assetMetadataRepository = ctx.getBean(AssetMetadataRepository)

    def "persist and read asset with metadata via shared embedded id"() {
        given:
        def id = new AssetId(java.util.UUID.randomUUID(), 1)
        assetMetadataRepository.save(new AssetMetadata(id, "chris"))
        assetRepository.save(new Asset(id, "Llama Llama", null))

        when:
        def found = assetRepository.findById(id).orElse(null)

        then:
        found != null
        found.id() == id
        found.title() == "Llama Llama"

        when:
        def all = assetRepository.findAll()

        then:
        all.size() == 1
        all[0].metadata() != null
        all[0].metadata().author() == "chris"
    }

    @JdbcRepository(dialect = Dialect.H2)
    static interface AssetRepository extends CrudRepository<Asset, AssetId> {
        @Join("metadata")
        List<Asset> findAll();
    }

    @JdbcRepository(dialect = Dialect.H2)
    static interface AssetMetadataRepository extends CrudRepository<AssetMetadata, AssetId> { }
}
