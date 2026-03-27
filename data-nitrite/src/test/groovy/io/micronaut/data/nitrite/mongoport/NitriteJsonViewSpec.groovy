package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.mongoport.entities.NitriteCustomer
import io.micronaut.data.nitrite.mongoport.entities.NitriteCustomerView
import io.micronaut.data.nitrite.mongoport.repositories.NitriteCustomerRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteJsonViewSpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteCustomerRepository customerRepository = applicationContext.getBean(NitriteCustomerRepository)

    def cleanup() {
        customerRepository.deleteAll()
    }

    void 'test save and retrieve customer'() {
        when:
            NitriteCustomer customer = new NitriteCustomer(name: "John Doe", email: "john@example.com")
            customerRepository.save(customer)
            NitriteCustomer retrieved = customerRepository.findById(customer.id).get()

        then:
            retrieved.id
            retrieved.name == "John Doe"
            retrieved.email == "john@example.com"
    }

    void 'test update customer'() {
        when:
            NitriteCustomer customer = new NitriteCustomer(name: "Jane Doe", email: "jane@example.com")
            customerRepository.save(customer)
            
            customer.email = "jane.updated@example.com"
            customerRepository.update(customer)
            
            NitriteCustomer retrieved = customerRepository.findById(customer.id).get()

        then:
            retrieved.email == "jane.updated@example.com"
    }

    void 'test delete customer'() {
        when:
            NitriteCustomer customer = new NitriteCustomer(name: "Delete Me", email: "delete@example.com")
            customerRepository.save(customer)
            customerRepository.deleteById(customer.id)
            def result = customerRepository.findById(customer.id)

        then:
            !result.isPresent()
    }
}
