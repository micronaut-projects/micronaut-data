package io.micronaut.data.model.entities

import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import java.time.Instant

@Entity
class Company {
    @GeneratedValue
    @Id
    Long myId
    @DateCreated
    Date dateCreated

    @DateUpdated
    Instant lastUpdated

    String name
    URL url
}
