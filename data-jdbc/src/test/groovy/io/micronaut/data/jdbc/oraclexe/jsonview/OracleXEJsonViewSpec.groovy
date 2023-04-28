package io.micronaut.data.jdbc.oraclexe.jsonview

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import org.testcontainers.containers.OracleContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalTime

class OracleXEJsonViewSpec extends Specification {

    @Shared
    @AutoCleanup("stop")
    OracleContainer container = createContainer()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(getProperties())

    StudentClassRepository getStudentClassRepository() {
        return context.getBean(StudentClassRepository)
    }

    ClassRepository getClassRepository() {
        return context.getBean(ClassRepository)
    }

    TeacherRepository getTeacherRepository() {
        return context.getBean(TeacherRepository)
    }

    StudentRepository getStudentRepository() {
        return context.getBean(StudentRepository)
    }

    StudentViewRepository getStudentViewRepository() {
        return context.getBean(StudentViewRepository)
    }

    Map<String, String> getProperties() {
        if (container == null) {
            container = createContainer()
        }
        container.start()
        def prefix = 'datasources.default'
        def dbType = 'oracle'
        return [
                'micronaut.test.resources.scope': dbType,
                (prefix + '.db-type')           : dbType,
                (prefix + '.url')               : container.getJdbcUrl(),
                (prefix + '.username')          : container.getUsername(),
                (prefix + '.password')          : container.getPassword(),
                (prefix + '.schema-generate')   : 'CREATE',
                (prefix + '.dialect')           : Dialect.ORACLE,
                (prefix + '.packages')          : getClass().package.name
        ] as Map<String, String>
    }

    def setup() {
        studentClassRepository.deleteAll()
        classRepository.deleteAll()
        teacherRepository.deleteAll()
        studentRepository.deleteAll()

        Teacher teacherAnna = teacherRepository.save(new Teacher("Mrs. Anna"))
        Teacher teacherJeff = teacherRepository.save(new Teacher("Mr. Jeff"))

        Student denis = studentRepository.save(new Student("Denis"))
        Student josh = studentRepository.save(new Student("Josh"))
        Student fred = studentRepository.save(new Student("Fred"))

        Class math = classRepository.save(new Class("Math", "A101", LocalTime.of(10, 00), teacherAnna))
        Class english = classRepository.save(new Class("English", "A102", LocalTime.of(11, 00), teacherJeff))
        Class german = classRepository.save(new Class("German", "A103", LocalTime.of(12, 00), teacherAnna))

        studentClassRepository.save(new StudentClass(denis, math))
        studentClassRepository.save(new StudentClass(josh, math))
        studentClassRepository.save(new StudentClass(fred, math))

        studentClassRepository.save(new StudentClass(denis, german))
        studentClassRepository.save(new StudentClass(josh, english))
        studentClassRepository.save(new StudentClass(fred, german))
    }

    /**
     * Test finding data using view from records created in source tables
     */
    def "find and update"() {
        when:
        def studentName = "Denis"
        def optDenisStudentView = studentViewRepository.findByStudent(studentName)
        def found = optDenisStudentView.isPresent()
        then:
        found

        when:"Do the view update by changing class schedule times"
        def denisStudentView = optDenisStudentView.get()
        def student = studentRepository.findByName(denisStudentView.getStudent()).get()
        def classSchedule = new HashMap<>()
        for (def clazz : student.getClasses()) {
            // Keep here to verify update
            classSchedule.put(clazz.getId(), clazz.getTime())
        }

        for (def schedule : denisStudentView.getSchedule()) {
            // Schedule one hour later
            schedule.getClazz().setTime(schedule.getClazz().getTime().plusHours(1))
        }
        studentViewRepository.updateByStudent(denisStudentView, denisStudentView.getStudent())
        student = studentRepository.findByName(denisStudentView.getStudent()).get()
        then:"Validate times are scheduled one hour later"
        for (def clazz : student.getClasses()) {
            def newClassTime = clazz.getTime()
            def oldClassTime = classSchedule.get(clazz.getId())
            newClassTime.minusHours(1) == oldClassTime
        }
    }

    static OracleContainer createContainer() {
        return new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:latest-faststart").asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                .withDatabaseName("test").withInitScript("./oracle-json-view-init.sql")
    }
}
