package example.domain

import io.micronaut.core.annotation.Creator

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class Owner {

    @Id
    @GeneratedValue
    Long id
    int age
    private final String name

    @Creator
    Owner(String name) {
        this.name = name
    }

    String getName() {
        return name
    }
}