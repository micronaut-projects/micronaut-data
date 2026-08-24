package io.micronaut.data.jdbc.oraclexe.etag

import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleETagVersionSpec extends Specification implements OracleTestPropertyProvider {

    @Memoized
    ETagBookExplicitRepository getExplicitRepo() {
        context.getBean(ETagBookExplicitRepository)
    }

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Memoized
    ETagBookRepository getRepo() {
        context.getBean(ETagBookRepository)
    }

    void "ETag is computed on read and used for optimistic locking (implicit via @ETaggable)"() {
        when: "save a new book"
        def b = repo.save(new ETagBook(null, "Initial", new ETagBook.BookDetails(200, 10), null))
        def opt = repo.findById(b.id())
        then:
        opt.present
        opt.get().etag() != null

        when: "optimistic update succeeds with fresh etag"
        def fresh = repo.findById(b.id()).get()
        def etag1 = fresh.etag()
        fresh = new ETagBook(fresh.id(), "Updated-1", fresh.bookDetails(), etag1)
        repo.update(fresh)
        def afterUpdate = repo.findById(b.id()).get()
        def etag2 = afterUpdate.etag()
        then:
        etag2 != null
        etag2 != etag1

        when: "optimistic update fails with stale etag"
        def stale = new ETagBook(b.id(), "Updated-2", new ETagBook.BookDetails(201, 20), etag1)
        repo.update(stale)
        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }

    void "ETag is computed on read and used for optimistic locking (explicit @ETagValue)"() {
        when: "save a new book"
        def b = explicitRepo.save(
                new ETagBookExplicit(null, "Initial", "notes-1", new ETagBookExplicit.BookDetails(200, 10), null)
        )
        def opt = explicitRepo.findById(b.id())
        then:
        opt.present
        opt.get().etag() != null

        when: "optimistic update succeeds with fresh etag"
        def fresh = explicitRepo.findById(b.id()).get()
        def etag1 = fresh.etag()
        fresh = new ETagBookExplicit(fresh.id(), "Updated-1", fresh.notes(), fresh.bookDetails(), etag1)
        explicitRepo.update(fresh)
        def afterUpdate = explicitRepo.findById(b.id()).get()
        def etag2 = afterUpdate.etag()
        then:
        etag2 != null
        etag2 != etag1

        when: "optimistic update fails with stale etag"
        def stale = new ETagBookExplicit(
                b.id(), "Updated-2", fresh.notes(), new ETagBookExplicit.BookDetails(201, 20), etag1
        )
        explicitRepo.update(stale)
        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }

    void "updating non ETag input does not change ETag"() {
        when:
        def b = explicitRepo.save(
                new ETagBookExplicit(null, "Initial", "notes-1", new ETagBookExplicit.BookDetails(200, 10), null)
        )
        def fresh = explicitRepo.findById(b.id()).get()
        def etag1 = fresh.etag()

        explicitRepo.update(new ETagBookExplicit(fresh.id(), fresh.title(), "notes-2", fresh.bookDetails(), etag1))
        def afterUpdate = explicitRepo.findById(b.id()).get()

        then:
        afterUpdate.notes() == "notes-2"
        afterUpdate.etag() == etag1
    }

    void "updating excluded implicit ETag input does not change ETag"() {
        when:
        def b = repo.save(new ETagBook(null, "Initial", new ETagBook.BookDetails(200, 10), null))
        def fresh = repo.findById(b.id()).get()
        def etag1 = fresh.etag()

        repo.update(new ETagBook(fresh.id(), fresh.title(), new ETagBook.BookDetails(fresh.bookDetails().pages(), 11), etag1))
        def afterUpdate = repo.findById(b.id()).get()

        then:
        afterUpdate.bookDetails().chapters() == 11
        afterUpdate.etag() == etag1
    }

    void "delete fails with stale ETag"() {
        given:
        def b = repo.save(new ETagBook(null, "Initial", new ETagBook.BookDetails(200, 10), null))
        def stale = repo.findById(b.id()).get()
        def updated = new ETagBook(stale.id(), "Updated", stale.bookDetails(), stale.etag())
        repo.update(updated)

        when:
        repo.delete(stale)

        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }
}
