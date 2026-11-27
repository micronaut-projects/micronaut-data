package io.micronaut.data.jdbc.search

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.TextSearchDialect
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.runtime.search.SearchCapabilities
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Each datasource registers its own {@link SearchCapabilities}, injected by the datasource name.
 */
class JdbcSearchCapabilitiesBeansSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(datasource("default", "H2") + datasource("other", "ORACLE"))

    private static Map<String, Object> datasource(String name, String dialect) {
        def prefix = "datasources." + name
        return [
                (prefix + ".url")            : "jdbc:h2:mem:capabilities_${name};DB_CLOSE_ON_EXIT=FALSE".toString(),
                (prefix + ".dialect")        : dialect,
                (prefix + ".schema-generate"): "NONE",
                (prefix + ".username")       : "",
                (prefix + ".password")       : "",
                (prefix + ".driverClassName"): "org.h2.Driver",
        ] as Map<String, Object>
    }

    void "an unqualified SearchCapabilities is the bean of the default datasource, which is primary"() {
        expect:
        context.getBeansOfType(SearchCapabilities).size() == 2
        context.getBean(SearchCapabilities).is(context.getBean(SearchCapabilities, Qualifiers.byName("default")))
    }

    void "an unqualified SearchCapabilities is ambiguous with two datasources and none named default"() {
        given:
        def noDefault = ApplicationContext.run(datasource("first", "H2") + datasource("second", "ORACLE"))

        when:
        noDefault.getBean(SearchCapabilities)

        then:
        thrown(NonUniqueBeanException)

        cleanup:
        noDefault.close()
    }

    void "the SearchCapabilities of a datasource is injected by its name and answers by its dialect"() {
        expect:
        context.getBean(SearchCapabilities, Qualifiers.byName("other")).getSearchCapabilities(Book) == TextSearchDialect.forDialect(Dialect.ORACLE).capabilities()
        context.getBean(SearchCapabilities, Qualifiers.byName("default")).getSearchCapabilities(Book).isEmpty()
    }
}
