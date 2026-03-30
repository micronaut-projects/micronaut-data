package io.micronaut.data.document.mongodb.joincollection

import com.mongodb.client.MongoClient
import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoJoinCollectionCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.joincollection']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'false'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates join collection for many-to-many association at startup'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoCollectionsCreator)
        conditions.eventually {
            def collectionNames = mongoClient.getDatabase('test').listCollectionNames().into([])
            assert collectionNames.contains('m2m_student')
            assert collectionNames.contains('m2m_course')
            assert collectionNames.contains('student_course')
        }
    }
}

@MongoRepository
interface JoinCollectionStudentRepository extends CrudRepository<JoinCollectionStudent, String> {
}

@MongoRepository
interface JoinCollectionCourseRepository extends CrudRepository<JoinCollectionCourse, String> {
}

@EqualsAndHashCode(includes = 'id')
@MappedEntity('m2m_student')
class JoinCollectionStudent {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<JoinCollectionCourse> courses
}

@EqualsAndHashCode(includes = 'id')
@MappedEntity('m2m_course')
class JoinCollectionCourse {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = 'courses')
    List<JoinCollectionStudent> students
}
