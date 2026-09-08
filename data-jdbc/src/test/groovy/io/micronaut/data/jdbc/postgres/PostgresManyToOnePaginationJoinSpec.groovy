package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresManyToOnePaginationJoinSpec extends Specification implements PostgresTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    ExtremelyLongVehicleRegistrationRecordRepository vehicleRepository = applicationContext.getBean(ExtremelyLongVehicleRegistrationRecordRepository)

    @Shared
    ExtraordinarilyLongVehicleManufacturerRecordRepository manufacturerRepository = applicationContext.getBean(ExtraordinarilyLongVehicleManufacturerRecordRepository)

    void "test issue 3851 many-to-one join with pageable sorting and long aliases on postgres"() {
        given:
        vehicleRepository.deleteAll()
        manufacturerRepository.deleteAll()

        def alpha = manufacturerRepository.save(new ExtraordinarilyLongVehicleManufacturerRecord(manufacturerName: "Alpha"))
        def beta = manufacturerRepository.save(new ExtraordinarilyLongVehicleManufacturerRecord(manufacturerName: "Beta"))
        def delta = manufacturerRepository.save(new ExtraordinarilyLongVehicleManufacturerRecord(manufacturerName: "Delta"))
        def gamma = manufacturerRepository.save(new ExtraordinarilyLongVehicleManufacturerRecord(manufacturerName: "Gamma"))
        vehicleRepository.save(new ExtremelyLongVehicleRegistrationRecord(registrationCode: "CCC", extraordinarilyLongManufacturerRecord: alpha))
        vehicleRepository.save(new ExtremelyLongVehicleRegistrationRecord(registrationCode: "BBB", extraordinarilyLongManufacturerRecord: beta))
        vehicleRepository.save(new ExtremelyLongVehicleRegistrationRecord(registrationCode: "AAA", extraordinarilyLongManufacturerRecord: delta))
        vehicleRepository.save(new ExtremelyLongVehicleRegistrationRecord(registrationCode: "DDD", extraordinarilyLongManufacturerRecord: gamma))

        when:
        def pageable = Pageable.from(1, 2, Sort.of(Sort.Order.asc("extraordinarilyLongManufacturerRecord.manufacturerName")))
        Page<ExtremelyLongVehicleRegistrationRecord> page = vehicleRepository.findAll(pageable)

        then:
        page.content*.registrationCode == ["AAA", "DDD"]
        page.content*.extraordinarilyLongManufacturerRecord*.manufacturerName == ["Delta", "Gamma"]
        page.totalSize == 4

        when:
        Slice<ExtremelyLongVehicleRegistrationRecord> slice = vehicleRepository.getAll(pageable)

        then:
        slice.content*.registrationCode == ["AAA", "DDD"]
        slice.content*.extraordinarilyLongManufacturerRecord*.manufacturerName == ["Delta", "Gamma"]

        cleanup:
        vehicleRepository.deleteAll()
        manufacturerRepository.deleteAll()
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface ExtremelyLongVehicleRegistrationRecordRepository extends CrudRepository<ExtremelyLongVehicleRegistrationRecord, Long> {

    @Join(value = "extraordinarilyLongManufacturerRecord", type = Join.Type.LEFT_FETCH)
    Page<ExtremelyLongVehicleRegistrationRecord> findAll(Pageable pageable)

    @Join(value = "extraordinarilyLongManufacturerRecord", type = Join.Type.LEFT_FETCH)
    Slice<ExtremelyLongVehicleRegistrationRecord> getAll(Pageable pageable)
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface ExtraordinarilyLongVehicleManufacturerRecordRepository extends CrudRepository<ExtraordinarilyLongVehicleManufacturerRecord, Long> {
}

@MappedEntity("vehicle_registration_record")
class ExtremelyLongVehicleRegistrationRecord {
    @Id
    @GeneratedValue
    Long id

    String registrationCode

    @Relation(Relation.Kind.MANY_TO_ONE)
    ExtraordinarilyLongVehicleManufacturerRecord extraordinarilyLongManufacturerRecord
}

@MappedEntity("vehicle_manufacturer_record")
class ExtraordinarilyLongVehicleManufacturerRecord {
    @Id
    @GeneratedValue
    Long id

    String manufacturerName
}
