package io.micronaut.data.hibernate.reactive


import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.GenericRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table

@MicronautTest(transactional = false)
class DtoAssociationsSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    @Shared
    GroupRepository groupRepository
    @Inject
    @Shared
    UserRepository userRepository

    def setupSpec() {
        def group1 = groupRepository.save(new Group(id: 1, name: "Group 1")).block()
        def group2 = groupRepository.save(new Group(id: 2, name: "Group 2")).block()

        userRepository.save(new User(id: 1, username: "john", something: "blabla", groups: [group1, group2])).block()
        userRepository.save(new User(id: 2, username: "jane", something: "blabla")).block()
    }

    def "basic dtos"() {
        when:
            def dtos = userRepository.listAll().collectList().block()
        then:
            dtos.size() == 2
    }

    // Hibernate tuple projections is equivalent of resultset rows so we get 3 result instead of 2
    def "basic dtos with groups"() {
        when:
            def dtos = userRepository.findAll().collectList().block()
        then:
            dtos.size() == 3
    }

    def "simple dto"() {
        when:
            def simple = userRepository.list().collectList().block()
        then:
            simple.size() == 2
    }

}

@Repository
interface UserRepository extends GenericRepository<User, Long> {

    Flux<SomeDtoWithGroup> listAll();

    @Join(value = "groups", type = Join.Type.LEFT)
    Flux<SomeDtoWithGroup> findAll();

    Mono<Void> save(User user);

    Flux<SomeDto> list();
}

@Repository
interface GroupRepository extends ReactorCrudRepository<Group, Long> {
}

@Introspected
class SomeDto {
    String username
    String something
}

@Introspected
class SomeDtoWithGroup {
    @Id
    Long id
    String username
    String something
    @ManyToMany
    Set<Group> groups

    SomeDtoWithGroup(String username, String something, @Nullable Set<Group> groups) {
        this.username = username
        this.something = something
        this.groups = groups
    }
}

@Entity
@Table(name = "app_user")
class User {
    @Id
    Long id
    String username
    String something
    @ManyToMany
    Set<Group> groups
}

@Entity
@Table(name = "app_group")
class Group {
    @Id
    Long id
    String name
}
