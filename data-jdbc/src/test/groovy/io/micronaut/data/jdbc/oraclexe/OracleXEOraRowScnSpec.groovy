package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleXEOraRowScnSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Memoized
    ItemRepository getItemRepository() {
        return context.getBean(ItemRepository)
    }

    void "test optimistic lock with ORA_ROWSCN"() {
        when:
        def item = itemRepository.save(new Item(name: "Sneakers", price: 119.99))
        def found = itemRepository.findById(item.id)
        then:
        found.present
        def foundItem = found.get()
        foundItem.oraRowscn
        when:
        foundItem.oraRowscn = foundItem.oraRowscn + 1
        itemRepository.update(foundItem)
        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }

}

@MappedEntity
class Item {
    @Id
    @GeneratedValue
    Long id

    String name

    Double price

    @Version
    @GeneratedValue
    Long oraRowscn
}
@JdbcRepository(dialect = Dialect.ORACLE)
interface ItemRepository extends CrudRepository<Item, Long> {
}
