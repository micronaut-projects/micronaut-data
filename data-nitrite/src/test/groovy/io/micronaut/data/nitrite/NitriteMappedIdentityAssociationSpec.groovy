package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.MappedClub
import io.micronaut.data.nitrite.model.MappedMember
import io.micronaut.data.nitrite.repository.MappedClubRepository
import io.micronaut.data.nitrite.repository.MappedMemberRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NitriteMappedIdentityAssociationSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])

    @Shared
    MappedClubRepository clubRepository = context.getBean(MappedClubRepository)

    @Shared
    MappedMemberRepository memberRepository = context.getBean(MappedMemberRepository)

    def setup() {
        clubRepository.deleteAll()
        memberRepository.deleteAll()
    }

    void "inverse many-to-many filtering works with mapped identity properties"() {
        given:
        def club = clubRepository.save(new MappedClub("Mapped Club"))
        def member = memberRepository.save(new MappedMember("Alice"))
        club.members.add(member)
        clubRepository.update(club)

        when:
        def members = memberRepository.findByClubsName("Mapped Club")

        then:
        members*.name == ["Alice"]
    }
}
