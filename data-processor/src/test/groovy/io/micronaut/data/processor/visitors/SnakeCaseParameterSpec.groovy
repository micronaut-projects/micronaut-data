package io.micronaut.data.processor.visitors

import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class SnakeCaseParameterSpec extends AbstractDataSpec {

    void "snake_case parameter names resolve to properties"() {
        given:
        def repository = buildRepository('test.ParamRepo', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

@Repository
interface ParamRepo extends GenericRepository<BookX, Long> {
    java.util.List<BookX> findByTotalPages(int total_pages);
    java.util.List<BookX> find_by_total_pages(int total_pages);
}

@io.micronaut.data.annotation.MappedEntity
class BookX {
    @io.micronaut.data.annotation.Id
    @io.micronaut.data.annotation.GeneratedValue
    private Long id;
    private int total_pages; // snake_case entity property
    private String title;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public int getTotal_pages(){return total_pages;} public void setTotal_pages(int v){this.total_pages=v;}
    public String getTitle(){return title;} public void setTitle(String t){this.title=t;}
}
""")
        when:
        def m1 = repository.getRequiredMethod("findByTotalPages", int)
        def m2 = repository.getRequiredMethod("find_by_total_pages", int)
        then:
        getQuery(m1)
        getQuery(m2)
        getQuery(m1) == getQuery(m2)
    }

    @Unroll
    void "snake_case param maps for '#name'"() {
        given:
        def repository = buildRepository('test.ParamRepo2', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

@Repository
interface ParamRepo2 extends GenericRepository<BookY, Long> {
    java.util.List<BookY> ${name}(int total_pages);
}

@io.micronaut.data.annotation.MappedEntity
class BookY {
    @io.micronaut.data.annotation.Id
    @io.micronaut.data.annotation.GeneratedValue
    private Long id;
    private int total_pages;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public int getTotal_pages(){return total_pages;} public void setTotal_pages(int v){this.total_pages=v;}
}
""")
        expect:
        repository.findPossibleMethods(name).findFirst().isPresent()
        where:
        name << [
            'findByTotalPages',
            'find_by_total_pages'
        ]
    }
}
