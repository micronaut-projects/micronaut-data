/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.sql


import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Shared

import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterAutoPopulatedProperties
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class CompositePrimaryKeySpec extends AbstractDataSpec {

    @Shared SourcePersistentEntity entity

    def setupSpec() {
        entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
    }

    void "test compile repository 2"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface CompanyRepository extends io.micronaut.data.tck.repositories.CompanyRepository {
}
""")
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()

        expect:"The repository compiles"
        repository != null
        getParameterPropertyPaths(updateMethod) == ["name", "lastUpdated", "myId"] as String[]
        getParameterAutoPopulatedProperties(updateMethod) == ['', "lastUpdated", ""] as String[]
    }

    void "test compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositePrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface ProjectRepository extends CrudRepository<Project, ProjectId>{
    void update(@Id ProjectId id, @Parameter("name") String name);
    List<Project> findByDepartmentId(Long departmentId);
//    List<Project> findByProjectId(Long projectId);
}
""")
        def findByIdMethod = repository.findPossibleMethods("findById").findFirst().get()
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def findByDepartmentIdMethod = repository.findPossibleMethods("findByDepartmentId").findFirst().get()
//        def findByProjectIdMethod = repository.findPossibleMethods("findByProjectId").findFirst().get()

        expect:"The repository compiles"
        repository != null
        getDataTypes(findByIdMethod) == [DataType.INTEGER, DataType.INTEGER]
        getParameterBindingIndexes(findByIdMethod) == ["0", "0"]
        getParameterPropertyPaths(findByIdMethod) == ["projectId.departmentId", "projectId.projectId"] as String[]
        getParameterBindingPaths(findByIdMethod) == ["departmentId", "projectId"] as String[]

        and:
        getParameterBindingIndexes(updateMethod) == ["1", "0", "0"]
        getParameterPropertyPaths(updateMethod) == ["name", "projectId.departmentId", "projectId.projectId"] as String[]
        getParameterBindingPaths(updateMethod) == ["", "departmentId", "projectId"] as String[]

        and:
        getParameterBindingIndexes(findByDepartmentIdMethod) == ["0"]
        getParameterPropertyPaths(findByDepartmentIdMethod) == ["projectId.departmentId"] as String[]
        getParameterBindingPaths(findByDepartmentIdMethod) == [""] as String[]
    }

    void "test compile repo with composite key relations"() {
        given:
        def repository = buildRepository('test.UserRoleRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositeRelationPrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {

    UserRole save(UserRole entity);

    default UserRole save(User user, Role role) {
        return save(new UserRole(new UserRoleId(user, role)));
    }

    void deleteById(UserRoleId id);

    default void delete(User user, Role role) {
        deleteById(new UserRoleId(user, role));
    }

    int count();

    Iterable<Role> findRoleByUser(User user);
}
""")

        when:
        def findRoleByUserMethod = repository.findPossibleMethods("findRoleByUser").findFirst().get()

        then:
        getQuery(findRoleByUserMethod) == 'SELECT user_role_id_role_."id",user_role_id_role_."name" FROM "user_role" user_role_ INNER JOIN "role" user_role_id_role_ ON user_role_."role_id"=user_role_id_role_."id" WHERE (user_role_."user_id" = ?)'
        getParameterBindingIndexes(findRoleByUserMethod) == ["0"]
        getParameterPropertyPaths(findRoleByUserMethod) == ["id.user.id"] as String[]
        getParameterBindingPaths(findRoleByUserMethod) == ["id"] as String[]

        when:
        def deleteByIdMethod = repository.findPossibleMethods("deleteById").findFirst().get()

        then:
        getQuery(deleteByIdMethod) == 'DELETE  FROM "user_role"  WHERE ("user_id" = ? AND "role_id" = ?)'
        getParameterBindingIndexes(deleteByIdMethod) == ["0", "0"]
        getParameterPropertyPaths(deleteByIdMethod) == ["id.user.id", "id.role.id"] as String[]
        getParameterBindingPaths(deleteByIdMethod) == ["user", "role"] as String[]
    }

    void "test compile repo with composite key relations2"() {
        given:
        def repository = buildRepository('test.EntityWithIdClassRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.tck.entities.*;
import io.micronaut.data.repository.CrudRepository;

@Repository
@RepositoryConfiguration(queryBuilder=JpaQueryBuilder.class, implicitQueries = true, namedParameters = true)
@io.micronaut.context.annotation.Executable
interface EntityWithIdClassRepository extends CrudRepository<EntityWithIdClass, EntityIdClass> {
    List<EntityWithIdClass> findById1(Long id1);
    List<EntityWithIdClass> findById2(Long id2);
    @Override long count();
    long countDistinct();
    long countDistinctName();
}
""")

        when:
        def findByIdMethod = repository.findPossibleMethods("findById").findFirst().get()
        def findById1Method = repository.findPossibleMethods("findById1").findFirst().get()
        def findById2Method = repository.findPossibleMethods("findById2").findFirst().get()
        def countMethod = repository.findPossibleMethods("count").findFirst().get()
        def countDistinctMethod = repository.findPossibleMethods("countDistinct").findFirst().get()
        def countDistinctNameMethod = repository.findPossibleMethods("countDistinctName").findFirst().get()

        then:
        getQuery(findByIdMethod) == 'SELECT entityWithIdClass_ FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_ WHERE (entityWithIdClass_.id1 = :p1 AND entityWithIdClass_.id2 = :p2)'
        getParameterBindingIndexes(findByIdMethod) == ["0", "0"]
        getParameterPropertyPaths(findByIdMethod) == ["id1", "id2"] as String[]
        getParameterBindingPaths(findByIdMethod) == ["id1", "id2"] as String[]

        and:
        getParameterBindingIndexes(findById1Method) == ["0"]
        getParameterPropertyPaths(findById1Method) == ["id1"] as String[]
        getParameterBindingPaths(findById1Method) == [""] as String[]

        getParameterBindingIndexes(findById2Method) == ["0"]
        getParameterPropertyPaths(findById2Method) == ["id2"] as String[]
        getParameterBindingPaths(findById2Method) == [""] as String[]

        getQuery(countMethod) == 'SELECT COUNT(entityWithIdClass_) FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_'
        getQuery(countDistinctMethod) == 'SELECT COUNT(DISTINCT(entityWithIdClass_)) FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_'
        getQuery(countDistinctNameMethod) == 'SELECT COUNT(DISTINCT(entityWithIdClass_.name)) FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_'
    }

    void "test create table"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        when:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.MYSQL)
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE `project` (`department_id` INT NOT NULL,`project_id` INT AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL, PRIMARY KEY(`department_id`,`project_id`));'
    }

    void "test build insert"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        when:
        def builder = new SourcePersistentEntityCriteriaBuilderImpl(null)
        def query = builder.createCriteriaInsert(entity)
        def sql = query.build(new SqlQueryBuilder()).query

        then:
        sql == 'INSERT INTO "project" ("name","department_id") VALUES (?,?)'
    }

    void "test build query"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        def builder = new SourcePersistentEntityCriteriaBuilderImpl(null)
        def query = builder.createQuery()
        def root = query.from(entity)

        when:
        def sql = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder()).query

        then:
        sql == 'SELECT project_."department_id",project_."project_id",project_."name" FROM "project" project_ WHERE (project_."department_id" = ? AND project_."project_id" = ?)'
    }

    void "test build query projection"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        def builder = new SourcePersistentEntityCriteriaBuilderImpl(null)

        when:
        def query1 = builder.createQuery()
        def root1 = query1.from(entity)
        def sql1 = query1.select(root1.get(entity.identity.name)).where(builder.equal(root1.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder()).query

        then:
        sql1.startsWith('SELECT project_."department_id",project_."project_id"')

        when:"an id project ins used"
        def query2 = builder.createQuery()
        def root2 = query2.from(entity)
        def sql2 = query2.select(root2.get(entity.identity.name)).where(builder.equal(root2.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder()).query

        then:
        sql2.startsWith('SELECT project_."department_id",project_."project_id"')
    }

    void "test nested embedded id property of an association resolves to the owning table"() {
        given:"an association whose target has an embedded id containing a further embedded"
        def repository = buildRepository('test.ParcelRepository', """
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
class Shipment {
    @EmbeddedId
    private ShipmentId shipmentId;
    private String name;

    public ShipmentId getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(ShipmentId shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

@Embeddable
class ShipmentId {
    @Embedded
    private ShipmentRegion region;
    private int number;

    public ShipmentRegion getRegion() {
        return region;
    }

    public void setRegion(ShipmentRegion region) {
        this.region = region;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}

@Embeddable
class ShipmentRegion {
    private String country;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}

@Entity
class Parcel {
    @Id
    @GeneratedValue
    private Long id;
    @ManyToOne
    private Shipment shipment;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }
}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface ParcelRepository extends GenericRepository<Parcel, Long> {

    List<Parcel> findByShipmentShipmentIdRegionCountry(String country);
}
""")

        when:"the query filters on the nested leaf of that embedded id"
        def method = repository.findPossibleMethods("findByShipmentShipmentIdRegionCountry").findFirst().get()
        def query = getQuery(method)

        then:"the leaf resolves to the foreign key column on the owning table, not to the join alias"
        // Accessibility has to agree with traversal, which descends the whole embedded chain.
        // Matching only the identity's top level made the predicate read parcel_shipment_."country".
        query == 'SELECT parcel_."id",parcel_."shipment_country",parcel_."shipment_number" FROM "parcel" parcel_ INNER JOIN "shipment" parcel_shipment_ ON parcel_."shipment_country"=parcel_shipment_."country" AND parcel_."shipment_number"=parcel_shipment_."number" WHERE (parcel_."shipment_country" = ?)'
    }
}
