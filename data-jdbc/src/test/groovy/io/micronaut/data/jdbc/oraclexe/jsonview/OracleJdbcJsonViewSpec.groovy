package io.micronaut.data.jdbc.oraclexe.jsonview

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Address
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.AddressView
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Class
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Driver
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.DriverRaceMap
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Race
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Student
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.StudentClass
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.StudentScheduleView
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.StudentView
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Teacher
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.TeacherView
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Team
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.AddressRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.ClassRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.DriverRaceMapRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.DriverRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.DriverViewRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.RaceRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.StudentClassRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.StudentRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.StudentScheduleClassView
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.StudentViewRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.TeacherRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.TeamRepository
import io.micronaut.data.jdbc.oraclexe.jsonview.repositories.TeamViewRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.LocalTime

@MicronautTest(environments = ["oracle-jsonview"])
class OracleJdbcJsonViewSpec extends Specification {

    @Inject
    StudentClassRepository studentClassRepository

    @Inject
    ClassRepository classRepository

    @Inject
    TeacherRepository teacherRepository

    @Inject
    AddressRepository addressRepository

    @Inject
    StudentRepository studentRepository

    @Inject
    StudentViewRepository studentViewRepository

    @Inject
    TeamRepository teamRepository

    @Inject
    DriverRepository driverRepository

    @Inject
    RaceRepository raceRepository

    @Inject
    DriverRaceMapRepository driverRaceMapRepository

    @Inject
    DriverViewRepository driverViewRepository

    @Inject
    TeamViewRepository teamViewRepository

    def setup() {
        studentClassRepository.deleteAll()
        classRepository.deleteAll()
        teacherRepository.deleteAll()
        studentRepository.deleteAll()

        Teacher teacherAnna = teacherRepository.save(new Teacher("Mrs. Anna"))
        Teacher teacherJeff = teacherRepository.save(new Teacher("Mr. Jeff"))

        Address address1 = addressRepository.save(new Address("Main Street", "City1"))
        Address address2 = addressRepository.save(new Address("New Street", "City1"))

        def startDateTime = LocalDateTime.now()
        Student denis = studentRepository.save(new Student("Denis", 8.5, startDateTime.minusDays(1), address1))
        Student josh = studentRepository.save(new Student("Josh", 9.1, startDateTime, address1))
        Student fred = studentRepository.save(new Student("Fred", 7.6, startDateTime.plusDays(2), address2))

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
        def first = all[0]
        then:
        all.size() == 3

        when:
        def name = studentViewRepository.findNameById(first.id)
        then:
        name == first.name

        when:
        def active = studentViewRepository.findActiveById(first.id)
        then:
        active

        when:
        def maxAvgGrade = studentViewRepository.findMaxAverageGrade()
        then:
        maxAvgGrade > 9

        when:
        def optStartDateTime = studentViewRepository.findStartDateTimeById(first.id)
        then:
        optStartDateTime.present
        optStartDateTime.get().isAfter(LocalDateTime.now().minusMonths(1))

        when:
        def street = studentViewRepository.findAddressStreetById(first.id)
        then:
        street == first.address.street

        when:
        def allSorted = studentViewRepository.findAll(Sort.of(Sort.Order.asc("name")))
        then:
        allSorted.size() == 3
        allSorted[0].name == "Denis"
        allSorted[1].name == "Fred"
        allSorted[2].name == "Josh"

        when:
        def allPages = studentViewRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.desc("name"))))
        then:
        allPages.totalPages == 2
        allPages.totalSize == 3
        allPages.content.size() == 2
        allPages.content[0].name == "Josh"
        allPages.content[1].name == "Fred"

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

        when:"Test updating single field using custom query"
        // Let's rename the student
        def newStudentName = "New Josh"
        studentViewRepository.updateName(studentName, newStudentName)
        then:
        !studentRepository.findByName(studentName).present

        when:"Test updating using query builder"
        newStudentName = "New Josh - Update"
        studentViewRepository.updateAverageGradeAndName(optJoshStudentView.get().id, 6.2, newStudentName)
        def optStudentView = studentViewRepository.findById(optJoshStudentView.get().id)
        then:
        optStudentView.present
        optStudentView.get().name == newStudentName
        optStudentView.get().averageGrade == 6.2

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

        def address = addressRepository.save(new Address("My Street", "My City"))
        def addressView = AddressView.fromAddress(address)

        newStudentScheduleView.setClazz(studentScheduleClassView)
        ivoneStudentView.setAddress(addressView)
        ivoneStudentView.setSchedule(List.of(newStudentScheduleView))
        peterStudentView.setAddress(addressView)
        peterStudentView.setSchedule(List.of(newStudentScheduleView))
        studentViewRepository.save(ivoneStudentView)
        studentViewRepository.saveAll(Arrays.asList(peterStudentView))

        def optIvoneStudentView = studentViewRepository.findByName(ivoneStudentName)
        def optPeterStudentView = studentViewRepository.findById(peterStudentView.id)
        def clazz = classRepository.findByName(className)

        then:
        optPeterStudentView.present
        optIvoneStudentView.isPresent()
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

    /**
     * Test finding data using view from records created in source tables
     */
    def "save entities and load views"() {
        when:
        def team1 = new Team()
        team1.name = "McLaren"
        team1.points = 90
        teamRepository.save(team1)
        def team2 = new Team()
        team2.name = "Ferrari"
        team2.points = 105
        teamRepository.save(team2)
        def driver1 = new Driver()
        driver1.points = 30
        driver1.name = "Lando Norris"
        driver1.team = team1
        driverRepository.save(driver1)
        def driver2 = new Driver()
        driver2.points = 20
        driver2.name = "Oscar Piastri"
        driver2.team = team1
        driverRepository.save(driver2)
        def driver3 = new Driver()
        driver3.name = "Charles Leclerc"
        driver3.points = 15
        driver3.team = team2
        driverRepository.save(driver3)
        def race = new Race()
        race.name = "Bahrain"
        race.laps = 57
        race.podium = "some random data"
        race.raceDate = new Date(2023, 6, 1)
        raceRepository.save(race)
        def driverRaceMap1 = new DriverRaceMap()
        driverRaceMap1.race = race
        driverRaceMap1.position = 1
        driverRaceMap1.driver = driver1
        driverRaceMapRepository.save(driverRaceMap1)
        def driverRaceMap2 = new DriverRaceMap()
        driverRaceMap2.race = race
        driverRaceMap2.position = 2
        driverRaceMap2.driver = driver2
        driverRaceMapRepository.save(driverRaceMap2)
        def driverRaceMap3 = new DriverRaceMap()
        driverRaceMap3.race = race
        driverRaceMap3.position = 3
        driverRaceMap3.driver = driver3
        driverRaceMapRepository.save(driverRaceMap3)

        def optTeamView1 = teamViewRepository.findById(team1.teamId)
        def optTeamView2 = teamViewRepository.findById(team2.teamId)
        def driverView1 = driverViewRepository.findById(driver1.driverId)
        def driverView3 = driverViewRepository.findById(driver3.driverId)
        then:
        optTeamView1.present
        optTeamView2.present
        driverView1.present
        driverView3.present
    }
}
