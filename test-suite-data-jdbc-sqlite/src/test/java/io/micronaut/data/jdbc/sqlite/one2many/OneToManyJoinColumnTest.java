package io.micronaut.data.jdbc.sqlite.one2many;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Employee;
import io.micronaut.data.tck.jdbc.entities.EmployeeGroup;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2many,io.micronaut.data.tck.jdbc.entities")
class OneToManyJoinColumnTest {

    @Inject
    EmployeeRepository employeeRepository;

    @Inject
    EmployeeGroupRepository employeeGroupRepository;

    @Test
    void testOneToManySaveAndLoad() {
        Employee emp1 = new Employee();
        emp1.setCategoryId(7L);
        emp1.setEmployerId(11L);
        emp1.setName("Emp1");
        employeeRepository.save(emp1);

        Employee emp2 = new Employee();
        emp2.setCategoryId(7L);
        emp2.setEmployerId(11L);
        emp2.setName("Emp2");
        employeeRepository.save(emp2);

        EmployeeGroup empGroup1 = new EmployeeGroup();
        empGroup1.setCategoryId(7L);
        empGroup1.setEmployerId(11L);
        empGroup1.setName("EmpGroup1");
        employeeGroupRepository.save(empGroup1);

        EmployeeGroup empGroup2 = new EmployeeGroup();
        empGroup2.setCategoryId(7L);
        empGroup2.setEmployerId(13L);
        empGroup2.setName("EmpGroup2");
        employeeGroupRepository.save(empGroup2);

        List<EmployeeGroup> employeeGroups = employeeGroupRepository.findByCategoryIdOrderByEmployerId(7L);
        assertEquals(2, employeeGroups.size());
        assertEquals(2, employeeGroups.get(0).getEmployees().size());
        assertEquals(0, employeeGroups.get(1).getEmployees().size());
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface EmployeeRepository extends CrudRepository<Employee, Long> {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface EmployeeGroupRepository extends CrudRepository<EmployeeGroup, Long> {

    @Join(value = "employees", alias = "employee_", type = Join.Type.LEFT_FETCH)
    @Where("@.category_id = :categoryId")
    List<EmployeeGroup> findByCategoryIdOrderByEmployerId(Long categoryId);
}
