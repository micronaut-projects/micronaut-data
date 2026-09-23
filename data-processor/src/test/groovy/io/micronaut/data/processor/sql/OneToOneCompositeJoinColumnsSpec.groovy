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
        getQuery(findMethod) == 'SELECT asset_.`container_id`,asset_.`asset_id`,asset_.`title`,asset_.`metadata_asset_id`,asset_metadata_.`author` AS metadata_author FROM `asset` asset_ INNER JOIN `asset_metadata` asset_metadata_ ON asset_.`container_id`=asset_metadata_.`container_id` AND asset_.`metadata_asset_id`=asset_metadata_.`asset_id` WHERE (asset_.`title` = ?)'
    }

    void "fully shared composite join columns are selected only once"() {
        given:
        def repository = buildRepository('test.SharedAssetRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import java.util.UUID;
import jakarta.persistence.JoinColumn;

@JdbcRepository(dialect = Dialect.H2)
interface SharedAssetRepository extends GenericRepository<SharedAsset, SharedAssetId> {
    Optional<SharedAsset> findById(SharedAssetId id);
    @Join("metadata")
    List<SharedAsset> findByTitle(String title);
}

@Embeddable
record SharedAssetId(@MappedProperty("container_id") UUID containerId,
                     @MappedProperty("asset_id") Integer assetId) {
}

@MappedEntity("shared_asset")
record SharedAsset(@EmbeddedId SharedAssetId id,
                   String title,
                   @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
                   @JoinColumn(name = "container_id", referencedColumnName = "container_id")
                   @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
                   SharedAssetMetadata metadata) {
}

@MappedEntity("shared_asset_metadata")
record SharedAssetMetadata(@EmbeddedId SharedAssetId id, String author) {
}
""")
        def findByIdMethod = repository.findPossibleMethods("findById").findFirst().get()
        def findByTitleMethod = repository.findPossibleMethods("findByTitle").findFirst().get()

        expect:
        getQuery(findByIdMethod) == 'SELECT shared_asset_.`container_id`,shared_asset_.`asset_id`,shared_asset_.`title` FROM `shared_asset` shared_asset_ WHERE (shared_asset_.`container_id` = ? AND shared_asset_.`asset_id` = ?)'
        getQuery(findByTitleMethod) == 'SELECT shared_asset_.`container_id`,shared_asset_.`asset_id`,shared_asset_.`title`,shared_asset_metadata_.`author` AS metadata_author FROM `shared_asset` shared_asset_ INNER JOIN `shared_asset_metadata` shared_asset_metadata_ ON shared_asset_.`container_id`=shared_asset_metadata_.`container_id` AND shared_asset_.`asset_id`=shared_asset_metadata_.`asset_id` WHERE (shared_asset_.`title` = ?)'
    }

    void "fully shared composite join columns are selected only once when joined from the inverse side"() {
        given:
        def repository = buildRepository('test.InverseMetadataRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import java.util.UUID;
import jakarta.persistence.JoinColumn;

@JdbcRepository(dialect = Dialect.H2)
interface InverseMetadataRepository extends GenericRepository<InverseMetadata, InverseId> {
    @Join("asset")
    List<InverseMetadata> findByAuthor(String author);
}

@Embeddable
class InverseId {
    @MappedProperty("container_id")
    private UUID containerId;
    @MappedProperty("asset_id")
    private Integer assetId;

    public UUID getContainerId() { return containerId; }
    public void setContainerId(UUID containerId) { this.containerId = containerId; }
    public Integer getAssetId() { return assetId; }
    public void setAssetId(Integer assetId) { this.assetId = assetId; }
}

@MappedEntity("inverse_asset")
class InverseAsset {
    @EmbeddedId
    private InverseId id;
    private String title;
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "container_id", referencedColumnName = "container_id")
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
    private InverseMetadata metadata;

    public InverseId getId() { return id; }
    public void setId(InverseId id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public InverseMetadata getMetadata() { return metadata; }
    public void setMetadata(InverseMetadata metadata) { this.metadata = metadata; }
}

@MappedEntity("inverse_metadata")
class InverseMetadata {
    @EmbeddedId
    private InverseId id;
    private String author;
    @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "metadata")
    private InverseAsset asset;

    public InverseId getId() { return id; }
    public void setId(InverseId id) { this.id = id; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public InverseAsset getAsset() { return asset; }
    public void setAsset(InverseAsset asset) { this.asset = asset; }
}
""")
        def method = repository.findPossibleMethods("findByAuthor").findFirst().get()

        expect:
        getQuery(method) == 'SELECT inverse_metadata_.`container_id`,inverse_metadata_.`asset_id`,inverse_metadata_.`author`,inverse_metadata_asset_.`container_id` AS asset_container_id,inverse_metadata_asset_.`asset_id` AS asset_asset_id,inverse_metadata_asset_.`title` AS asset_title FROM `inverse_metadata` inverse_metadata_ INNER JOIN `inverse_asset` inverse_metadata_asset_ ON inverse_metadata_.`container_id`=inverse_metadata_asset_.`container_id` AND inverse_metadata_.`asset_id`=inverse_metadata_asset_.`asset_id` WHERE (inverse_metadata_.`author` = ?)'
    }

    void "partially shared composite join columns define only the non-identity join column in ddl"() {
        given:
        def entity = buildEntity('test.Asset', PARTIALLY_SHARED_IMPORTS + PARTIALLY_SHARED_ENTITIES)
        def sql = new SqlQueryBuilder(Dialect.H2).buildBatchCreateTableStatement(List.of(), entity)

        expect:
        sql == 'CREATE TABLE `asset` (`container_id` UUID NOT NULL,`asset_id` INT NOT NULL,`title` VARCHAR(255) NOT NULL,`metadata_asset_id` INT NOT NULL, PRIMARY KEY(`container_id`,`asset_id`));'
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
        getQuery(findMethod) == 'SELECT document_.`id`,document_.`title`,document_.`meta_container`,document_.`meta_asset`,document_metadata_.`author` AS metadata_author FROM `document` document_ INNER JOIN `document_metadata` document_metadata_ ON document_.`meta_container`=document_metadata_.`md_container_id` AND document_.`meta_asset`=document_metadata_.`md_asset_id` WHERE (document_.`title` = ?)'
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

    void "join column is matched using the naming strategy of the associated entity"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.persistence.JoinColumn;

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends GenericRepository<Book, Long> {
    Book save(Book entity);
    @Join("writer")
    List<Book> findByTitle(String title);
}

@Embeddable
record WriterId(String code, String region) {
}

// The owner uses the raw naming strategy while the associated entity uses the default one
@MappedEntity(value = "book", namingStrategy = NamingStrategies.Raw.class)
record Book(@Id Long id,
            String title,
            @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
            @JoinColumn(name = "book_writer_code", referencedColumnName = "writer_code")
            @JoinColumn(name = "book_writer_region", referencedColumnName = "writer_region")
            Writer writer) {
}

@MappedEntity("writer")
record Writer(@EmbeddedId @MappedProperty("writer") WriterId id, String name) {
}
""")
        def saveMethod = repository.findPossibleMethods("save").findFirst().get()
        def findMethod = repository.findPossibleMethods("findByTitle").findFirst().get()

        expect:
        getQuery(saveMethod) == 'INSERT INTO `book` (`title`,`book_writer_code`,`book_writer_region`,`id`) VALUES (?,?,?,?)'
        getParameterPropertyPaths(saveMethod) == ["title", "writer.id.code", "writer.id.region", "id"] as String[]
        getQuery(findMethod) == 'SELECT book_.`id`,book_.`title`,book_.`book_writer_code`,book_.`book_writer_region`,book_writer_.`name` AS writer_name FROM `book` book_ INNER JOIN `writer` book_writer_ ON book_.`book_writer_code`=book_writer_.`writer_code` AND book_.`book_writer_region`=book_writer_.`writer_region` WHERE (book_.`title` = ?)'
    }
}
