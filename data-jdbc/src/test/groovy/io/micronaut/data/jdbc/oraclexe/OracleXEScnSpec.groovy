package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleXEScnSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Memoized
    ArticleRepository getArticleRepository() {
        return context.getBean(ArticleRepository)
    }

    void "test optimistic lock with ORA_ROWSCN"() {
        when:
        def article = articleRepository.save(new Article(name: "Sneakers", price: 119.99))
        def found = articleRepository.findById(article.id)
        then:
        found.present
        def foundArticle = found.get()
        foundArticle.oraRowscn
        when:
        foundArticle.oraRowscn = foundArticle.oraRowscn + 1
        articleRepository.update(foundArticle)
        then:
        def ex = thrown(OptimisticLockException)
        ex.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
    }

}

@MappedEntity
class Article {
    @Id
    @GeneratedValue
    Long id

    String name

    Double price

    @Version(systemField = true)
    Long oraRowscn
}
@JdbcRepository(dialect = Dialect.ORACLE)
interface ArticleRepository extends CrudRepository<Article, Long> {
}
