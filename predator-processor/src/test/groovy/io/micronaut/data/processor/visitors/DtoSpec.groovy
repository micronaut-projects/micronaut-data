package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

class DtoSpec extends AbstractPredatorSpec {

    void "test build repository with DTO projection - invalid types"() {
        when:
        buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    List<PersonDto> list(String name);
}

@Introspected
class PersonDto {
    private int name;
    
    public int getName() {
        return name;
    }

    public void setName(int name) {
        this.name = name;
    }
    
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Property [name] of type [int] is not compatible with equivalent property declared in entity: io.micronaut.data.model.entities.Person')
    }


    void "test build repository with DTO projection"() {
        when:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    List<PersonDto> list(String name);
}

@Introspected
class PersonDto {
    private String name;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
}
""")
        then:
        repository != null
        def method = repository.getRequiredMethod("list", String)
        def ann = method.synthesize(PredatorMethod)
        ann.resultType().name.contains("PersonDto")
        ann.rootEntity() == Person
        method.synthesize(Query).value() == "SELECT person.name AS name FROM $Person.name AS person WHERE (person.name = :p1)"
        method.isTrue(PredatorMethod, PredatorMethod.META_MEMBER_DTO)
    }
}
