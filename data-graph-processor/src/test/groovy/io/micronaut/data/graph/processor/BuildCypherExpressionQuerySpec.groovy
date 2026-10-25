package io.micronaut.data.graph.processor

import io.micronaut.inject.BeanDefinition
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Checks the Cypher rendered for finders that use aggregates, functions, null and boolean checks, negations,
 * ignore-case comparisons, sorting and updates.
 */
class BuildCypherExpressionQuerySpec extends AbstractGraphDataSpec {

    @Shared
    BeanDefinition<?> awardRepository

    void setupSpec() {
        awardRepository = buildRepository('test.AwardRepository', """@Node
class Award {
    @Id
    private String name;
    private Integer year;
    private Long points;
    private Double score;
    private boolean active;
    private List<String> tags;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Long getPoints() { return points; }
    public void setPoints(Long points) { this.points = points; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}

@TestGraphRepository
interface AwardRepository extends CrudRepository<Award, String> {
    Double findAvgPoints();
    Long findSumPoints();
    Integer findMinYear();
    Double findMaxScore();
    long countDistinctName();
    List<Award> findByNameIgnoreCase(String name);
    List<Award> findByNameNotEqual(String name);
    List<Award> findByNameNotEqualIgnoreCase(String name);
    List<Award> findByActiveTrue();
    List<Award> findByActiveFalse();
    List<Award> findByNameIsNotNull();
    List<Award> findByNameIsEmpty();
    List<Award> findByNameIsNotEmpty();
    List<Award> findByYearGreaterThanEquals(int year);
    List<Award> findByYearLessThanEquals(int year);
    List<Award> findByNameRegex(String pattern);
    List<String> findDistinctName();
    List<Integer> findYearByName(String name);
    List<Award> findByNameStartsWithOrderByYearAndScoreDesc(String prefix);
    List<Award> findByNameStartsWithIgnoreCase(String prefix);
    List<Award> findByNameEndsWithIgnoreCase(String suffix);
    Award findFirstOrderByScoreDesc();
    long countByActiveTrue();
    void deleteByActiveFalse();
    void updatePoints(@Id String name, Long points);
    void updatePointsByYear(Integer year, Long points);
    List<Award> findByIdIn(List<String> ids);
    List<Award> findByYearIsNull();
    List<Award> findByTagsArrayContains(String tag);
    List<Award> findByScoreGreaterThanAndActiveTrue(Double score);
    List<Award> findByYearNotIn(List<Integer> years);
}
""")
    }

    @Unroll
    void "test query for #method"() {
        expect:
        getQuery(awardRepository.executableMethods.find { it.methodName == method }) == query

        where:
        method                                         | query
        'countByActiveTrue'                            | 'MATCH (n:Award) WHERE n.active = true RETURN count(n) AS result'
        'countDistinctName'                            | 'MATCH (n:Award) RETURN count(DISTINCT n.name) AS result'
        'deleteByActiveFalse'                          | 'MATCH (n:Award) WHERE n.active = false DETACH DELETE n RETURN count(*)'
        'deleteById'                                   | 'MATCH (n:Award) WHERE n.name = \$p0 DETACH DELETE n RETURN count(*)'
        'existsById'                                   | 'MATCH (n:Award) WHERE n.name = \$p0 RETURN true AS result LIMIT 1'
        'findAvgPoints'                                | 'MATCH (n:Award) RETURN avg(n.points) AS result'
        'findByActiveFalse'                            | 'MATCH (n:Award) WHERE n.active = false RETURN n'
        'findByActiveTrue'                             | 'MATCH (n:Award) WHERE n.active = true RETURN n'
        'findByIdIn'                                   | 'MATCH (n:Award) WHERE n.name IN \$p0 RETURN n'
        'findByNameEndsWithIgnoreCase'                 | 'MATCH (n:Award) WHERE toLower(n.name) ENDS WITH toLower(\$p0) RETURN n'
        'findByNameIgnoreCase'                         | 'MATCH (n:Award) WHERE toLower(n.name) = toLower(\$p0) RETURN n'
        'findByNameIsEmpty'                            | 'MATCH (n:Award) WHERE (n.name IS NULL OR size(n.name) = 0) RETURN n'
        'findByNameIsNotEmpty'                         | 'MATCH (n:Award) WHERE (n.name IS NOT NULL AND size(n.name) > 0) RETURN n'
        'findByNameIsNotNull'                          | 'MATCH (n:Award) WHERE n.name IS NOT NULL RETURN n'
        'findByNameNotEqual'                           | 'MATCH (n:Award) WHERE n.name <> \$p0 RETURN n'
        'findByNameNotEqualIgnoreCase'                 | 'MATCH (n:Award) WHERE NOT (toLower(n.name) = toLower(\$p0)) RETURN n'
        'findByNameRegex'                              | 'MATCH (n:Award) WHERE n.name =~ \$p0 RETURN n'
        'findByNameStartsWithIgnoreCase'               | 'MATCH (n:Award) WHERE toLower(n.name) STARTS WITH toLower(\$p0) RETURN n'
        'findByNameStartsWithOrderByYearAndScoreDesc'  | 'MATCH (n:Award) WHERE n.name STARTS WITH \$p0 RETURN n ORDER BY n.year ASC, n.score DESC'
        'findByScoreGreaterThanAndActiveTrue'          | 'MATCH (n:Award) WHERE (n.score > \$p0 AND n.active = true) RETURN n'
        'findByTagsArrayContains'                      | 'MATCH (n:Award) WHERE \$p0 IN n.tags RETURN n'
        'findByYearGreaterThanEquals'                  | 'MATCH (n:Award) WHERE n.year >= \$p0 RETURN n'
        'findByYearIsNull'                             | 'MATCH (n:Award) WHERE n.year IS NULL RETURN n'
        'findByYearLessThanEquals'                     | 'MATCH (n:Award) WHERE n.year <= \$p0 RETURN n'
        'findByYearNotIn'                              | 'MATCH (n:Award) WHERE NOT (n.year IN \$p0) RETURN n'
        'findDistinctName'                             | 'MATCH (n:Award) RETURN DISTINCT n.name AS name'
        'findFirstOrderByScoreDesc'                    | 'MATCH (n:Award) RETURN n ORDER BY n.score DESC LIMIT 1'
        'findMaxScore'                                 | 'MATCH (n:Award) RETURN max(n.score) AS result'
        'findMinYear'                                  | 'MATCH (n:Award) RETURN min(n.year) AS result'
        'findSumPoints'                                | 'MATCH (n:Award) RETURN sum(n.points) AS result'
        'findYearByName'                               | 'MATCH (n:Award) WHERE n.name = \$p0 RETURN n.year AS year'
        'updatePoints'                                 | 'MATCH (n:Award) WHERE n.name = \$p0 SET n.points = \$p1 RETURN count(*)'
        'updatePointsByYear'                           | 'MATCH (n:Award) WHERE n.year = \$p0 SET n.points = \$p1 RETURN count(*)'
    }

    void "test LIKE on an entity without relationships"() {
        when:
        def repository = buildRepository('test.LikeRepository', """
@Node
class Film {
    @Id
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

@TestGraphRepository
interface LikeRepository extends GenericRepository<Film, String> {
    List<Film> findByTitleLike(String pattern);
}
""")

        then:
        getQuery(repository.executableMethods.find { it.methodName == 'findByTitleLike' }) == "MATCH (n:Film) WHERE n.title =~ ('(?s)' + \$p0) RETURN n"
    }
}
