package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.tck.repositories.PersonRepository

@Repository
interface HibernateReactivePersonRepository : PersonRepository
