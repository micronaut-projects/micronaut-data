package example

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.services.PersonService
import io.r2dbc.spi.Connection
import jakarta.inject.Singleton

@Singleton
open class PersonServiceImpl(beanContext: ApplicationContext) : PersonService<Connection>(beanContext)
