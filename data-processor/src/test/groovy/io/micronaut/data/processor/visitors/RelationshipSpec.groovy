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
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll

class RelationshipSpec extends AbstractDataSpec {

    @Unroll
    void "test one-to-one collection mapping #annotated1 #annotated2"() {
        given:
            def repository = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import java.util.*;
import javax.persistence.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<User, Long> {

    @Join(value = "authority")
    User findById(Long id);
}

@Entity
class Authority {

    @Id
    private Long id;
    private String name;
    $annotated1
    private User user;

    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
}

@Entity
class User {

    @Id
    @GeneratedValue
    private Long id;
    $annotated2
    private Authority authority;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    public Authority getAuthority() {
        return authority;
    }
    
    public void setAuthority(Authority authority) {
        this.authority = authority;
    }
}
""")
            def q = repository.getRequiredMethod("findById", Long)
                    .stringValue(Query).get()

        expect:
            q == query

        where:
            annotated1 << [
                    '@OneToOne',
                    '@OneToOne',
                    '@OneToOne(mappedBy="authority")',
            ]
            annotated2 << [
                    '@OneToOne',
                    '@OneToOne(mappedBy="user")',
                    '@OneToOne',
            ]
            query << [
                    'SELECT user_."id",user_."authority_id",user_authority_."name" AS authority_name,user_authority_."user_id" AS authority_user_id FROM "user" user_ INNER JOIN "authority" user_authority_ ON user_."authority_id"=user_authority_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authority_."id" AS authority_id,user_authority_."name" AS authority_name,user_authority_."user_id" AS authority_user_id FROM "user" user_ INNER JOIN "authority" user_authority_ ON user_."id"=user_authority_."user_id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_."authority_id",user_authority_."name" AS authority_name FROM "user" user_ INNER JOIN "authority" user_authority_ ON user_."authority_id"=user_authority_."id" WHERE (user_."id" = ?)',
            ]
    }

    @Unroll
    void "test many-to-many collection mapping #usersAnnotated #authoritiesAnnotated"() {
        given:
            def repository = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import java.util.*;
import javax.persistence.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<User, Long> {

    @Join(value = "authorities")
    User findById(Long id);
}

@Entity
class Authority {

    @Id
    private Long id;
    private String name;
    $usersAnnotated
    private List<User> users;

    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }
    
}

@Entity
class User {

    @Id
    @GeneratedValue
    private Long id;
    $authoritiesAnnotated
    private Set<Authority> authorities = new HashSet<>();
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }
    
    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }
}
""")
            def q = repository.getRequiredMethod("findById", Long)
                    .stringValue(Query).get()

        expect:
            q == query

        where:
            usersAnnotated << [
                    '@ManyToMany(mappedBy="authorities")',
                    '@ManyToMany',
                    '@ManyToMany',
                    '@ManyToMany(mappedBy="authorities")',
                    '@JoinTable(name="some_users_authorities") @ManyToMany',
                    '@JoinTable(name="some_users_authorities") @ManyToMany',
                    '@ManyToMany',
            ]
            authoritiesAnnotated << [
                    '@ManyToMany',
                    '@ManyToMany(mappedBy="users")',
                    '@ManyToMany',
                    '@JoinTable(name="some_users_authorities") @ManyToMany',
                    '@ManyToMany(mappedBy="users")',
                    '@JoinTable(name="some_users_authorities") @ManyToMany',
                    '@ManyToMany @JoinTable(name = "ua", joinColumns = @JoinColumn(name = "a_id"), inverseJoinColumns = @JoinColumn(name = "u_id"))',
            ]
            query << [
                    // All join tables
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "some_users_authorities" user_authorities_some_users_authorities_ ON user_."id"=user_authorities_some_users_authorities_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_some_users_authorities_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "some_users_authorities" user_authorities_some_users_authorities_ ON user_."id"=user_authorities_some_users_authorities_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_some_users_authorities_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name FROM "user" user_ INNER JOIN "ua" user_authorities_ua_ ON user_."a_id"=user_authorities_ua_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_ua_."authority_id"=user_authorities_."a_id" WHERE (user_."id" = ?)',
            ]
    }

    @Unroll
    void "test many-to-one/one-to-many collection mapping #authorityAnnotated #userAnnotated"() {
        given:
            def repository = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import java.util.*;
import javax.persistence.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<User, Long> {

    @Join(value = "authorities")
    User findById(Long id);
}

@Entity
class Authority {

    @Id
    private Long id;
    private String name;
    $authorityAnnotated
    private User user;

    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    
}

@Entity
class User {

    @Id
    @GeneratedValue
    private Long id;
    $userAnnotated
    private Set<Authority> authorities = new HashSet<>();
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }
    
    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }
}
""")
            def q = repository.getRequiredMethod("findById", Long)
                    .stringValue(Query).get()

        expect:
            q == query

        where:
            authorityAnnotated << [
                    '@ManyToOne',
                    '@ManyToOne',
                    '@JoinColumn(name="a_id", referencedColumnName="u_id") @JoinColumn(name="x_id", referencedColumnName="y_id") @ManyToOne',
                    '@ManyToOne',
            ]
            userAnnotated << [
                    '@OneToMany',
                    '@OneToMany(mappedBy="user")',
                    '@OneToMany(mappedBy="user")',
                    // @JoinColumn is ignored in this case, association requires @JoinTable
                    '@JoinColumn(name="a_id", referencedColumnName="u_id") @JoinColumn(name="x_id", referencedColumnName="y_id") @OneToMany',
            ]
            query << [
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name,user_authorities_."user_id" AS authorities_user_id FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name,user_authorities_."user_id" AS authorities_user_id FROM "user" user_ INNER JOIN "authority" user_authorities_ ON user_."id"=user_authorities_."user_id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name,user_authorities_."user_id" AS authorities_user_id FROM "user" user_ INNER JOIN "authority" user_authorities_ ON user_."u_id"=user_authorities_."a_id" AND user_."y_id"=user_authorities_."x_id" WHERE (user_."id" = ?)',
                    'SELECT user_."id",user_authorities_."id" AS authorities_id,user_authorities_."name" AS authorities_name,user_authorities_."user_id" AS authorities_user_id FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."id" WHERE (user_."id" = ?)',
            ]
    }
}
