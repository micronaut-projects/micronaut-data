package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.tck.repositories.PersonCustomDbRepository

@Repository
interface HibernateReactivePersonCustomDbRepository : PersonCustomDbRepository
