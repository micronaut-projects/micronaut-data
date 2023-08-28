package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.jdbc.entities.Employee
import io.micronaut.data.tck.jdbc.entities.EmployeeGroup
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class OneToManyJoinColumnSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    EmployeeRepository employeeRepository = applicationContext.getBean(EmployeeRepository)

    @Shared
    @Inject
    EmployeeGroupRepository employeeGroupRepository = applicationContext.getBean(EmployeeGroupRepository)

    void 'test one-to-many save and load'() {
        given:
        def emp1 = new Employee()
        emp1.setCategoryId(7)
        emp1.setEmployerId(11)
        emp1.setName("Emp1")
        employeeRepository.save(emp1)

        def emp2 = new Employee()
        emp2.setCategoryId(7)
        emp2.setEmployerId(11)
        emp2.setName("Emp2")
        employeeRepository.save(emp2)

        def empGroup1 = new EmployeeGroup()
        empGroup1.setCategoryId(7)
        empGroup1.setEmployerId(11)
        empGroup1.setName("EmpGroup1")
        employeeGroupRepository.save(empGroup1)

        def empGroup2 = new EmployeeGroup()
        empGroup2.setCategoryId(7)
        empGroup2.setEmployerId(13)
        empGroup2.setName("EmpGroup2")
        employeeGroupRepository.save(empGroup2)

        when:
        def employeeGroups = employeeGroupRepository.findByCategoryIdOrderByEmployerId(7)
        then:
        employeeGroups.size() == 2
        // The emp group with employer id 11 will have 2 matching employees
        // and the other with employer id 13 won't have any matching employee
        employeeGroups[0].employees.size() == 2
        employeeGroups[1].employees.size() == 0
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface EmployeeRepository extends CrudRepository<Employee, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface EmployeeGroupRepository extends CrudRepository<EmployeeGroup, Long> {

    @Join(value = "employees", alias = "employee_", type = Join.Type.LEFT_FETCH)
    @Where("@.category_id = :categoryId")
    List<EmployeeGroup> findByCategoryIdOrderByEmployerId(Long categoryId);
}
