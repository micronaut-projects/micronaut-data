package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.DataType
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.LocalTime

class H2DtoSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    ThingRepository thingRepository = applicationContext.getBean(ThingRepository)

    @Shared
    @Inject
    InventoryItemRepository inventoryItemRepository = applicationContext.getBean(InventoryItemRepository)

    void 'test dtos'() {
        given:
            Thing thing = new Thing(name: "Test", score: 123, site: "XYZ")
        when:
            thingRepository.save(thing)
        then:
            thing.id
        when:
            def things = thingRepository.findThingDTOsByThingId(thing.id)
        then:
            things.size() == 1
            things[0].thingId == thing.id
            things[0].thingName == "Test"
            things[0].thingUpdatedAt
            things[0].thingUpdatedAtTime
    }

    void 'test converter-backed introspected scalar projections'() {
        given:
            def item = inventoryItemRepository.save(new InventoryItem(
                    quantity: new Quantity(42),
                    objectQuantity: new ObjectQuantity(84)
            ))

        expect:
            inventoryItemRepository.findQuantityById(item.id).amount == 42
            inventoryItemRepository.findObjectQuantityById(item.id).amount == 84
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface ThingRepository extends CrudRepository<Thing, Long> {

    @Query("""
      SELECT thing.id AS thingId, thing.name AS thingName, thing.updatedAt as thingUpdatedAt, thing.updatedAt as thingUpdatedAtTime
      FROM the_things thing
      WHERE thing.id = :id
    """)
    List<ThingDTO> findThingDTOsByThingId(Long id)

}

@MappedEntity(value = "the_things", namingStrategy = NamingStrategies.Raw)
class Thing {
    @Id
    @GeneratedValue
    Long id
    String name
    Integer score
    String site
    @DateUpdated
    LocalDateTime updatedAt
}

@Introspected
@NamingStrategy(NamingStrategies.Raw)
class ThingDTO {
    Integer thingId
    String thingName
    LocalDateTime thingUpdatedAt
    LocalTime thingUpdatedAtTime
}

@JdbcRepository(dialect = Dialect.H2)
interface InventoryItemRepository extends CrudRepository<InventoryItem, Long> {

    Quantity findQuantityById(Long id)

    ObjectQuantity findObjectQuantityById(Long id)
}

@MappedEntity
class InventoryItem {
    @Id
    @GeneratedValue
    Long id
    Quantity quantity
    @MappedProperty(definition = "INTEGER")
    ObjectQuantity objectQuantity
}

@Introspected
@TypeDef(type = DataType.INTEGER, converter = QuantityConverter.class)
class Quantity {
    final int amount

    Quantity(int amount) {
        this.amount = amount
    }
}

@Singleton
class QuantityConverter implements AttributeConverter<Quantity, Integer>, TypeConverter<Integer, Quantity> {

    @Override
    Integer convertToPersistedValue(Quantity entityValue, ConversionContext context) {
        entityValue.amount
    }

    @Override
    Quantity convertToEntityValue(Integer persistedValue, ConversionContext context) {
        new Quantity(persistedValue)
    }

    @Override
    Optional<Quantity> convert(Integer value, Class<Quantity> targetType, ConversionContext context) {
        Optional.of(new Quantity(value))
    }
}

@Introspected
@TypeDef(type = DataType.OBJECT, converter = ObjectQuantityConverter.class)
class ObjectQuantity {
    final int amount

    ObjectQuantity(int amount) {
        this.amount = amount
    }
}

@Singleton
class ObjectQuantityConverter implements AttributeConverter<ObjectQuantity, Integer>, TypeConverter<Integer, ObjectQuantity> {

    @Override
    Integer convertToPersistedValue(ObjectQuantity entityValue, ConversionContext context) {
        entityValue.amount
    }

    @Override
    ObjectQuantity convertToEntityValue(Integer persistedValue, ConversionContext context) {
        new ObjectQuantity(persistedValue)
    }

    @Override
    Optional<ObjectQuantity> convert(Integer value, Class<ObjectQuantity> targetType, ConversionContext context) {
        Optional.of(new ObjectQuantity(value))
    }
}
