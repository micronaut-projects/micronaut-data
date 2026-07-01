package io.micronaut.data.jdbc.sqlite.embeddedAssociation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.repeatable.JoinSpecifications;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Order;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmbeddedAssociationJoinTest {

    @Test
    void testOneToOneUpdate() {
        try (ApplicationContext applicationContext = ApplicationContext.run(createProperties())) {
            MainEntityRepository mainEntityRepository = applicationContext.getBean(MainEntityRepository.class);

            ChildEntity child = new ChildEntity();
            child.setName("child");
            MainEntity main = new MainEntity();
            main.setName("test");
            main.setChild(child);
            child.setMain(main);

            mainEntityRepository.save(main);
            main.setName("diff-name");
            child.setName("diff-child");
            MainEntity updatedMain = mainEntityRepository.update(main);

            assertEquals("diff-name", updatedMain.getName());
            assertEquals("diff-child", updatedMain.getChild().getName());
        }
    }

    @Test
    void testManyToManyHierarchy() {
        try (ApplicationContext applicationContext = ApplicationContext.run(createProperties())) {
            MainEntityRepository mainEntityRepository = applicationContext.getBean(MainEntityRepository.class);
            OneMainEntityRepository oneMainEntityRepository = applicationContext.getBean(OneMainEntityRepository.class);
            OneMainEntityEmRepository oneMainEntityEmRepository = applicationContext.getBean(OneMainEntityEmRepository.class);

            MainEntity entity = new MainEntity();
            entity.setName("test");
            entity.setAssoc(new ArrayList<>(List.of(
                mainEntityAssociation("A"),
                mainEntityAssociation("B")
            )));
            MainEmbedded embedded = new MainEmbedded();
            embedded.setAssoc(new ArrayList<>(List.of(
                mainEntityAssociation("C"),
                mainEntityAssociation("D")
            )));
            entity.setEm(embedded);

            mainEntityRepository.save(entity);
            entity = mainEntityRepository.findById(entity.getId()).orElseThrow();
            Sort.Order.Direction sortDirection = Sort.Order.Direction.ASC;
            Pageable pageable = Pageable.UNPAGED.order(new Sort.Order("child.name", sortDirection, false));
            assertEquals(1, mainEntityRepository.findAll(pageable).getContent().size());
            PredicateSpecification<Order> predicate = null;
            assertEquals(1, mainEntityRepository.findAllByCriteria(predicate, pageable).getContent().size());

            assertAssoc(entity, "A", "B", "C", "D");

            mainEntityRepository.update(entity);
            entity = mainEntityRepository.findById(entity.getId()).orElseThrow();
            assertAssoc(entity, "A", "B", "C", "D");

            OneMainEntity oneMainEntity = new OneMainEntity();
            oneMainEntity.setOne(entity);
            oneMainEntity = oneMainEntityRepository.save(oneMainEntity);
            oneMainEntity = oneMainEntityRepository.findById(oneMainEntity.getId()).orElseThrow();
            assertAssoc(oneMainEntity.getOne(), "A", "B", "C", "D");

            OneMainEntityEm oneMainEntityEm = new OneMainEntityEm();
            EmId emId = new EmId();
            emId.setOne(entity);
            oneMainEntityEm.setId(emId);
            oneMainEntityEm.setName("Embedded is crazy");
            oneMainEntityEm = oneMainEntityEmRepository.insert(oneMainEntityEm);
            oneMainEntityEm = oneMainEntityEmRepository.findById(oneMainEntityEm.getId()).orElseThrow();

            assertEquals("Embedded is crazy", oneMainEntityEm.getName());
            assertAssoc(oneMainEntityEm.getId().getOne(), "A", "B", "C", "D");
        }
    }

    @Test
    void embeddedWithGeneratedValuesAreSaved() {
        try (ApplicationContext applicationContext = ApplicationContext.run(createProperties())) {
            ClientRepository clientRepository = applicationContext.getBean(ClientRepository.class);
            RelationshipStatusRepository relationshipStatusRepository = applicationContext.getBean(RelationshipStatusRepository.class);

            RelationshipStatus active = new RelationshipStatus();
            active.setId(1L);
            active.setName("Active");
            relationshipStatusRepository.insert(active);

            RelationshipStatus inactive = new RelationshipStatus();
            inactive.setId(2L);
            inactive.setName("InActive");
            relationshipStatusRepository.insert(inactive);

            RelationshipStatus status = relationshipStatusRepository.findById(1L).orElse(null);
            assertNotNull(status);
            assertEquals("Active", status.getName());

            Client client = new Client();
            client.setName("Active Client");
            Relationship relationship = new Relationship();
            relationship.setStatus(status);
            client.setRelationship(relationship);

            Client newClient = clientRepository.save(client);

            assertEquals("Active Client", newClient.getName());
            assertEquals(RelationshipType.CLIENT, newClient.getRelationship().getType());
            assertNotNull(newClient.getRelationship().getStatus());
            assertEquals(status.getId(), newClient.getRelationship().getStatus().getId());
            assertEquals(status.getName(), newClient.getRelationship().getStatus().getName());
        }
    }

    private static void assertAssoc(MainEntity entity, String a, String b, String c, String d) {
        assertNotNull(entity.getId());
        assertEquals(2, entity.getAssoc().size());
        assertEquals(a, entity.getAssoc().get(0).getName());
        assertEquals(b, entity.getAssoc().get(1).getName());
        assertNotNull(entity.getEm());
        assertEquals(2, entity.getEm().getAssoc().size());
        assertEquals(c, entity.getEm().getAssoc().get(0).getName());
        assertEquals(d, entity.getEm().getAssoc().get(1).getName());
    }

    private static MainEntityAssociation mainEntityAssociation(String name) {
        MainEntityAssociation association = new MainEntityAssociation();
        association.setName(name);
        return association;
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("embeddedassociationjoin", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite.embeddedAssociation");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface MainEntityRepository extends CrudRepository<MainEntity, Long>, JpaSpecificationExecutor<MainEntity> {

    @Join(value = "assoc", type = Join.Type.FETCH)
    @Join(value = "em.assoc", type = Join.Type.FETCH)
    @Override
    Optional<MainEntity> findById(Long id);

    @JoinSpecifications(@Join(value = "child", type = Join.Type.LEFT_FETCH))
    Page<MainEntity> findAll(Pageable pageable);

    @JoinSpecifications(@Join(value = "child", type = Join.Type.LEFT_FETCH))
    Page<MainEntity> findAllByCriteria(@Nullable PredicateSpecification<Order> spec, Pageable pageable);
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface OneMainEntityRepository extends CrudRepository<OneMainEntity, Long> {

    @Join(value = "one", type = Join.Type.FETCH)
    @Join(value = "one.assoc", type = Join.Type.FETCH)
    @Join(value = "one.em.assoc", type = Join.Type.FETCH)
    @Override
    Optional<OneMainEntity> findById(Long id);
}

@Join(value = "id.one", type = Join.Type.FETCH)
@Join(value = "id.one.assoc", type = Join.Type.FETCH)
@Join(value = "id.one.em.assoc", type = Join.Type.FETCH)
@JdbcRepository(dialect = Dialect.SQLITE)
interface OneMainEntityEmRepository extends CrudRepository<OneMainEntityEm, EmId> {
}

@MappedEntity
class OneMainEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    private MainEntity one;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    MainEntity getOne() {
        return one;
    }

    void setOne(MainEntity one) {
        this.one = one;
    }
}

@MappedEntity
class OneMainEntityEm {

    @EmbeddedId
    private EmId id;
    private String name;

    EmId getId() {
        return id;
    }

    void setId(EmId id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}

@Embeddable
class EmId implements Serializable {

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    private MainEntity one;

    MainEntity getOne() {
        return one;
    }

    void setOne(MainEntity one) {
        this.one = one;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmId emId)) {
            return false;
        }
        return Objects.equals(one, emId.one);
    }

    @Override
    public int hashCode() {
        return Objects.hash(one);
    }
}

@MappedEntity
class MainEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<MainEntityAssociation> assoc;

    @Relation(value = Relation.Kind.EMBEDDED)
    private MainEmbedded em;

    @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "main", cascade = Relation.Cascade.ALL)
    private ChildEntity child;

    private String name;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    List<MainEntityAssociation> getAssoc() {
        return assoc;
    }

    void setAssoc(List<MainEntityAssociation> assoc) {
        this.assoc = assoc;
    }

    MainEmbedded getEm() {
        return em;
    }

    void setEm(MainEmbedded em) {
        this.em = em;
    }

    ChildEntity getChild() {
        return child;
    }

    void setChild(ChildEntity child) {
        this.child = child;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}

@MappedEntity
class ChildEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(Relation.Kind.ONE_TO_ONE)
    private MainEntity main;

    private String name;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    MainEntity getMain() {
        return main;
    }

    void setMain(MainEntity main) {
        this.main = main;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}

@Embeddable
class MainEmbedded {

    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<MainEntityAssociation> assoc;

    List<MainEntityAssociation> getAssoc() {
        return assoc;
    }

    void setAssoc(List<MainEntityAssociation> assoc) {
        this.assoc = assoc;
    }
}

@MappedEntity
class MainEntityAssociation {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}

@MappedEntity
class RelationshipStatus {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }
}

enum RelationshipType {
    CLIENT,
    SUPPLIER
}

@Embeddable
class Relationship {

    private RelationshipType type = RelationshipType.CLIENT;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private RelationshipStatus status;

    RelationshipType getType() {
        return type;
    }

    void setType(RelationshipType type) {
        this.type = type;
    }

    RelationshipStatus getStatus() {
        return status;
    }

    void setStatus(RelationshipStatus status) {
        this.status = status;
    }
}

@MappedEntity
class Client {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @Relation(value = Relation.Kind.EMBEDDED)
    private Relationship relationship;

    @DateCreated
    private Instant createdAt = Instant.now();

    @DateUpdated
    private Instant updatedAt = Instant.now();

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Relationship getRelationship() {
        return relationship;
    }

    void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
@Join(value = "relationship.status", type = Join.Type.LEFT_FETCH)
interface ClientRepository extends CrudRepository<Client, Long> {
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface RelationshipStatusRepository extends CrudRepository<RelationshipStatus, Long> {
}
