package io.micronaut.data.document.mongodb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.mongodb.repositories.MongoCitizenRepository
import io.micronaut.data.document.mongodb.repositories.MongoCountryRepository
import io.micronaut.data.document.mongodb.repositories.MongoSettlementRepository
import io.micronaut.data.document.mongodb.repositories.MongoSettlementTypeRepository
import io.micronaut.data.document.mongodb.repositories.MongoZoneRepository
import io.micronaut.data.document.tck.entities.Citizen
import io.micronaut.data.document.tck.entities.County
import io.micronaut.data.document.tck.entities.CountyPk
import io.micronaut.data.document.tck.entities.Settlement
import io.micronaut.data.document.tck.entities.SettlementPk
import io.micronaut.data.document.tck.entities.SettlementType
import io.micronaut.data.document.tck.entities.State
import io.micronaut.data.document.tck.entities.Zone
import io.micronaut.data.document.tck.repositories.CitizenRepository
import io.micronaut.data.document.tck.repositories.CountryRepository
import io.micronaut.data.document.tck.repositories.SettlementRepository
import io.micronaut.data.document.tck.repositories.SettlementTypeRepository
import io.micronaut.data.document.tck.repositories.ZoneRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Ignore("Fix embedded id fetching")
@MicronautTest
class MongoCompositeSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    SettlementRepository settlementRepository = applicationContext.getBean(MongoSettlementRepository)

    @Shared
    @Inject
    SettlementTypeRepository settlementTypeRepository = applicationContext.getBean(MongoSettlementTypeRepository)

    @Shared
    @Inject
    ZoneRepository zoneRepository = applicationContext.getBean(MongoZoneRepository)

    @Shared
    @Inject
    CountryRepository countryRepository = applicationContext.getBean(MongoCountryRepository)

    @Shared
    @Inject
    CitizenRepository citizenRepository = applicationContext.getBean(MongoCitizenRepository)

    void 'test composite relations'() {
        given:
            Settlement settlement = new Settlement()
            State state = new State()
            state.id = 12
            SettlementType type = new SettlementType()
            type.name = "Some"
            type.id = 1
            County county = new County()
            CountyPk countyPk = new CountyPk()
            countyPk.id = 44
            countyPk.state = state
            county.id = countyPk
            county.countyName = "Costa Rica"
            Zone zone = new Zone()
            zone.name = "Danger"
            zone.id = 1
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
            settlement.id.county == null
//            settlement.id.county.countyName == null
//            settlement.id.county.id
//            settlement.id.county.id.id == 44
//            settlement.id.county.id.state.id == 12
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
            Citizen citizen = new Citizen(name: "Jack", settlements: [settlement])
            citizenRepository.save(citizen)

        then:
            citizen.id
            citizen.name == "Jack"

        when:
            citizenRepository.queryById(citizen.id).get()
            citizenRepository.findById(citizen.id).get()

        then:
            citizen.id
            verifyAll(citizen.settlements[0]) {
                it.id
                it.id.code == "20010"
                it.id.codeId == 9
                it.id.county.countyName == "Czech Republic"
                it.id.county.id
                it.id.county.id.id == 44
                it.id.county.id.state.id == 12
                it.zone.id == 1
                it.zone.name == "Danger"
                it.settlementType.id == 1
                it.settlementType.name == "Some"
                it.description == "New settlement MODIFIED"
                it.enabled
            }

        when:
            citizenRepository.update(citizen)
            citizen = citizenRepository.queryById(citizen.id).get()

        then:
            citizen.id
            citizen.name == "Jack"
            citizen.settlements == null

        when:
            citizenRepository.update(citizen)
            citizen = citizenRepository.findById(citizen.id).get()

        then:
            citizen.id
            verifyAll(citizen.settlements[0]) {
                it.id
                it.id.code == "20010"
                it.id.codeId == 9
                it.id.county.countyName == "Czech Republic"
                it.id.county.id
                it.id.county.id.id == 44
                it.id.county.id.state.id == 12
                it.zone.id == 1
                it.zone.name == "Danger"
                it.settlementType.id == 1
                it.settlementType.name == "Some"
                it.description == "New settlement MODIFIED"
                it.enabled
            }

        when:
            def settlements = settlementRepository.findAll(Pageable.from(0, 10))

        then:
            settlements.size() == 1
            verifyAll(settlements[0]) {
                it.id
                it.id.code == "20010"
                it.id.codeId == 9
                it.id.county.countyName == "Czech Republic"
                it.id.county.id
                it.id.county.id.id == 44
                it.id.county.id.state.id == 12
                it.zone.id == 1
                it.zone.name == "Danger"
                it.settlementType.id == 1
                it.settlementType.name == "Some"
                it.description == "New settlement MODIFIED"
                it.enabled
            }
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