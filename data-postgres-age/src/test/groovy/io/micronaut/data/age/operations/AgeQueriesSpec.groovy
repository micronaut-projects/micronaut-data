package io.micronaut.data.age.operations

import spock.lang.Specification

class AgeQueriesSpec extends Specification {

    void "read the columns of the last RETURN clause"() {
        expect:
        AgeQueries.returnColumns(cypher) == columns

        where:
        cypher                                                                                     | columns
        'MATCH (n:Movie) RETURN n'                                                                 | ['n']
        'MATCH (n:Movie) RETURN n.title AS title, n.released AS released ORDER BY n.title LIMIT 2' | ['title', 'released']
        'MATCH (n:Movie) RETURN count(n) AS result'                                                | ['result']
        'MATCH (n:Movie) RETURN DISTINCT n'                                                        | ['n']
        'MATCH (n) WITH n, collect({rel: r, node: j}) AS j_roles RETURN n, j_roles'                | ['n', 'j_roles']
        'MATCH (s)-[r]-(e) RETURN r, startNode(r) AS f, endNode(r) AS t'                           | ['r', 'f', 't']
        "MATCH (m) WHERE m.title = 'RETURN x, y' RETURN m"                                         | ['m']
        'MATCH (m {title: $title}) SET m.votes = 1'                                                | []
        'MATCH (n) RETURN n.title'                                                                 | ['n.title']
        'MATCH (n) RETURN n AS `the node`'                                                         | ['the node']
    }

    void "wrap a query in a cypher() call"() {
        expect:
        AgeQueries.wrap("movies", "MATCH (n) RETURN n", true, 1) ==
            'SELECT * FROM ag_catalog.cypher(\'movies\', $$ MATCH (n) RETURN n $$, ?) AS (c0 ag_catalog.agtype)'
        AgeQueries.wrap("movies", "MATCH (n) DETACH DELETE n", false, 0) ==
            'SELECT * FROM ag_catalog.cypher(\'movies\', $$ MATCH (n) DETACH DELETE n $$) AS (c0 ag_catalog.agtype)'
        AgeQueries.wrap("g", "MATCH (n) RETURN n, n.a AS a", false, 2).endsWith('AS (c0 ag_catalog.agtype, c1 ag_catalog.agtype)')
    }

    void "reject unsafe input"() {
        when:
        AgeQueries.wrap("movies; DROP TABLE x", "MATCH (n) RETURN n", false, 1)

        then:
        thrown(IllegalArgumentException)

        when:
        AgeQueries.wrap("movies", 'RETURN $$', false, 1)

        then:
        thrown(IllegalArgumentException)
    }
}
