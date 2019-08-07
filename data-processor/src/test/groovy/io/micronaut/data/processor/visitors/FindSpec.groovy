/*
 * Copyright 2017-2019 original authors
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

    void "test find by association id - singled ended"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', '''

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
}

@Repository
interface PlayerRepository extends GenericRepository<Player, Integer> {

    Collection<Player> findByTeamName(String name);

    Collection<Player> findByTeamId(Integer id);
}

''')
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

}
