package io.micronaut.data.age.operations

import io.micronaut.data.graph.model.query.builder.AgeStatements
import spock.lang.Specification

class AgeStatementsSpec extends Specification {

    void "name the result columns after the last RETURN clause"() {
        expect:
        AgeStatements.wrap(cypher).endsWith(columns)

        where:
        cypher                                                                                     | columns
        'MATCH (n:Movie) RETURN n'                                                                 | '("n" ag_catalog.agtype)'
        'MATCH (n:Movie) RETURN n.title AS title, n.released AS released ORDER BY n.title LIMIT 2' | '("title" ag_catalog.agtype, "released" ag_catalog.agtype)'
        'MATCH (n:Movie) RETURN count(n) AS result'                                                | '("result" ag_catalog.agtype)'
        'MATCH (n:Movie) RETURN DISTINCT n'                                                        | '("n" ag_catalog.agtype)'
        'MATCH (n) WITH n, collect({rel: r, node: j}) AS j_roles RETURN n, j_roles'                | '("n" ag_catalog.agtype, "j_roles" ag_catalog.agtype)'
        'MATCH (s)-[r]-(e) RETURN r, startNode(r) AS f, endNode(r) AS t'                           | '("r" ag_catalog.agtype, "f" ag_catalog.agtype, "t" ag_catalog.agtype)'
        "MATCH (m) WHERE m.title = 'RETURN x, y' RETURN m"                                         | '("m" ag_catalog.agtype)'
        'MATCH (m {title: $title}) SET m.votes = 1'                                                | '("result" ag_catalog.agtype)'
        'MATCH (n) RETURN n.title'                                                                 | '("n.title" ag_catalog.agtype)'
        'MATCH (n) RETURN n AS `the node`'                                                         | '("the node" ag_catalog.agtype)'
        'MATCH (n) RETURN n.limit AS limit, n.order AS order, n.skip, $skip AS s ORDER BY n.order' | '("limit" ag_catalog.agtype, "order" ag_catalog.agtype, "n.skip" ag_catalog.agtype, "s" ag_catalog.agtype)'
    }

    void "wrap a query in a cypher() call for the graph token"() {
        expect:
        AgeStatements.wrap('MATCH (n) WHERE n.title = $title RETURN n') ==
            'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n) WHERE n.title = $title RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        AgeStatements.wrap('MATCH (n) DETACH DELETE n') ==
            'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n) DETACH DELETE n $$) AS ("result" ag_catalog.agtype)'
        AgeStatements.forGraph(AgeStatements.wrap('MATCH (n) RETURN n'), 'movies').startsWith('SELECT * FROM ag_catalog.cypher(\'movies\', $$ MATCH')
    }

    void "take the parameter map only when the query refers to parameters"() {
        expect:
        AgeStatements.takesParameters(AgeStatements.wrap('MATCH (n) WHERE n.title = $title RETURN n'))
        !AgeStatements.takesParameters(AgeStatements.wrap('MATCH (n) RETURN n'))
        !AgeStatements.takesParameters(AgeStatements.wrap("MATCH (n) WHERE n.title = '\$title' RETURN n"))
        !AgeStatements.takesParameters(AgeStatements.wrap('MATCH p = (a)-[*1..$__pathLimit]->(b) RETURN p'))
    }

    void "find the Cypher query inside a statement"() {
        given:
        def statement = AgeStatements.wrap('MATCH (n) RETURN n')

        expect:
        statement.substring(AgeStatements.cypherStart(statement), AgeStatements.cypherEnd(statement)) == 'MATCH (n) RETURN n'
        AgeStatements.cypherStart('MATCH (n) RETURN n') == -1
    }

    void "reject what AGE cannot run"() {
        when:
        AgeStatements.validateGraphName("movies; DROP TABLE x")

        then:
        thrown(IllegalArgumentException)

        when:
        AgeStatements.wrap('RETURN $$')

        then:
        thrown(IllegalArgumentException)

        when:
        AgeStatements.wrap('MATCH (n) RETURN *')

        then:
        thrown(IllegalArgumentException)
    }
}
