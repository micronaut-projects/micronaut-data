package io.micronaut.data.jdbc.sqlite.composite;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.OneToMany;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.composite")
class CompositeTest {

    @Inject
    SettlementRepository settlementRepository;

    @Inject
    SettlementTypeRepository settlementTypeRepository;

    @Inject
    ZoneRepository zoneRepository;

    @Inject
    CountryRepository countryRepository;

    @Inject
    CitizenRepository citizenRepository;

    @Inject
    RuntimeCriteriaBuilder builder;

    @Disabled("citizenRepository.save(citizen) should create join table entries without any cascade")
    @Test
    void testInsert() {
        Settlement settlement = createSettlement();

        settlementTypeRepository.save(settlement.getSettlementType());
        zoneRepository.save(settlement.getZone());
        settlementRepository.save(settlement);
        settlement = settlementRepository.findById(settlement.getId()).orElseThrow();

        assertSettlement(settlement, "Some", "Danger", "New settlement", null, true);
        assertEquals(1L, settlement.getZone().getId());
        assertEquals(1L, settlement.getSettlementType().getId());

        settlement.setDescription("New settlement MODIFIED");
        settlementRepository.update(settlement);
        settlement = settlementRepository.findById(settlement.getId()).orElseThrow();

        assertSettlement(settlement, "Some", "Danger", "New settlement MODIFIED", null, true);

        settlement.getId().getCounty().setCountyName("Czech Republic");
        settlement.getId().getCounty().setEnabled(true);
        countryRepository.save(settlement.getId().getCounty());
        settlement = settlementRepository.queryById(settlement.getId()).orElseThrow();

        assertSettlement(settlement, "Some", "Danger", "New settlement MODIFIED", "Czech Republic", true);

        Citizen citizen = new Citizen();
        citizen.setName("Jack");
        citizen.setSettlements(List.of(settlement));
        citizenRepository.save(citizen);

        assertNotNull(citizen.getId());
        assertEquals("Jack", citizen.getName());

        citizenRepository.queryById(citizen.getId()).orElseThrow();
        citizen = citizenRepository.findById(citizen.getId()).orElseThrow();

        assertNotNull(citizen.getId());
        assertSettlement(citizen.getSettlements().getFirst(), "Some", "Danger", "New settlement MODIFIED", "Czech Republic", true);

        citizenRepository.update(citizen);
        citizen = citizenRepository.queryById(citizen.getId()).orElseThrow();

        assertNotNull(citizen.getId());
        assertEquals("Jack", citizen.getName());
        assertNull(citizen.getSettlements());

        citizenRepository.update(citizen);
        citizen = citizenRepository.findById(citizen.getId()).orElseThrow();

        assertNotNull(citizen.getId());
        assertSettlement(citizen.getSettlements().getFirst(), "Some", "Danger", "New settlement MODIFIED", "Czech Republic", true);

        List<Settlement> settlements = settlementRepository.findAll(Pageable.from(0, 10));

        assertEquals(1, settlements.size());
        assertSettlement(settlements.getFirst(), "Some", "Danger", "New settlement MODIFIED", "Czech Republic", true);
    }

    @Test
    void testCriteria() {
        citizenRepository.deleteAll();
        settlementRepository.deleteAll();
        countryRepository.deleteAll();
        zoneRepository.deleteAll();
        settlementTypeRepository.deleteAll();

        Settlement settlement = createSettlement();

        settlementTypeRepository.save(settlement.getSettlementType());
        zoneRepository.save(settlement.getZone());
        settlementRepository.save(settlement);
        settlement = settlementRepository.findById(settlement.getId()).orElseThrow();

        assertSettlement(settlement, "Some", "Danger", "New settlement", null, true);
        assertNotNull(settlement.getZone().getId());
        assertNotNull(settlement.getSettlementType().getId());

        settlement.setDescription("New settlement MODIFIED");
        settlementRepository.update(settlement);
        settlement = settlementRepository.findById(settlement.getId()).orElseThrow();

        assertSettlement(settlement, "Some", "Danger", "New settlement MODIFIED", null, true);

        SettlementPk settlementId = settlement.getId();
        settlementId.getCounty().setCountyName("Czech Republic");
        settlementId.getCounty().setEnabled(true);
        countryRepository.save(settlementId.getCounty());
        settlement = settlementRepository.findOne(new CriteriaQueryBuilder<>() {
            @Override
            public CriteriaQuery<Settlement> build(CriteriaBuilder criteriaBuilder) {
                CriteriaQuery<Settlement> query = criteriaBuilder.createQuery(Settlement.class);
                var root = query.from(Settlement.class);
                root.fetch("settlementType");
                root.fetch("zone");
                root.fetch("id.county");
                return query.where(criteriaBuilder.equal(root.get("id"), settlementId));
            }
        });

        assertSettlement(settlement, "Some", "Danger", "New settlement MODIFIED", "Czech Republic", true);
    }

    @Test
    void testBuildCreateSettlement() {
        SqlQueryBuilder encoder = new SqlQueryBuilder();
        String[] statements = encoder.buildCreateTableStatements(builder.getRuntimeEntityRegistry().getEntity(Settlement.class));

        assertEquals("CREATE TABLE \"comp_settlement\" (\"code\" VARCHAR(255) NOT NULL,\"code_id\" INT NOT NULL,\"id_county_id_id\" INT NOT NULL,\"id_county_id_state_id\" INT NOT NULL,\"description\" VARCHAR(255) NOT NULL,\"settlement_type_id\" BIGINT NOT NULL,\"zone_id\" BIGINT NOT NULL,\"is_enabled\" BOOLEAN NOT NULL, PRIMARY KEY(\"code\",\"code_id\",\"id_county_id_id\",\"id_county_id_state_id\"));", String.join("\n", statements));
    }

    @Test
    void testBuildCreateCitizen() {
        SqlQueryBuilder encoder = new SqlQueryBuilder();
        String[] statements = encoder.buildCreateTableStatements(builder.getRuntimeEntityRegistry().getEntity(Citizen.class));

        assertEquals(2, statements.length);
        assertEquals("CREATE TABLE \"citizen_settlement\" (\"citizen_id\" BIGINT NOT NULL,\"settlement_id_code\" VARCHAR(255) NOT NULL,\"settlement_id_code_id\" INT NOT NULL,\"settlement_id_county_id_id\" INT NOT NULL,\"settlement_id_county_id_state_id\" INT NOT NULL, PRIMARY KEY(\"citizen_id\",\"settlement_id_code\",\"settlement_id_code_id\",\"settlement_id_county_id_id\",\"settlement_id_county_id_state_id\"));", statements[0]);
        assertEquals("CREATE TABLE \"comp_citizen\" (\"id\" INTEGER PRIMARY KEY,\"name\" VARCHAR(255) NOT NULL);", statements[1]);
    }

    @Test
    void testBuildInsert() {
        var res = builder.createCriteriaInsert(Settlement.class).build(new SqlQueryBuilder());

        assertEquals("INSERT INTO \"comp_settlement\" (\"description\",\"settlement_type_id\",\"zone_id\",\"is_enabled\",\"code\",\"code_id\",\"id_county_id_id\",\"id_county_id_state_id\") VALUES (?,?,?,?,?,?,?,?)", res.getQuery());
        assertEquals(List.of("description", "settlementType.id", "zone.id", "enabled", "id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            res.getParameters().get("1"),
            res.getParameters().get("2"),
            res.getParameters().get("3"),
            res.getParameters().get("4"),
            res.getParameters().get("5"),
            res.getParameters().get("6"),
            res.getParameters().get("7"),
            res.getParameters().get("8")
        ));
    }

    @Test
    void testUpdateInsert() {
        var query = builder.createCriteriaUpdate(Settlement.class);
        query = query.where(builder.equal(query.getRoot().id(), builder.parameter(Object.class)));
        for (String prop : query.getRoot().getPersistentEntity().getPersistentPropertyNames()) {
            query.set(prop, builder.parameter(Object.class));
        }
        var res = query.build(new SqlQueryBuilder());

        assertEquals("UPDATE \"comp_settlement\" SET \"code\"=?,\"code_id\"=?,\"id_county_id_id\"=?,\"id_county_id_state_id\"=?,\"description\"=?,\"settlement_type_id\"=?,\"zone_id\"=?,\"is_enabled\"=? WHERE (\"code\" = ? AND \"code_id\" = ? AND \"id_county_id_id\" = ? AND \"id_county_id_state_id\" = ?)", res.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id", "description", "settlementType.id", "zone.id", "enabled", "id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            res.getParameters().get("1"),
            res.getParameters().get("2"),
            res.getParameters().get("3"),
            res.getParameters().get("4"),
            res.getParameters().get("5"),
            res.getParameters().get("6"),
            res.getParameters().get("7"),
            res.getParameters().get("8"),
            res.getParameters().get("9"),
            res.getParameters().get("10"),
            res.getParameters().get("11"),
            res.getParameters().get("12")
        ));
    }

    @Test
    void testBuildQueryByIdParameter() {
        var query = builder.createQuery();
        var root = query.from(Settlement.class);
        var q = query.where(builder.equal(root.id(), builder.parameter(SettlementPk.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT settlement_.\"code\",settlement_.\"code_id\",settlement_.\"id_county_id_id\",settlement_.\"id_county_id_state_id\",settlement_.\"description\",settlement_.\"settlement_type_id\",settlement_.\"zone_id\",settlement_.\"is_enabled\" FROM \"comp_settlement\" settlement_ WHERE (settlement_.\"code\" = ? AND settlement_.\"code_id\" = ? AND settlement_.\"id_county_id_id\" = ? AND settlement_.\"id_county_id_state_id\" = ?)", q.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            q.getParameters().get("1"),
            q.getParameters().get("2"),
            q.getParameters().get("3"),
            q.getParameters().get("4")
        ));
    }

    @Test
    void testBuildQueryByIdValue() {
        var settlementPk = new SettlementPk();
        settlementPk.setCode("Kode");
        settlementPk.setCodeId(123);

        var query = builder.createQuery();
        var root = query.from(Settlement.class);
        var q = query.where(builder.equal(root.id(), settlementPk)).build(new SqlQueryBuilder());

        assertEquals("SELECT settlement_.\"code\",settlement_.\"code_id\",settlement_.\"id_county_id_id\",settlement_.\"id_county_id_state_id\",settlement_.\"description\",settlement_.\"settlement_type_id\",settlement_.\"zone_id\",settlement_.\"is_enabled\" FROM \"comp_settlement\" settlement_ WHERE (settlement_.\"code\" = ? AND settlement_.\"code_id\" = ? AND settlement_.\"id_county_id_id\" = ? AND settlement_.\"id_county_id_state_id\" = ?)", q.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            q.getParameters().get("1"),
            q.getParameters().get("2"),
            q.getParameters().get("3"),
            q.getParameters().get("4")
        ));
        assertEquals("Kode", q.getParameterBindings().get(0).getValue());
        assertEquals(123, q.getParameterBindings().get(1).getValue());
    }

    @Test
    void testBuildQuery2() {
        var query = builder.createQuery();
        var root = query.from(Settlement.class);
        root.join("settlementType", Join.Type.FETCH);
        root.join("zone", Join.Type.FETCH);
        var q = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT settlement_.\"code\",settlement_.\"code_id\",settlement_.\"id_county_id_id\",settlement_.\"id_county_id_state_id\",settlement_.\"description\",settlement_.\"settlement_type_id\",settlement_.\"zone_id\",settlement_.\"is_enabled\",settlement_settlement_type_.\"name\" AS settlement_type_name,settlement_zone_.\"name\" AS zone_name FROM \"comp_settlement\" settlement_ INNER JOIN \"comp_zone\" settlement_zone_ ON settlement_.\"zone_id\"=settlement_zone_.\"id\" INNER JOIN \"comp_sett_type\" settlement_settlement_type_ ON settlement_.\"settlement_type_id\"=settlement_settlement_type_.\"id\" WHERE (settlement_.\"code\" = ? AND settlement_.\"code_id\" = ? AND settlement_.\"id_county_id_id\" = ? AND settlement_.\"id_county_id_state_id\" = ?)", q.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            q.getParameters().get("1"),
            q.getParameters().get("2"),
            q.getParameters().get("3"),
            q.getParameters().get("4")
        ));
    }

    @Test
    void testBuildQuery2Fetch() {
        var query = builder.createQuery();
        var root = query.from(Settlement.class);
        root.fetch("settlementType");
        root.fetch("zone");
        var q = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT settlement_.\"code\",settlement_.\"code_id\",settlement_.\"id_county_id_id\",settlement_.\"id_county_id_state_id\",settlement_.\"description\",settlement_.\"settlement_type_id\",settlement_.\"zone_id\",settlement_.\"is_enabled\",settlement_settlement_type_.\"name\" AS settlement_type_name,settlement_zone_.\"name\" AS zone_name FROM \"comp_settlement\" settlement_ INNER JOIN \"comp_zone\" settlement_zone_ ON settlement_.\"zone_id\"=settlement_zone_.\"id\" INNER JOIN \"comp_sett_type\" settlement_settlement_type_ ON settlement_.\"settlement_type_id\"=settlement_settlement_type_.\"id\" WHERE (settlement_.\"code\" = ? AND settlement_.\"code_id\" = ? AND settlement_.\"id_county_id_id\" = ? AND settlement_.\"id_county_id_state_id\" = ?)", q.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            q.getParameters().get("1"),
            q.getParameters().get("2"),
            q.getParameters().get("3"),
            q.getParameters().get("4")
        ));
    }

    @Test
    void testBuildQuery3() {
        var query = builder.createQuery();
        var root = query.from(Settlement.class);
        root.join("settlementType", Join.Type.FETCH);
        root.join("zone", Join.Type.FETCH);
        root.join("id.county", Join.Type.FETCH);
        var q = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT settlement_.\"code\",settlement_.\"code_id\",settlement_.\"id_county_id_id\",settlement_.\"id_county_id_state_id\",settlement_.\"description\",settlement_.\"settlement_type_id\",settlement_.\"zone_id\",settlement_.\"is_enabled\",settlement_settlement_type_.\"name\" AS settlement_type_name,settlement_id_county_.\"county_name\" AS id_county_county_name,settlement_id_county_.\"is_enabled\" AS id_county_is_enabled,settlement_zone_.\"name\" AS zone_name FROM \"comp_settlement\" settlement_ INNER JOIN \"comp_zone\" settlement_zone_ ON settlement_.\"zone_id\"=settlement_zone_.\"id\" INNER JOIN \"comp_country\" settlement_id_county_ ON settlement_.\"id_county_id_id\"=settlement_id_county_.\"id\" AND settlement_.\"id_county_id_state_id\"=settlement_id_county_.\"state_id\" INNER JOIN \"comp_sett_type\" settlement_settlement_type_ ON settlement_.\"settlement_type_id\"=settlement_settlement_type_.\"id\" WHERE (settlement_.\"code\" = ? AND settlement_.\"code_id\" = ? AND settlement_.\"id_county_id_id\" = ? AND settlement_.\"id_county_id_state_id\" = ?)", q.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            q.getParameters().get("1"),
            q.getParameters().get("2"),
            q.getParameters().get("3"),
            q.getParameters().get("4")
        ));
    }

    @Test
    void testBuildQuery3Fetch() {
        var query = builder.createQuery();
        var root = query.from(Settlement.class);
        root.fetch("settlementType");
        root.fetch("zone");
        root.fetch("id.county");
        var q = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT settlement_.\"code\",settlement_.\"code_id\",settlement_.\"id_county_id_id\",settlement_.\"id_county_id_state_id\",settlement_.\"description\",settlement_.\"settlement_type_id\",settlement_.\"zone_id\",settlement_.\"is_enabled\",settlement_settlement_type_.\"name\" AS settlement_type_name,settlement_id_county_.\"county_name\" AS id_county_county_name,settlement_id_county_.\"is_enabled\" AS id_county_is_enabled,settlement_zone_.\"name\" AS zone_name FROM \"comp_settlement\" settlement_ INNER JOIN \"comp_zone\" settlement_zone_ ON settlement_.\"zone_id\"=settlement_zone_.\"id\" INNER JOIN \"comp_country\" settlement_id_county_ ON settlement_.\"id_county_id_id\"=settlement_id_county_.\"id\" AND settlement_.\"id_county_id_state_id\"=settlement_id_county_.\"state_id\" INNER JOIN \"comp_sett_type\" settlement_settlement_type_ ON settlement_.\"settlement_type_id\"=settlement_settlement_type_.\"id\" WHERE (settlement_.\"code\" = ? AND settlement_.\"code_id\" = ? AND settlement_.\"id_county_id_id\" = ? AND settlement_.\"id_county_id_state_id\" = ?)", q.getQuery());
        assertEquals(List.of("id.code", "id.codeId", "id.county.id.id", "id.county.id.state.id"), List.of(
            q.getParameters().get("1"),
            q.getParameters().get("2"),
            q.getParameters().get("3"),
            q.getParameters().get("4")
        ));
    }

    @Test
    void testBuildQuery4() {
        var query = builder.createQuery();
        var root = query.from(Citizen.class);
        root.join("settlements", Join.Type.FETCH);
        var q = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT citizen_.\"id\",citizen_.\"name\",citizen_settlements_.\"code\" AS settlements_code,citizen_settlements_.\"code_id\" AS settlements_code_id,citizen_settlements_.\"id_county_id_id\" AS settlements_id_county_id_id,citizen_settlements_.\"id_county_id_state_id\" AS settlements_id_county_id_state_id,citizen_settlements_.\"description\" AS settlements_description,citizen_settlements_.\"settlement_type_id\" AS settlements_settlement_type_id,citizen_settlements_.\"zone_id\" AS settlements_zone_id,citizen_settlements_.\"is_enabled\" AS settlements_is_enabled FROM \"comp_citizen\" citizen_ INNER JOIN \"citizen_settlement\" citizen_settlements_citizen_settlement_ ON citizen_.\"id\"=citizen_settlements_citizen_settlement_.\"citizen_id\"  INNER JOIN \"comp_settlement\" citizen_settlements_ ON citizen_settlements_citizen_settlement_.\"settlement_id_code\"=citizen_settlements_.\"code\" AND citizen_settlements_citizen_settlement_.\"settlement_id_code_id\"=citizen_settlements_.\"code_id\" AND citizen_settlements_citizen_settlement_.\"settlement_id_county_id_id\"=citizen_settlements_.\"id_county_id_id\" AND citizen_settlements_citizen_settlement_.\"settlement_id_county_id_state_id\"=citizen_settlements_.\"id_county_id_state_id\" WHERE (citizen_.\"id\" = ?)", q.getQuery());
        assertEquals("id", q.getParameters().get("1"));
    }

    @Test
    void testBuildQuery4Fetch() {
        var query = builder.createQuery();
        var root = query.from(Citizen.class);
        root.fetch("settlements");
        var q = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT citizen_.\"id\",citizen_.\"name\",citizen_settlements_.\"code\" AS settlements_code,citizen_settlements_.\"code_id\" AS settlements_code_id,citizen_settlements_.\"id_county_id_id\" AS settlements_id_county_id_id,citizen_settlements_.\"id_county_id_state_id\" AS settlements_id_county_id_state_id,citizen_settlements_.\"description\" AS settlements_description,citizen_settlements_.\"settlement_type_id\" AS settlements_settlement_type_id,citizen_settlements_.\"zone_id\" AS settlements_zone_id,citizen_settlements_.\"is_enabled\" AS settlements_is_enabled FROM \"comp_citizen\" citizen_ INNER JOIN \"citizen_settlement\" citizen_settlements_citizen_settlement_ ON citizen_.\"id\"=citizen_settlements_citizen_settlement_.\"citizen_id\"  INNER JOIN \"comp_settlement\" citizen_settlements_ ON citizen_settlements_citizen_settlement_.\"settlement_id_code\"=citizen_settlements_.\"code\" AND citizen_settlements_citizen_settlement_.\"settlement_id_code_id\"=citizen_settlements_.\"code_id\" AND citizen_settlements_citizen_settlement_.\"settlement_id_county_id_id\"=citizen_settlements_.\"id_county_id_id\" AND citizen_settlements_citizen_settlement_.\"settlement_id_county_id_state_id\"=citizen_settlements_.\"id_county_id_state_id\" WHERE (citizen_.\"id\" = ?)", q.getQuery());
        assertEquals("id", q.getParameters().get("1"));
    }

    private Settlement createSettlement() {
        Settlement settlement = new Settlement();
        State state = new State();
        state.setId(12);
        SettlementType type = new SettlementType();
        type.setName("Some");
        County county = new County();
        CountyPk countyPk = new CountyPk();
        countyPk.setId(44);
        countyPk.setState(state);
        county.setId(countyPk);
        county.setCountyName("Costa Rica");
        Zone zone = new Zone();
        zone.setName("Danger");
        SettlementPk setPk = new SettlementPk();
        setPk.setCode("20010");
        setPk.setCodeId(9);
        setPk.setCounty(county);
        settlement.setId(setPk);
        settlement.setZone(zone);
        settlement.setSettlementType(type);
        settlement.setDescription("New settlement");
        settlement.setEnabled(true);
        return settlement;
    }

    private void assertSettlement(Settlement settlement, String typeName, String zoneName, String description, String countyName, boolean enabled) {
        assertNotNull(settlement.getId());
        assertEquals("20010", settlement.getId().getCode());
        assertEquals(9, settlement.getId().getCodeId());
        assertNotNull(settlement.getId().getCounty().getId());
        assertEquals(44, settlement.getId().getCounty().getId().getId());
        assertEquals(12, settlement.getId().getCounty().getId().getState().getId());
        if (countyName == null) {
            assertNull(settlement.getId().getCounty().getCountyName());
        } else {
            assertEquals(countyName, settlement.getId().getCounty().getCountyName());
        }
        assertEquals(zoneName, settlement.getZone().getName());
        assertEquals(typeName, settlement.getSettlementType().getName());
        assertEquals(description, settlement.getDescription());
        assertEquals(enabled, settlement.getEnabled());
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SettlementRepository extends CrudRepository<Settlement, SettlementPk>, JpaSpecificationExecutor<Settlement> {

    @Join(value = "settlementType", type = Join.Type.FETCH)
    @Join(value = "zone", type = Join.Type.FETCH)
    @Override
    Optional<Settlement> findById(@NonNull SettlementPk settlementPk);

    @Join(value = "settlementType", type = Join.Type.FETCH)
    @Join(value = "zone", type = Join.Type.FETCH)
    @Join(value = "id.county", type = Join.Type.FETCH)
    Optional<Settlement> queryById(@NonNull SettlementPk settlementPk);

    @Join(value = "settlementType", type = Join.Type.FETCH)
    @Join(value = "zone", type = Join.Type.FETCH)
    @Join(value = "id.county", type = Join.Type.FETCH)
    List<Settlement> findAll(Pageable pageable);
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SettlementTypeRepository extends CrudRepository<SettlementType, Long> {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface ZoneRepository extends CrudRepository<Zone, Long> {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CountryRepository extends CrudRepository<County, CountyPk> {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CitizenRepository extends CrudRepository<Citizen, Long> {

    @Join(value = "settlements", type = Join.Type.FETCH)
    @Override
    Optional<Citizen> findById(@NonNull Long id);

    Optional<Citizen> queryById(@NonNull Long id);
}

@MappedEntity("comp_state")
class State {

    @Id
    private Integer id;

    @MappedProperty
    private String stateName;

    @MappedProperty("is_enabled")
    private Boolean enabled;

    Integer getId() {
        return id;
    }

    void setId(Integer id) {
        this.id = id;
    }

    String getStateName() {
        return stateName;
    }

    void setStateName(String stateName) {
        this.stateName = stateName;
    }

    Boolean getEnabled() {
        return enabled;
    }

    void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

@Embeddable
class CountyPk {

    @MappedProperty("id")
    private Integer id;

    @MappedProperty("state_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    private State state;

    Integer getId() {
        return id;
    }

    void setId(Integer id) {
        this.id = id;
    }

    State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CountyPk countyPk)) {
            return false;
        }
        return Objects.equals(id, countyPk.id) && Objects.equals(state != null ? state.getId() : null, countyPk.state != null ? countyPk.state.getId() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, state != null ? state.getId() : null);
    }
}

@MappedEntity("comp_country")
class County {

    @EmbeddedId
    @MappedProperty("id")
    private CountyPk id;

    @MappedProperty
    private String countyName;

    @MappedProperty("is_enabled")
    private Boolean enabled;

    CountyPk getId() {
        return id;
    }

    void setId(CountyPk id) {
        this.id = id;
    }

    String getCountyName() {
        return countyName;
    }

    void setCountyName(String countyName) {
        this.countyName = countyName;
    }

    Boolean getEnabled() {
        return enabled;
    }

    void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

@Embeddable
class SettlementPk {

    @MappedProperty("code")
    private String code;

    @MappedProperty("code_id")
    private Integer codeId;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private County county;

    String getCode() {
        return code;
    }

    void setCode(String code) {
        this.code = code;
    }

    Integer getCodeId() {
        return codeId;
    }

    void setCodeId(Integer codeId) {
        this.codeId = codeId;
    }

    County getCounty() {
        return county;
    }

    void setCounty(County county) {
        this.county = county;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SettlementPk that)) {
            return false;
        }
        return Objects.equals(code, that.code)
            && Objects.equals(codeId, that.codeId)
            && Objects.equals(county != null ? county.getId() : null, that.county != null ? that.county.getId() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, codeId, county != null ? county.getId() : null);
    }
}

@MappedEntity("comp_settlement")
class Settlement {

    @EmbeddedId
    @MappedProperty("id")
    private SettlementPk id;

    @MappedProperty
    private String description;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private SettlementType settlementType;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Zone zone;

    @MappedProperty("is_enabled")
    private Boolean enabled;

    SettlementPk getId() {
        return id;
    }

    void setId(SettlementPk id) {
        this.id = id;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    SettlementType getSettlementType() {
        return settlementType;
    }

    void setSettlementType(SettlementType settlementType) {
        this.settlementType = settlementType;
    }

    Zone getZone() {
        return zone;
    }

    void setZone(Zone zone) {
        this.zone = zone;
    }

    Boolean getEnabled() {
        return enabled;
    }

    void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

@MappedEntity("comp_sett_type")
class SettlementType {

    @Id
    @GeneratedValue
    private Long id;

    @MappedProperty
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

@MappedEntity("comp_zone")
class Zone {

    @Id
    @GeneratedValue
    private Long id;

    @MappedProperty
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

@MappedEntity("comp_citizen")
class Citizen {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @OneToMany
    private List<Settlement> settlements;

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

    List<Settlement> getSettlements() {
        return settlements;
    }

    void setSettlements(List<Settlement> settlements) {
        this.settlements = settlements;
    }
}
