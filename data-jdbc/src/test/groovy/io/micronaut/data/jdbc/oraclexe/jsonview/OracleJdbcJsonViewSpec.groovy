package io.micronaut.data.jdbc.oraclexe.jsonview

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.tck.entities.Metadata
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
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
    TeacherViewRepository teacherViewRepository

    @Inject
    ApartmentViewRepository apartmentViewRepository

    @Inject
    BuildingViewRepository buildingViewRepository

    @Inject
    CrocodileViewRepository crocodileViewRepository

    def setup() {
        studentClassRepository.deleteAll()
        classRepository.deleteAll()
        teacherRepository.deleteAll()
        studentRepository.deleteAll()
        apartmentViewRepository.deleteAll()

        Teacher teacherAnna = teacherRepository.save(new Teacher("Mrs. Anna"))
        Teacher teacherJeff = teacherRepository.save(new Teacher("Mr. Jeff"))

        Address address1 = addressRepository.save(new Address("Main Street", "City1"))
        Address address2 = addressRepository.save(new Address("New Street", "City1"))

        def startDateTime = LocalDateTime.now()
        def birthDate = LocalDate.now().minusYears(20)
        Student denis = studentRepository.save(new Student("Denis", birthDate, 8.5, startDateTime.minusDays(1), address1))
        Student josh = studentRepository.save(new Student("Josh", birthDate.minusMonths(3), 9.1, startDateTime, address1))
        Student fred = studentRepository.save(new Student("Fred", birthDate.plusMonths(1), 7.6, startDateTime.plusDays(2), address2))
        Student dimitrije = studentRepository.save(new Student("Dimitrije", birthDate.minusMonths(4), 9.0, startDateTime.minusDays(2), address1))

        Class math = classRepository.save(new Class("Math", "A101", LocalTime.of(10, 00), teacherAnna))
        Class english = classRepository.save(new Class("English", "A102", LocalTime.of(11, 00), teacherJeff))
        Class german = classRepository.save(new Class("German", "A103", LocalTime.of(12, 00), teacherAnna))
        Class serbian = classRepository.save(new Class("Serbian", "A104", LocalTime.of(13, 00), teacherJeff))

        studentClassRepository.save(new StudentClass(denis, math))
        studentClassRepository.save(new StudentClass(josh, math))
        studentClassRepository.save(new StudentClass(fred, math))

        studentClassRepository.save(new StudentClass(denis, german))
        studentClassRepository.save(new StudentClass(josh, english))
        studentClassRepository.save(new StudentClass(fred, german))
        studentClassRepository.save(new StudentClass(dimitrije, serbian))
    }

    @Shared
    Map<java.lang.Class, RuntimePersistentEntity> entities = [:]

    private RuntimePersistentEntity getRuntimePersistentEntity(java.lang.Class type) {
        RuntimePersistentEntity entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(java.lang.Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }

    /**
     * Test finding data using view from records created in source tables
     */
    def "find and update"() {
        when:
        def all = studentViewRepository.findAll()
        def first = all[0]
        then:
        all.size() == 4

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
        allSorted.size() == 4
        allSorted[0].name == "Denis"
        allSorted[1].name == "Dimitrije"
        allSorted[2].name == "Fred"
        allSorted[3].name == "Josh"
        when:
        allSorted = studentViewRepository.findAll(Sort.of(Sort.Order.asc("startDateTime")))
        then:
        allSorted.size() == 4
        allSorted[0].name == "Dimitrije"
        allSorted[1].name == "Denis"
        allSorted[2].name == "Josh"
        allSorted[3].name == "Fred"

        when:
        def allPages = studentViewRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.desc("name"))))
        then:
        allPages.totalPages == 2
        allPages.totalSize == 4
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

        for (def schedule : denisStudentView.getClasses()) {
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

        when:
        denisStudentView = studentViewRepository.findByName("Denis").orElse(null)
        denisStudentView.setActive(false)
        studentViewRepository.update(denisStudentView)
        allSorted = studentViewRepository.findAllOrderByActive()
        then:
        allSorted.size() == 4
        allSorted[0].name == "Denis"
        when:
        def inActives = studentViewRepository.findAllByActive(false)
        def actives = studentViewRepository.findAllByActive(true)
        then:
        inActives.size() == 1
        inActives[0].name == "Denis"
        actives.size() == 3

        when:
        def birthDate = studentViewRepository.findBirthDateById(denisStudentView.id)
        then:
        birthDate == denisStudentView.birthDate
        when:
        allSorted = studentViewRepository.findAllOrderByBirthDate()
        then:
        allSorted.size() == 4
        allSorted[0].name == "Dimitrije_"
        allSorted[1].name == "Josh_"
        allSorted[2].name == "Denis"
        allSorted[3].name == "Fred_"
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
        newJoshStudentView.setMetadata(new Metadata(UUID.randomUUID().toString(), newJoshStudentView.getMetadata().asof()))
        studentViewRepository.update(newJoshStudentView)
        then:
        thrown(OptimisticLockException)

        when:"Optimistic lock exception with invalid ETAG in batch update"
        studentViewRepository.updateAll(List.of(newJoshStudentView))
        then:
        thrown(OptimisticLockException)
    }

    def "insert new"() {
        when:"Test inserting into the view"
        def ivoneStudentView = new StudentView()
        def ivoneStudentName = "Ivone"
        ivoneStudentView.name = ivoneStudentName
        ivoneStudentView.birthDate = LocalDate.now().minusYears(20)

        def peterStudentView = new StudentView()
        def peterStudentName = "Peter"
        peterStudentView.name = peterStudentName
        peterStudentView.birthDate = LocalDate.now().minusYears(20).minusDays(10)

        def newStudentScheduleView = new StudentScheduleSubView()

        def teacherName = "Mrs. Anna"
        def teacherAnna = teacherRepository.findByName(teacherName)
        def className = "Math"
        def teacherView = new TeacherSubView()
        teacherView.setTeacher(teacherAnna.getName())
        teacherView.setTeachID(teacherAnna.getId())

        def classMath = classRepository.findByName(className)
        def studentScheduleClassView = new StudentScheduleClassSubView()
        // By inserting new student class, we can also update class time as class is marked as updatable in the view
        def classTime = classMath.getTime()
        studentScheduleClassView.setTime(classTime.plusMinutes(30))
        studentScheduleClassView.setName(classMath.getName())
        studentScheduleClassView.setClassID(classMath.getId())
        studentScheduleClassView.setRoom(classMath.getRoom())
        studentScheduleClassView.setTeacher(teacherView)

        def address = addressRepository.save(new Address("My Street", "My City"))
        def addressView = AddressSubView.fromAddress(address)

        newStudentScheduleView.setClazz(studentScheduleClassView)
        ivoneStudentView.setAddress(addressView)
        ivoneStudentView.setClasses(List.of(newStudentScheduleView))
        peterStudentView.setAddress(addressView)
        peterStudentView.setClasses(List.of(newStudentScheduleView))
        studentViewRepository.save(ivoneStudentView)
        studentViewRepository.saveAll(Arrays.asList(peterStudentView))

        def optIvoneStudentView = studentViewRepository.findByName(ivoneStudentName)
        def optPeterStudentView = studentViewRepository.findById(peterStudentView.id)
        def clazz = classRepository.findByName(className)

        then:
        optPeterStudentView.present
        optIvoneStudentView.isPresent()
        // And just to validate that saved local time is + 30 minutes from initial class time
        def studentClassTime = optIvoneStudentView.get().getClasses().get(0).getClazz().getTime()
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
        count == 1
    }

    @Unroll
    def "test_dialect_without_json_view_support"() {
        when:
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        PersistentEntity studentViewEntity = getRuntimePersistentEntity(StudentView)
        String[] result = builder.buildCreateTableStatements(studentViewEntity)

        then:
        result.length == 0

        where:
        dialect << [Dialect.H2, Dialect.ANSI, Dialect.MYSQL, Dialect.POSTGRES, Dialect.SQL_SERVER]
    }


    def "embedded object test"() {
        when:
        def crocodile = new CrocodileView(null, "Bob", new Crocodile.Characteristics(10, 11))
        def created = crocodileViewRepository.save(crocodile)

        then:
        created.name() == "Bob"
        created.characteristics().weight() == 10

        when:
        def get = crocodileViewRepository.findById(created.id()).orElse(null)

        then:
        get != null
        get.name() == "Bob"
        get.characteristics().weight() == 10
    }

    def "test_generate_create_student_view"() {
        when:
        Dialect dialect = Dialect.ORACLE
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        PersistentEntity studentViewEntity = getRuntimePersistentEntity(StudentView)
        String[] sql = builder.buildCreateTableStatements(studentViewEntity)
        then:
        sql[0] == "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW student_view AS SELECT JSON {'_id': s.id, 'name': s.name, 'birthDate': s.birth_date, 'averageGrade': s.average_grade, 'startDateTime': s.start_date_time, 'active': s.active, 'classes': [SELECT JSON {'id': sc.id, 'class': (SELECT JSON {'classID': c.id, 'teacher': (SELECT JSON {'teachID': t.id, 'teacher': t.name} FROM TBL_TEACHER t WITH UPDATE INSERT  WHERE c.\"TEACHER_ID\"=t.\"ID\"), 'room': c.room, 'time': c.time, 'name': c.name} FROM TBL_CLASS c WITH UPDATE  WHERE sc.\"CLASS_ID\"=c.\"ID\")} FROM TBL_STUDENT_CLASSES sc WITH UPDATE INSERT DELETE  WHERE s.\"ID\"=sc.\"STUDENT_ID\"], 'address': (SELECT JSON {'addressID': a.id, 'street': a.street, 'city': a.city} FROM TBL_ADDRESS a WITH UPDATE INSERT  WHERE s.\"ADDRESS_ID\"=a.\"ID\")} FROM TBL_STUDENT s WITH UPDATE INSERT DELETE "
    }

    def "test_generate_create_apartment_view"() {
        when:
        Dialect dialect = Dialect.ORACLE
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        PersistentEntity apartmentViewEntity = getRuntimePersistentEntity(ApartmentView)
        String[] sql = builder.buildCreateTableStatements(apartmentViewEntity)
        then:
        sql[0] == "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW apartment_view AS SELECT JSON {'_id': {'buildingId': ap.building_id, 'flatId': ap.flat_id}} FROM TBL_APARTMENT ap WITH UPDATE INSERT DELETE "
    }

    def "test_apartment_view_repository"() {
        when:
        def apartmentId = new ApartmentId(12, 34)
        def apartmentView = new ApartmentView(apartmentId)
        apartmentViewRepository.save(apartmentView)
        def result = apartmentViewRepository.findById(apartmentId)
        then:
        result.present
    }

    def "test_building_view_repository"() {
        when:
        def buildingView = new BuildingView()
        buildingViewRepository.save(buildingView)

        def apartmentId = new ApartmentId(buildingView.getId(), 1)
        def apartment = new ApartmentSubView(apartmentId)
        def apartments = new ArrayList()
        apartments.add(apartment)
        buildingView.setApartments(apartments)

        buildingViewRepository.update(buildingView)
        def result = buildingViewRepository.findById(buildingView.getId())
        then:
        result.present
        result.get().apartments.size() == 1
    }

    def "test_generate_drop_json_vew"() {
        when:
        Dialect dialect = Dialect.ORACLE
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        PersistentEntity studentViewEntity = getRuntimePersistentEntity(StudentView)
        String[] sql = builder.buildDropTableStatements(studentViewEntity)
        then:
        sql[0] == "DROP VIEW " + builder.getTableName(studentViewEntity)
    }

    def "test_teacher_json_view"() {
        when:
        def teacherView = teacherViewRepository.findByName("Mr. Jeff").get()
        teacherView.setName("Mr. Dimitrije")
        teacherViewRepository.update(teacherView)
        def teacherPersistedView = teacherViewRepository.findById(teacherView.teachID).get()
        then:
        teacherPersistedView.name == "Mr. Dimitrije"
        teacherPersistedView.schedule.name.get(0) == "English"
    }

    def "test_generate_create_crocodile_view"() {
        when:
        Dialect dialect = Dialect.ORACLE
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        PersistentEntity crocodileViewEntity = getRuntimePersistentEntity(CrocodileView)
        String[] sql = builder.buildCreateTableStatements(crocodileViewEntity)
        then:
        sql[0] == "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW crocodile_view AS SELECT JSON " +
                "{'_id': crocodile_.id, 'name': crocodile_.name, 'characteristics': " +
                "(JSON {'weight': crocodile_.weight, 'length': crocodile_.length})" +
                "} FROM crocodile crocodile_ WITH UPDATE INSERT DELETE "
    }
}
