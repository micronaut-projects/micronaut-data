package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Club
import io.micronaut.data.nitrite.model.CriteriaAuthor
import io.micronaut.data.nitrite.model.CriteriaBook
import io.micronaut.data.nitrite.model.Member
import io.micronaut.data.nitrite.repository.ClubRepository
import io.micronaut.data.nitrite.repository.CriteriaAuthorRepository
import io.micronaut.data.nitrite.repository.CriteriaBookRepository
import io.micronaut.data.nitrite.repository.MemberRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteAssociationRegressionSpec extends Specification {

    @Inject
    CriteriaAuthorRepository authorRepository

    @Inject
    CriteriaBookRepository bookRepository

    @Inject
    ClubRepository clubRepository

    @Inject
    MemberRepository memberRepository

    def setup() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
        clubRepository.deleteAll()
        memberRepository.deleteAll()
    }

    void "criteria query can filter across MANY_TO_ONE association path"() {
        given:
        def king = authorRepository.save(new CriteriaAuthor("Stephen King"))
        def rowling = authorRepository.save(new CriteriaAuthor("J.K. Rowling"))
        bookRepository.save(new CriteriaBook("The Stand", king))
        bookRepository.save(new CriteriaBook("Harry Potter", rowling))

        when:
        PredicateSpecification<CriteriaBook> byAuthorName = (root, cb) ->
            cb.equal(root.get("author").get("name"), "Stephen King")
        def results = bookRepository.findAll(byAuthorName)

        then:
        results*.title == ["The Stand"]
    }

    void "@Join can populate MANY_TO_MANY mappedBy association"() {
        given:
        def alice = memberRepository.save(new Member("Alice"))
        def bob = memberRepository.save(new Member("Bob"))

        def chess = new Club("Chess")
        chess.members.add(alice)
        chess.members.add(bob)

        def books = new Club("Books")
        books.members.add(alice)

        clubRepository.saveAll([chess, books])

        when:
        def loaded = memberRepository.findByName("Alice").orElse(null)

        then:
        loaded != null
        loaded.clubs != null
        loaded.clubs*.name as Set == ["Chess", "Books"] as Set
    }
}
