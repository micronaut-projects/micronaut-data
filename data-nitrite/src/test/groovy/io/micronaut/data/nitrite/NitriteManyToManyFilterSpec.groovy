package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.Club
import io.micronaut.data.nitrite.model.Member
import io.micronaut.data.nitrite.repository.ClubRepository
import io.micronaut.data.nitrite.repository.MemberRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NitriteManyToManyFilterSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run([
        "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
    ])

    @Shared
    ClubRepository clubRepo = context.getBean(ClubRepository)
    @Shared
    MemberRepository memberRepo = context.getBean(MemberRepository)

    def setup() {
        clubRepo.deleteAll()
        memberRepo.deleteAll()
    }

    void "test owner-side MANY_TO_MANY filtering (Club.members.name)"() {
        given:
        def club1 = new Club("Science Club")
        def club2 = new Club("Art Club")
        clubRepo.saveAll([club1, club2])

        def member1 = new Member("Alice")
        def member2 = new Member("Bob")
        memberRepo.saveAll([member1, member2])

        // Add members to clubs
        club1.members.add(member1)
        club1.members.add(member2)
        clubRepo.update(club1)

        club2.members.add(member2)
        clubRepo.update(club2)

        when:
        def scienceClubMembers = clubRepo.findByMembersName("Alice")

        then:
        scienceClubMembers.size() == 1
        scienceClubMembers[0].name == "Science Club"

        when:
        def bothClubsMembers = clubRepo.findByMembersName("Bob")

        then:
        bothClubsMembers.size() == 2
        bothClubsMembers.collect { it.name }.sort() == ["Art Club", "Science Club"]
    }

    void "test join-fetching an inverse MANY_TO_MANY for more than one owner"() {
        given: "two members, one of them in both clubs"
        def club1 = new Club("Science Club")
        def club2 = new Club("Art Club")
        clubRepo.saveAll([club1, club2])

        def alice = new Member("Alice")
        def bob = new Member("Bob")
        memberRepo.saveAll([alice, bob])

        club1.members.addAll([alice, bob])
        clubRepo.update(club1)
        club2.members.add(bob)
        clubRepo.update(club2)

        when: "one owner is join-fetched, which bounds the back-reference with an equality"
        def single = memberRepo.findByName("Alice")

        then:
        single.present
        single.get().clubs.collect { it.name } == ["Science Club"]

        when: "two owners are join-fetched, which bounds the same back-reference with an IN"
        def all = memberRepo.findAllOrderByName()

        then: "the IN matches the club's member array element-wise, exactly as the equality did"
        all.collect { it.name } == ["Alice", "Bob"]
        all.find { it.name == "Alice" }.clubs.collect { it.name } == ["Science Club"]
        all.find { it.name == "Bob" }.clubs.collect { it.name }.sort() == ["Art Club", "Science Club"]
    }

}
