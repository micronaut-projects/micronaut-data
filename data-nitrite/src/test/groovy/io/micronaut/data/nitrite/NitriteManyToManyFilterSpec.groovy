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

}
