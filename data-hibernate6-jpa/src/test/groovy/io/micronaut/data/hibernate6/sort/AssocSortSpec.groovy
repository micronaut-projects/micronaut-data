package io.micronaut.data.hibernate6.sort

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.hibernate6.sort")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class AssocSortSpec extends Specification {

    @Inject
    Level1Repository repository

    def "should sort on 3rd level"() {
        when:
            // insert the first item with children
            Level1 item_1 = new Level1("item_1")
            Level2 item_1_X = new Level2("item_1_X", item_1)
            Level3 item_1_X_X = new Level3("item_1_X_X", item_1_X)
            item_1_X.setLevel3(item_1_X_X)
            item_1.setLevel2(item_1_X)
            repository.save(item_1)
            List<Level1> items = repository.findAll()

        then:
            items.size() == 1
            items.get(0).getName1() == "item_1"
            items.get(0).getLevel2().getName2() == "item_1_X"
            items.get(0).getLevel2().getLevel3().getName3() == "item_1_X_X"

        when:
            // insert the second item with children
            Level1 item_2 = new Level1("item_2")
            Level2 item_2_X = new Level2("item_2_X", item_2)
            Level3 item_2_X_X = new Level3("item_2_X_X", item_2_X)
            item_2_X.setLevel3(item_2_X_X)
            item_2.setLevel2(item_2_X)
            repository.save(item_2)

            items = repository.findAll()

        then:
            items.size() == 2
            items.get(0).getName1() == "item_1"
            items.get(0).getLevel2().getName2() == "item_1_X"
            items.get(0).getLevel2().getLevel3().getName3() == "item_1_X_X"
            items.get(1).getName1() == "item_2"
            items.get(1).getLevel2().getName2() == "item_2_X"
            items.get(1).getLevel2().getLevel3().getName3() == "item_2_X_X"

        when:
            // find all unsorted -> works
            items = repository.findAll(Sort.unsorted())
        then:
            items.size() == 2

        when:
            // sort the list on a first level property -> works
            items = repository.findAll(Sort.of(Sort.Order.asc("name1")))
        then:
            items.get(0).getName1() == "item_1"
            items.get(1).getName1() == "item_2"

        when:
            items = repository.findAll(Sort.of(Sort.Order.desc("name1")))
        then:
            items.get(0).getName1() == "item_2"
            items.get(1).getName1() == "item_1"

        when:
            // sort the list on a second level property -> works
            items = repository.findAll(Sort.of(Sort.Order.asc("level2.name2")))
        then:
            items.get(0).getName1() == "item_1"
            items.get(1).getName1() == "item_2"

        when:
            items = repository.findAll(Sort.of(Sort.Order.desc("level2.name2")))
        then:
            items.get(0).getName1() == "item_2"
            items.get(1).getName1() == "item_1"

        when:
            // sort the list on a third or more level property
            items = repository.findAll(Sort.of(Sort.Order.asc("level2.level3.name3")))
        then:
            items.get(0).getName1() == "item_1"
            items.get(1).getName1() == "item_2"

        when:
            items = repository.findAll(Sort.of(Sort.Order.desc("level2.level3.name3")))
        then:
            items.get(0).getName1() == "item_2"
            items.get(1).getName1() == "item_1"
    }

}
