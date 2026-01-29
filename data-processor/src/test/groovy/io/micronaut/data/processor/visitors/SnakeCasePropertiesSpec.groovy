/*
 * Copyright 2017-2026 original authors
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

import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class SnakeCasePropertiesSpec extends AbstractDataSpec {

    void "snake_case leaf property and camelCase alias both resolve"() {
        given:
        def repository = buildRepository('test.CBookRepository', """
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.GenericRepository;

@MappedEntity
class CAuthor {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@MappedEntity
class CBook {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    private int total_pages;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private CAuthor author;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getTotal_pages() { return total_pages; }
    public void setTotal_pages(int total_pages) { this.total_pages = total_pages; }

    public CAuthor getAuthor() { return author; }
    public void setAuthor(CAuthor author) { this.author = author; }
}

@Repository
interface CBookRepository extends GenericRepository<CBook, Long> {

    CBook find_by_total_pages(int pages);

    CBook findByTotalPages(int pages);

    java.util.List<CBook> find_by_author_name(String name);

    java.util.List<CBook> findByAuthorName(String name);
}
""")
        when:
        def q1 = getQuery(repository.getRequiredMethod("find_by_total_pages", int))
        def q2 = getQuery(repository.getRequiredMethod("findByTotalPages", int))
        then:
        q1
        q2
        q1 == q2
        when:
        def q3 = getQuery(repository.getRequiredMethod("find_by_author_name", String))
        def q4 = getQuery(repository.getRequiredMethod("findByAuthorName", String))
        then:
        q3
        q4
        q3 == q4
    }

    @Unroll
    void "snake_case finders compile for '#name'"() {
        given:
        def repository = buildRepository('test.CBookRepository2', """
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.GenericRepository;

@MappedEntity class CAuthor2 { @Id @GeneratedValue private Long id; private String author_name;
public Long getId(){return id;} public void setId(Long id){this.id=id;}
public String getAuthor_name(){return author_name;} public void setAuthor_name(String n){this.author_name=n;}}

@MappedEntity class CBook2 { @Id @GeneratedValue private Long id; private String title; private int total_pages; @Relation(Relation.Kind.MANY_TO_ONE) private CAuthor2 author;
public Long getId(){return id;} public void setId(Long id){this.id=id;}
public String getTitle(){return title;} public void setTitle(String t){this.title=t;}
public int getTotal_pages(){return total_pages;} public void setTotal_pages(int p){this.total_pages=p;}
public CAuthor2 getAuthor(){return author;} public void setAuthor(CAuthor2 a){this.author=a;}}

@Repository
interface CBookRepository2 extends GenericRepository<CBook2, Long> {
    java.util.List<CBook2> ${name}(${name.contains('total_pages') || name.contains('TotalPages') ? 'int' : 'String'} a);
}
""")
        expect:
        repository.findPossibleMethods(name).findFirst().isPresent()
        where:
        name << [
            'find_by_total_pages',
            'findByTotalPages',
            'find_by_author_name',
            'findByAuthorName'
        ]
    }
}
