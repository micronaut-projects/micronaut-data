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
package io.micronaut.data.processor.visitors


import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindByIdInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class FindSpec extends AbstractDataSpec {

    void "test find by with overlapping property paths 2"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', '''
@javax.persistence.Entity
@javax.persistence.Table(name = "player")
class Player {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy=javax.persistence.GenerationType.IDENTITY)
    private Long id;
    private Team team;
    private String name;
       
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
       
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    public void setTeam(Team team) {
        this.team = team;
    }
    
    public Team getTeam() {
        return team;
    }
}

@javax.persistence.Entity
@javax.persistence.Table(name = "team")
class Team {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy=javax.persistence.GenerationType.IDENTITY)
    private Long id;
    private Integer externalTeamId;
    private String externalTeam;
    private String name;
       
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
               
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
           
    public Integer getExternalTeamId() {
        return externalTeamId;
    }

    public void setExternalTeamId(Integer externalTeamId) {
        this.externalTeamId = externalTeamId;
    }
    
    public String getExternalTeam() {
        return externalTeam;
    }

    public void setExternalTeam(String externalTeam) {
        this.externalTeam = externalTeam;
    }
}

@Repository
interface PlayerRepository extends GenericRepository<Player, Long> {

    Collection<Player> findByName(String name);

    Collection<Player> findByTeamName(String name);

    Collection<Player> findByTeamId(Integer id);

    Collection<Player> findByTeamExternalTeamId(Integer id);

    Collection<Player> findByTeamExternalTeam(String team);
}
''')
        expect:
        beanDefinition != null
    }

    void "test find by with overlapping property paths"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.DeviceInfoRepository', """
@javax.persistence.Entity
class DeviceInfo {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy=javax.persistence.GenerationType.IDENTITY)
    private Long id;

    private String manufacturerDeviceId;

    @javax.persistence.ManyToOne
    @javax.persistence.JoinColumn(name="manufacturer_id")
    public DeviceManufacturer manufacturer;
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getManufacturerDeviceId() {
        return manufacturerDeviceId;
    }

    public void setManufacturerDeviceId(String manufacturerDeviceId) {
        this.manufacturerDeviceId = manufacturerDeviceId;
    }
        
    public DeviceManufacturer getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(DeviceManufacturer manufacturerDeviceId) {
        this.manufacturer = manufacturer;
    }        
}

@javax.persistence.Entity
class DeviceManufacturer {
    
    @javax.persistence.Id
    private Long id;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
}

@Repository
interface DeviceInfoRepository extends GenericRepository<DeviceInfo, Long> {

    DeviceInfo findByManufacturerDeviceId(String id);
}

""")
        def findByManufacturerDeviceId = beanDefinition.getRequiredMethod("findByManufacturerDeviceId", String)

        expect:
        findByManufacturerDeviceId != null
    }

    void "test find order by"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', """

${playerTeamModel()}

@Repository
interface PlayerRepository extends GenericRepository<Player, Integer> {

    Collection<Player> findAllOrderByName();
    Collection<Player> findAllOrderByNameAsc();
    Collection<Player> findAllOrderByNameDesc();
}

""")
        def findAllOrderByName = beanDefinition.getRequiredMethod("findAllOrderByName")
        def findAllOrderByNameAsc = beanDefinition.getRequiredMethod("findAllOrderByNameAsc")
        def findAllOrderByNameDesc = beanDefinition.getRequiredMethod("findAllOrderByNameDesc")

        expect:
        findAllOrderByName.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ ORDER BY player_.name ASC'
        findAllOrderByNameAsc.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ ORDER BY player_.name ASC'
        findAllOrderByNameDesc.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ ORDER BY player_.name DESC'
    }

    void "test find by association id - singled ended"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', """

${playerTeamModel()}

@Repository
interface PlayerRepository extends GenericRepository<Player, Integer> {

    Collection<Player> findByTeamName(String name);

    Collection<Player> findByTeamId(Integer id);
}

""")
        def findByTeamName = beanDefinition.getRequiredMethod("findByTeamName", String)
        def findByTeamId = beanDefinition.getRequiredMethod("findByTeamId", Integer)

        expect:
        findByTeamName.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ WHERE (player_.team.name = :p1)'
        findByTeamId.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ WHERE (player_.team.id = :p1)'
    }

    void "test find method match"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    Person find(Long id);
    
    Person find(Long id, String name);
    
    Person findById(Long id);
    
    Iterable<Person> findByIds(Iterable<Long> ids);
}
""")
        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

        when: "the list method is retrieved"

        def findMethod = beanDefinition.getRequiredMethod("find", Long)
        def findMethod2 = beanDefinition.getRequiredMethod("find", Long, String)
        def findMethod3 = beanDefinition.getRequiredMethod("findById", Long)
        def findByIds = beanDefinition.getRequiredMethod("findByIds", Iterable.class)

        def findAnn = findMethod.synthesize(DataMethod)
        def findAnn2 = findMethod2.synthesize(DataMethod)
        def findAnn3 = findMethod3.synthesize(DataMethod)
        def findByIdsAnn = findByIds.synthesize(DataMethod)

        then:"it is configured correctly"
        findAnn.interceptor() == FindByIdInterceptor
        findAnn3.interceptor() == FindByIdInterceptor
        findAnn2.interceptor() == FindOneInterceptor
        findByIdsAnn.interceptor() == FindAllInterceptor
        findByIds.synthesize(Query).value() == "SELECT $alias FROM io.micronaut.data.model.entities.Person AS $alias WHERE (${alias}.id IN (:p1))"
    }

    String playerTeamModel() {
        '''
@MappedEntity
class Player {

    @GeneratedValue
    @Id
    private Integer id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Team team;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
        
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    
    public void setTeam(Team team) {
        this.team = team;
    }
    
    public Team getTeam() {
        return this.team;
    }
}

@MappedEntity
class Team {
    @GeneratedValue
    @Id
    private Integer id;
    private String name;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }    
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}'''
    }
}
