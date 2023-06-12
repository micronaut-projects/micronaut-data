package example

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.services.PersonService
import jakarta.inject.Singleton
import org.hibernate.reactive.stage.Stage.Session

@Singleton
open class PersonServiceImpl(beanContext: ApplicationContext) : PersonService<Session>(beanContext)
