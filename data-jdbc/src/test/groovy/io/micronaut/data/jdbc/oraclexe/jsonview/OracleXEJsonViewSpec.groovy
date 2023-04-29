package io.micronaut.data.jdbc.oraclexe.jsonview

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.OptimisticLockException
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
                // Cannot create JSON view during schema creation, works via init script
                (prefix + '.schema-generate')   : 'NONE',
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
        def all = studentViewRepository.findAll()
        then:
        all.size() == 3

        when:
        for (def student : all) {
            if (student.name != 'Denis') {
                student.name = student.name + '_'
            }
        }
        studentViewRepository.updateAll(all)
        def optJoshStudentView = studentViewRepository.findByName("Josh_")
        def optFredStudentView = studentViewRepository.findByName("Fred_")
        then:
        noExceptionThrown()
        optFredStudentView.present
        optJoshStudentView.present

        when:
        def studentName = "Denis"
        def optDenisStudentView = studentViewRepository.findByName(studentName)
        def found = optDenisStudentView.present
        then:
        found
        studentViewRepository.existsById(optDenisStudentView.get().id)
        studentViewRepository.count() > 0

        when:"Do the view update by changing class schedule times"
        def denisStudentView = optDenisStudentView.get()
        def student = studentRepository.findByName(denisStudentView.getName()).get()
        def classSchedule = new HashMap<>()
        for (def clazz : student.getClasses()) {
            // Keep here to verify update
            classSchedule.put(clazz.getId(), clazz.getTime())
        }

        for (def schedule : denisStudentView.getSchedule()) {
            // Schedule one hour later
            schedule.getClazz().setTime(schedule.getClazz().getTime().plusHours(1))
        }
        studentViewRepository.updateByName(denisStudentView, denisStudentView.getName())
        student = studentRepository.findByName(denisStudentView.getName()).get()
        then:"Validate times are scheduled one hour later"
        for (def clazz : student.getClasses()) {
            def newClassTime = clazz.getTime()
            def oldClassTime = classSchedule.get(clazz.getId())
            newClassTime.minusHours(1) == oldClassTime
        }

        when:"Find non existing record"
        def randomName = UUID.randomUUID().toString()
        def optUnexpectedStudent = studentViewRepository.findByName(randomName)
        then:"Expected not found"
        !optUnexpectedStudent.present
    }

    def "find and update partial"() {
        when:
        def studentName = "Josh"
        def optJoshStudentView = studentViewRepository.findByName(studentName)
        then:
        optJoshStudentView.present

        when:"Test updating single field"
        // Let's rename the student
        def newStudentName = "New Josh"
        studentViewRepository.updateName(studentName, newStudentName)
        then:
        !studentRepository.findByName(studentName).present

        when:"Try to trigger optimistic lock exception with invalid ETAG"
        def newJoshStudentView = studentViewRepository.findByName(newStudentName).get()
        newJoshStudentView.getMetadata().setEtag(UUID.randomUUID().toString())
        studentViewRepository.update(newJoshStudentView)
        then:
        thrown(OptimisticLockException)
    }

    def "insert new"() {
        when:"Test inserting into the view"
        def ivoneStudentView = new StudentView()
        def ivoneStudentName = "Ivone"
        ivoneStudentView.setName(ivoneStudentName)

        def peterStudentView = new StudentView()
        def peterStudentName = "Peter"
        peterStudentView.setName(peterStudentName)

        def newStudentScheduleView = new StudentScheduleView()

        def teacherName = "Mrs. Anna"
        def teacherAnna = teacherRepository.findByName(teacherName)
        def className = "Math"
        def teacherView = new TeacherView()
        teacherView.setTeacher(teacherAnna.getName())
        teacherView.setTeachID(teacherAnna.getId())

        def classMath = classRepository.findByName(className)
        def studentScheduleClassView = new StudentScheduleClassView()
        // By inserting new student class, we can also update class time as class is marked as updatable in the view
        def classTime = classMath.getTime()
        studentScheduleClassView.setTime(classTime.plusMinutes(30))
        studentScheduleClassView.setName(classMath.getName())
        studentScheduleClassView.setClassID(classMath.getId())
        studentScheduleClassView.setRoom(classMath.getRoom())
        studentScheduleClassView.setTeacher(teacherView)

        newStudentScheduleView.setClazz(studentScheduleClassView)
        ivoneStudentView.setSchedule(List.of(newStudentScheduleView))
        peterStudentView.setSchedule(List.of(newStudentScheduleView))
        ivoneStudentView = studentViewRepository.save(ivoneStudentView)
        studentViewRepository.saveAll(Arrays.asList(peterStudentView))

        def optIvoneStudentView = studentViewRepository.findByName(ivoneStudentName)
        def optPeterStudentView = studentViewRepository.findById(peterStudentView.id)
        def clazz = classRepository.findByName(className)

        then:
        optPeterStudentView.present
        def found = optIvoneStudentView.isPresent()
        // And just to validate that saved local time is + 30 minutes from initial class time
        def studentClassTime = optIvoneStudentView.get().getSchedule().get(0).getClazz().getTime()
        classTime.plusMinutes(30) == studentClassTime
        // And also in class table itself
        def updatedClassTime = clazz.getTime()
        classTime.plusMinutes(30) == updatedClassTime

        when:
        studentViewRepository.deleteAll()
        def count = studentViewRepository.count()
        then:
        count == 0
    }

    def "delete record"() {
        when:
        def studentName = "Denis"
        def optionalStudentView = studentViewRepository.findByName(studentName)
        then:
        optionalStudentView.present

        when:
        studentViewRepository.deleteById(optionalStudentView.get().id)
        optionalStudentView = studentViewRepository.findByName(studentName)
        then:
        !optionalStudentView.present

        when:"Verify via regular repo"
        def optionalStudent = studentRepository.findByName(studentName)
        then:
        !optionalStudent.present

        when:
        optionalStudentView = studentViewRepository.findByName("Josh")
        def count = studentViewRepository.count()
        then:
        optionalStudentView.present
        count > 0
        when:
        studentViewRepository.deleteAll(Arrays.asList(optionalStudentView.get()))
        optionalStudentView = studentViewRepository.findByName("Josh")
        then:
        !optionalStudentView.present

        when:
        def optFredStudentView = studentViewRepository.findByName("Fred")
        studentViewRepository.delete(optFredStudentView.get())
        optFredStudentView = studentViewRepository.findByName("Fred")
        count = studentViewRepository.count()
        then:
        // After deleted should not be present
        !optFredStudentView.present
        count == 0
    }

    static OracleContainer createContainer() {
        return new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:slim-faststart").asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                .withDatabaseName("test").withInitScript("./oracle-json-view-init.sql")
    }
}
