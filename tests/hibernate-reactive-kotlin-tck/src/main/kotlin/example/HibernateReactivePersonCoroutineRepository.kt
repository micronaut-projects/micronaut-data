package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.tck.repositories.PersonCoroutineRepository

@Repository
interface HibernateReactivePersonCoroutineRepository : PersonCoroutineRepository
