package io.micronaut.data.processor.visitors


import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.intercept.annotation.DataMethod

class ParameterTypeDefSpec extends AbstractDataSpec {

    void "test parameter type def resolved from entity"() {
        given:
        def repository = buildRepository('test.PersonRepository', '''
import java.util.UUID;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Repository
interface PersonRepository extends io.micronaut.data.repository.GenericRepository<Person, UUID> {
    List<Person> findByIdIn(List<UUID> id);
    
     
    void deleteAll(Iterable<Person> entities);
}

@MappedEntity
class Person {
    @Id
    @TypeDef(type = DataType.OBJECT)
    protected UUID id = UUID.randomUUID();
    
    public UUID getId() {
        return id;
    }    
    
    public void setId(UUID id) {
        this.id = id;
    }
}
''')


        expect:
        repository.getRequiredMethod("findByIdIn", List)
                .getAnnotationMetadata()
                .getAnnotation(DataMethod)
                .stringValues(DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS)[0] == 'OBJECT'

        repository.getRequiredMethod("deleteAll", Iterable)
                .getAnnotationMetadata()
                .getAnnotation(DataMethod)
                .stringValues(DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS)[0] == 'ENTITY'


    }

    void "test parameter type entities"() {
        given:
        def repository = buildRepository('test.PersonRepository', '''
import java.util.UUID;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Repository
interface PersonRepository extends io.micronaut.data.repository.GenericRepository<Person, Long> {
    void deleteAll(Iterable<Person> entities);
}

@MappedEntity
class Person {
    @Id
    protected Long id;
    
    public Long getId() {
        return id;
    }    
    
    public void setId(Long id) {
        this.id = id;
    }
}
''')


        expect:

        repository.getRequiredMethod("deleteAll", Iterable)
                .getAnnotationMetadata()
                .getAnnotation(DataMethod)
                .stringValues(DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS)[0] == 'ENTITY'


    }
}
