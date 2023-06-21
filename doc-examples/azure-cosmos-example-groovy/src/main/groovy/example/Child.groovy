package example

import io.micronaut.data.annotation.Relation
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Child extends GenderAware {
    String firstName
    int grade;
    @Relation(value = Relation.Kind.ONE_TO_MANY)
    List<Pet> pets = new ArrayList<>();
}

class GenderAware {
    String gender
}
