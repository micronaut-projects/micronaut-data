package io.micronaut.data.model.entities

import javax.persistence.Entity
import javax.persistence.Id

@Entity
class PersonAssignedId {

    @Id
    Long id

    String name

    int age

    boolean enabled = true
}
