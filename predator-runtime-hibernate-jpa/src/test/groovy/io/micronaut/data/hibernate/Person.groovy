package io.micronaut.data.hibernate


import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class Person {
    @Id
    @GeneratedValue
    Long id

    String name
}
