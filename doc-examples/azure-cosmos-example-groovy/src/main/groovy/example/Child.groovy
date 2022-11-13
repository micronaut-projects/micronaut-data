package example

import io.micronaut.data.annotation.Relation
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Child {

    String firstName;
    String gender;
    int grade;
    @Relation(value = Relation.Kind.ONE_TO_MANY)
    List<Pet> pets = new ArrayList<>();

}
