package io.micronaut.data.processor.sql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class OneToOneCompositeJoinColumnsSpec extends AbstractDataSpec {

    private static final String PARTIALLY_SHARED_IMPORTS = '''
import java.util.UUID;
import jakarta.persistence.JoinColumn;
'''

    private static final String PARTIALLY_SHARED_ENTITIES = '''
@MappedEntity("asset")
record Asset(@EmbeddedId AssetId id,
             String title,
             @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
             @JoinColumn(name = "container_id", referencedColumnName = "container_id")
             @JoinColumn(name = "metadata_asset_id", referencedColumnName = "asset_id")
             AssetMetadata metadata) {
}

@Embeddable
record AssetId(@MappedProperty("container_id") UUID containerId,
               @MappedProperty("asset_id") Integer assetId) {
}

@MappedEntity("asset_metadata")
record AssetMetadata(@EmbeddedId AssetId id, String author) {
}
'''

    void "partially shared composite join columns write only the non-identity join column"() {
        given:
        def repository = buildRepository('test.AssetRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
$PARTIALLY_SHARED_IMPORTS
@JdbcRepository(dialect = Dialect.H2)
interface AssetRepository extends GenericRepository<Asset, AssetId> {
    Asset save(Asset entity);
    Asset update(Asset entity);
    @Join("metadata")
    List<Asset> findByTitle(String title);
}
$PARTIALLY_SHARED_ENTITIES
""")
        def saveMethod = repository.findPossibleMethods("save").findFirst().get()
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def findMethod = repository.findPossibleMethods("findByTitle").findFirst().get()

        expect:
        getQuery(saveMethod) == 'INSERT INTO `asset` (`title`,`metadata_asset_id`,`container_id`,`asset_id`) VALUES (?,?,?,?)'
        getParameterPropertyPaths(saveMethod) == ["title", "metadata.id.assetId", "id.containerId", "id.assetId"] as String[]
        getQuery(updateMethod) == 'UPDATE `asset` SET `title`=?,`metadata_asset_id`=? WHERE (`container_id` = ? AND `asset_id` = ?)'
        getParameterPropertyPaths(updateMethod) == ["title", "metadata.id.assetId", "id.containerId", "id.assetId"] as String[]
        getQuery(findMethod).contains('ON asset_.`container_id`=asset_metadata_.`container_id` AND asset_.`metadata_asset_id`=asset_metadata_.`asset_id`')
        !getQuery(findMethod).contains('metadata_id_')
    }

    void "partially shared composite join columns define only the non-identity join column in ddl"() {
        given:
        def entity = buildEntity('test.Asset', PARTIALLY_SHARED_IMPORTS + PARTIALLY_SHARED_ENTITIES)
        def sql = new SqlQueryBuilder(Dialect.H2).buildBatchCreateTableStatement(List.of(), entity)

        expect:
        sql.contains('`metadata_asset_id`')
        !sql.contains('`metadata_container_id`')
        sql.count('`container_id`') == 2 // column and primary key
        sql.count('`asset_id`') == 2 // column and primary key
    }

    void "join column is matched by the column name of the associated table"() {
        given:
        def repository = buildRepository('test.DocumentRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import java.util.UUID;
import jakarta.persistence.JoinColumn;

@JdbcRepository(dialect = Dialect.H2)
interface DocumentRepository extends GenericRepository<Document, Long> {
    Document save(Document entity);
    Document update(Document entity);
    @Join("metadata")
    List<Document> findByTitle(String title);
}

@Embeddable
record MetadataId(UUID containerId, Integer assetId) {
}

@MappedEntity("document")
record Document(@Id Long id,
                String title,
                @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
                @JoinColumn(name = "meta_container", referencedColumnName = "md_container_id")
                @JoinColumn(name = "meta_asset", referencedColumnName = "md_asset_id")
                DocumentMetadata metadata) {
}

// The embedded id is prefixed, so the associated columns differ from the persisted property names
@MappedEntity("document_metadata")
record DocumentMetadata(@EmbeddedId @MappedProperty("md") MetadataId id, String author) {
}
""")
        def saveMethod = repository.findPossibleMethods("save").findFirst().get()
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def findMethod = repository.findPossibleMethods("findByTitle").findFirst().get()

        expect:
        getQuery(saveMethod) == 'INSERT INTO `document` (`title`,`meta_container`,`meta_asset`,`id`) VALUES (?,?,?,?)'
        getParameterPropertyPaths(saveMethod) == ["title", "metadata.id.containerId", "metadata.id.assetId", "id"] as String[]
        getQuery(updateMethod) == 'UPDATE `document` SET `title`=?,`meta_container`=?,`meta_asset`=? WHERE (`id` = ?)'
        getQuery(findMethod).contains('document_.`meta_container`')
        getQuery(findMethod).contains('ON document_.`meta_container`=document_metadata_.`md_container_id` AND document_.`meta_asset`=document_metadata_.`md_asset_id`')
    }

    void "join column with unmatched referenced column name falls back to the derived column name"() {
        given:
        def repository = buildRepository('test.ReportRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import java.util.UUID;
import jakarta.persistence.JoinColumn;

@JdbcRepository(dialect = Dialect.H2)
interface ReportRepository extends GenericRepository<Report, Long> {
    Report save(Report entity);
}

@Embeddable
record ReportMetadataId(@MappedProperty("container_id") UUID containerId,
                        @MappedProperty("asset_id") Integer assetId) {
}

@MappedEntity("report")
record Report(@Id Long id,
              String title,
              @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
              @JoinColumn(name = "meta_container", referencedColumnName = "container_id")
              @JoinColumn(name = "meta_asset", referencedColumnName = "no_such_column")
              ReportMetadata metadata) {
}

@MappedEntity("report_metadata")
record ReportMetadata(@EmbeddedId ReportMetadataId id, String author) {
}
""")
        def saveMethod = repository.findPossibleMethods("save").findFirst().get()

        expect:
        getQuery(saveMethod) == 'INSERT INTO `report` (`title`,`meta_container`,`metadata_asset_id`,`id`) VALUES (?,?,?,?)'
        getParameterPropertyPaths(saveMethod) == ["title", "metadata.id.containerId", "metadata.id.assetId", "id"] as String[]
    }
}
