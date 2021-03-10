package io.micronaut.data.jdbc.h2.composite

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.persistence.CascadeType
import javax.persistence.OneToMany

@MicronautTest
@H2DBProperties
class CompositeSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    SettlementRepository settlementRepository = applicationContext.getBean(SettlementRepository)

    @Shared
    @Inject
    SettlementTypeRepository settlementTypeRepository = applicationContext.getBean(SettlementTypeRepository)

    @Shared
    @Inject
    ZoneRepository zoneRepository = applicationContext.getBean(ZoneRepository)

    @Shared
    @Inject
    CountryRepository countryRepository = applicationContext.getBean(CountryRepository)

    @Shared
    @Inject
    CitizenRepository citizenRepository = applicationContext.getBean(CitizenRepository)

    void 'test insert'() {
        given:
            Settlement settlement = new Settlement()
            State state = new State()
            state.id = 12
            SettlementType type = new SettlementType()
            type.name = "Some"
            County county = new County()
            CountyPk countyPk = new CountyPk()
            countyPk.id = 44
            countyPk.state = state
            county.id = countyPk
            county.countyName = "Costa Rica"
            Zone zone = new Zone()
            zone.name = "Danger"
            SettlementPk setPk = new SettlementPk()
            setPk.code = "20010"
            setPk.codeId = 9
            setPk.county = county
            settlement.id = setPk
            settlement.zone = zone
            settlement.settlementType = type
            settlement.description = "New settlement"
            settlement.enabled = true

        when:
            settlementTypeRepository.save(type)
            zoneRepository.save(zone)
            settlementRepository.save(settlement)
            settlement = settlementRepository.findById(settlement.getId()).get()

        then:
            settlement.id
            settlement.id.code == "20010"
            settlement.id.codeId == 9
            settlement.id.county.countyName == null
            settlement.id.county.id
            settlement.id.county.id.id == 44
            settlement.id.county.id.state.id == 12
            settlement.zone.id == 1
            settlement.zone.name == "Danger"
            settlement.settlementType.id == 1
            settlement.settlementType.name == "Some"
            settlement.description == "New settlement"
            settlement.enabled

        when:
            settlement.description = "New settlement MODIFIED"
            settlementRepository.update(settlement)
            settlement = settlementRepository.findById(settlement.getId()).get()

        then:
            settlement.id
            settlement.id.code == "20010"
            settlement.id.codeId == 9
            settlement.id.county.countyName == null
            settlement.id.county.id
            settlement.id.county.id.id == 44
            settlement.id.county.id.state.id == 12
            settlement.zone.id == 1
            settlement.zone.name == "Danger"
            settlement.settlementType.id == 1
            settlement.settlementType.name == "Some"
            settlement.description == "New settlement MODIFIED"
            settlement.enabled

        when:
            settlement.id.county.countyName = "Czech Republic"
            settlement.id.county.enabled = true
            countryRepository.save(settlement.id.county)
            settlement = settlementRepository.queryById(settlement.getId()).get()

        then:
            settlement.id
            settlement.id.code == "20010"
            settlement.id.codeId == 9
            settlement.id.county.countyName == "Czech Republic"
            settlement.id.county.id
            settlement.id.county.id.id == 44
            settlement.id.county.id.state.id == 12
            settlement.zone.id == 1
            settlement.zone.name == "Danger"
            settlement.settlementType.id == 1
            settlement.settlementType.name == "Some"
            settlement.description == "New settlement MODIFIED"
            settlement.enabled

        when: "joined table citizen is added"
            Citizen citizen = new Citizen(settlements: [settlement])
            citizenRepository.save(citizen)

        then:
            citizen.id

        when:
            citizenRepository.findById(citizen.id).get()

        then:
            citizen.id
            verifyAll(citizen.settlements[0]) {
                settlement.id
                settlement.id.code == "20010"
                settlement.id.codeId == 9
                settlement.id.county.countyName == "Czech Republic"
                settlement.id.county.id
                settlement.id.county.id.id == 44
                settlement.id.county.id.state.id == 12
                settlement.zone.id == 1
                settlement.zone.name == "Danger"
                settlement.settlementType.id == 1
                settlement.settlementType.name == "Some"
                settlement.description == "New settlement MODIFIED"
                settlement.enabled
            }

        when:
            citizenRepository.update(citizen)
            citizen = citizenRepository.queryById(citizen.id).get()

        then:
            citizen.id
            citizen.settlements == null

        when:
            citizenRepository.update(citizen)
            citizen = citizenRepository.findById(citizen.id).get()

        then:
            citizen.id
            verifyAll(citizen.settlements[0]) {
                settlement.id
                settlement.id.code == "20010"
                settlement.id.codeId == 9
                settlement.id.county.countyName == "Czech Republic"
                settlement.id.county.id
                settlement.id.county.id.id == 44
                settlement.id.county.id.state.id == 12
                settlement.zone.id == 1
                settlement.zone.name == "Danger"
                settlement.settlementType.id == 1
                settlement.settlementType.name == "Some"
                settlement.description == "New settlement MODIFIED"
                settlement.enabled
            }

        when:
            def settlements = settlementRepository.findAll(Pageable.from(0, 10))

        then:
            settlements.size() == 1
            verifyAll(settlements[0]) {
                settlement.id
                settlement.id.code == "20010"
                settlement.id.codeId == 9
                settlement.id.county.countyName == "Czech Republic"
                settlement.id.county.id
                settlement.id.county.id.id == 44
                settlement.id.county.id.state.id == 12
                settlement.zone.id == 1
                settlement.zone.name == "Danger"
                settlement.settlementType.id == 1
                settlement.settlementType.name == "Some"
                settlement.description == "New settlement MODIFIED"
                settlement.enabled
            }
    }

    void "test build create Settlement"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Settlement))

        then:
            statements.join("\n") == 'CREATE TABLE "comp_settlement" ("code" VARCHAR(255) NOT NULL,"code_id" INT NOT NULL,"id_county_id_id" INT NOT NULL,"id_county_id_state_id" INT NOT NULL,"description" VARCHAR(255) NOT NULL,"settlement_type_id" BIGINT NOT NULL,"zone_id" BIGINT NOT NULL,"is_enabled" BOOLEAN NOT NULL, PRIMARY KEY("code","code_id","id_county_id_id","id_county_id_state_id"));'
    }
    
    void "test build create Citizen"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Citizen))

        then:
            statements.length == 2
            statements[0] == 'CREATE TABLE "citizen_settlement" ("citizen_id" BIGINT NOT NULL,"settlement_id_code" VARCHAR(255) NOT NULL,"settlement_id_code_id" INT NOT NULL,"settlement_id_county_id_id" INT NOT NULL,"settlement_id_county_id_state_id" INT NOT NULL);'
            statements[1] == 'CREATE TABLE "comp_citizen" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT);'
    }

    void "test build insert"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def res = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, getRuntimePersistentEntity(Settlement))

        then:
            res.query == 'INSERT INTO "comp_settlement" ("description","settlement_type_id","zone_id","is_enabled","code","code_id","id_county_id_id","id_county_id_state_id") VALUES (?,?,?,?,?,?,?,?)'
            res.parameters == [
                    '1': 'description',
                    '2': 'settlementType.id',
                    '3': 'zone.id',
                    '4': 'enabled',
                    '5': 'id.code',
                    '6': 'id.codeId',
                    '7': 'id.county.id.id',
                    '8': 'id.county.id.state.id'
            ]
    }

    void "test update insert"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def entity = getRuntimePersistentEntity(Settlement)
            def res = encoder.buildUpdate(
                    QueryModel.from(entity).idEq(new QueryParameter("xyz")),
                    entity.getPersistentPropertyNames()
            )

        then:
            res.query == 'UPDATE "comp_settlement" SET "code"=?,"code_id"=?,"id_county_id_id"=?,"id_county_id_state_id"=?,"description"=?,"settlement_type_id"=?,"zone_id"=?,"is_enabled"=? WHERE ("code" = ? AND "code_id" = ? AND "id_county_id_id" = ? AND "id_county_id_state_id" = ?)'
            res.parameters == [
                    '1': 'id.code',
                    '2': 'id.codeId',
                    '3': 'id.county.id.id',
                    '4': 'id.county.id.state.id',
                    '5': 'description',
                    '6': 'settlementType.id',
                    '7': 'zone.id',
                    '8': 'enabled',
                    '9': 'xyz.code',
                    '10': 'xyz.codeId',
                    '11': 'xyz.county.id.id',
                    '12': 'xyz.county.id.state.id'
            ]
    }

    void "test build query"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def q = encoder.buildQuery(QueryModel.from(getRuntimePersistentEntity(Settlement)).idEq(new QueryParameter("xyz")))
        then:

            q.query == 'SELECT settlement_."code",settlement_."code_id",settlement_."id_county_id_id",settlement_."id_county_id_state_id",settlement_."description",settlement_."settlement_type_id",settlement_."zone_id",settlement_."is_enabled" FROM "comp_settlement" settlement_ WHERE (settlement_."code" = ? AND settlement_."code_id" = ? AND settlement_."id_county_id_id" = ? AND settlement_."id_county_id_state_id" = ?)'
            q.parameters == [
                    '1': 'xyz.code',
                    '2': 'xyz.codeId',
                    '3': 'xyz.county.id.id',
                    '4': 'xyz.county.id.state.id'
            ]
    }

    void "test build query 2"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Settlement))
            queryModel.join("settlementType", null, Join.Type.FETCH, null)
            queryModel.join("zone", null, Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("xyz")))
        then:
            q.query == 'SELECT settlement_."code",settlement_."code_id",settlement_."id_county_id_id",settlement_."id_county_id_state_id",settlement_."description",settlement_."settlement_type_id",settlement_."zone_id",settlement_."is_enabled",settlement_settlement_type_."name" AS settlement_type_name,settlement_zone_."name" AS zone_name FROM "comp_settlement" settlement_ INNER JOIN "comp_sett_type" settlement_settlement_type_ ON settlement_."settlement_type_id"=settlement_settlement_type_."id" INNER JOIN "comp_zone" settlement_zone_ ON settlement_."zone_id"=settlement_zone_."id" WHERE (settlement_."code" = ? AND settlement_."code_id" = ? AND settlement_."id_county_id_id" = ? AND settlement_."id_county_id_state_id" = ?)'
            q.parameters == [
                    '1': 'xyz.code',
                    '2': 'xyz.codeId',
                    '3': 'xyz.county.id.id',
                    '4': 'xyz.county.id.state.id'
            ]
    }

    void "test build query 3"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Settlement))
            queryModel.join("settlementType", null, Join.Type.FETCH, null)
            queryModel.join("zone", null, Join.Type.FETCH, null)
            queryModel.join("id.county", null, Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("xyz")))
        then:
            q.query == 'SELECT settlement_."code",settlement_."code_id",settlement_."id_county_id_id",settlement_."id_county_id_state_id",settlement_."description",settlement_."settlement_type_id",settlement_."zone_id",settlement_."is_enabled",settlement_settlement_type_."name" AS settlement_type_name,settlement_zone_."name" AS zone_name,settlement_id_county_."county_name" AS id_county_county_name,settlement_id_county_."is_enabled" AS id_county_is_enabled FROM "comp_settlement" settlement_ INNER JOIN "comp_sett_type" settlement_settlement_type_ ON settlement_."settlement_type_id"=settlement_settlement_type_."id" INNER JOIN "comp_zone" settlement_zone_ ON settlement_."zone_id"=settlement_zone_."id" INNER JOIN "comp_country" settlement_id_county_ ON settlement_."id_county_id_id"=settlement_id_county_."id" AND settlement_."id_county_id_state_id"=settlement_id_county_."state_id" WHERE (settlement_."code" = ? AND settlement_."code_id" = ? AND settlement_."id_county_id_id" = ? AND settlement_."id_county_id_state_id" = ?)'
            q.parameters == [
                    '1': 'xyz.code',
                    '2': 'xyz.codeId',
                    '3': 'xyz.county.id.id',
                    '4': 'xyz.county.id.state.id'
            ]
    }

    void "test build query 4"() {
        when:
//            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata()
            QueryBuilder encoder = new SqlQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Citizen))
            queryModel.join("settlements", null, Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("xyz")))
        then:
            q.query == 'SELECT citizen_."id",citizen_settlements_."code" AS settlements_code,citizen_settlements_."code_id" AS settlements_code_id,citizen_settlements_."id_county_id_id" AS settlements_id_county_id_id,citizen_settlements_."id_county_id_state_id" AS settlements_id_county_id_state_id,citizen_settlements_."description" AS settlements_description,citizen_settlements_."settlement_type_id" AS settlements_settlement_type_id,citizen_settlements_."zone_id" AS settlements_zone_id,citizen_settlements_."is_enabled" AS settlements_is_enabled FROM "comp_citizen" citizen_ INNER JOIN "citizen_settlement" citizen_settlements_citizen_settlement_ ON citizen_."id"=citizen_settlements_citizen_settlement_."citizen_id"  INNER JOIN "comp_settlement" citizen_settlements_ ON citizen_settlements_citizen_settlement_."settlement_id_code"=citizen_settlements_."code" AND citizen_settlements_citizen_settlement_."settlement_id_code_id"=citizen_settlements_."code_id" AND citizen_settlements_citizen_settlement_."settlement_id_county_id_id"=citizen_settlements_."id_county_id_id" AND citizen_settlements_citizen_settlement_."settlement_id_county_id_state_id"=citizen_settlements_."id_county_id_state_id" WHERE (citizen_."id" = ?)'
            q.parameters == [
                    '1': 'xyz'
            ]
    }

    @Shared
    Map<Class, RuntimePersistentEntity> entities = [:]

    // entities have instance compare in some cases
    private RuntimePersistentEntity getRuntimePersistentEntity(Class type) {
        RuntimePersistentEntity entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface SettlementRepository extends CrudRepository<Settlement, SettlementPk> {

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

@JdbcRepository(dialect = Dialect.H2)
interface SettlementTypeRepository extends CrudRepository<SettlementType, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface ZoneRepository extends CrudRepository<Zone, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface CountryRepository extends CrudRepository<County, CountyPk> {
}

@JdbcRepository(dialect = Dialect.H2)
interface CitizenRepository extends CrudRepository<Citizen, Long> {

    @Join(value = "settlements", type = Join.Type.FETCH)
    @Override
    Optional<Citizen> findById(@NonNull Long id);

    Optional<Citizen> queryById(@NonNull Long id);

}

@MappedEntity("comp_state")
class State {
    @Id
    Integer id
    @MappedProperty
    String stateName
    @MappedProperty("is_enabled")
    Boolean enabled
}

@Embeddable
class CountyPk {
    @MappedProperty(value = "id")
    Integer id
    @MappedProperty(value = "state_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    State state
}

@MappedEntity("comp_country")
class County {
    @EmbeddedId
    CountyPk id
    @MappedProperty
    String countyName
    @MappedProperty("is_enabled")
    Boolean enabled
}

@Embeddable
class SettlementPk {
    @MappedProperty(value = "code")
    String code

    @MappedProperty(value = "code_id")
    Integer codeId

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    County county
}

@MappedEntity("comp_settlement")
class Settlement {
    @EmbeddedId
    SettlementPk id
    @MappedProperty
    String description
    @Relation(Relation.Kind.MANY_TO_ONE)
    SettlementType settlementType
    @Relation(Relation.Kind.MANY_TO_ONE)
    Zone zone
    @MappedProperty("is_enabled")
    Boolean enabled
}

@MappedEntity("comp_sett_type")
class SettlementType {
    @Id
    @GeneratedValue
    Long id
    @MappedProperty
    String name
}

@MappedEntity("comp_zone")
class Zone {
    @Id
    @GeneratedValue
    Long id
    @MappedProperty
    String name
}

@MappedEntity("comp_citizen")
class Citizen {
    @Id
    @GeneratedValue
    Long id

    @OneToMany(cascade = CascadeType.PERSIST)
    List<Settlement> settlements
}