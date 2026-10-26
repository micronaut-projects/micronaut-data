package io.micronaut.data.graph.processor.matchers

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Checks that string literals and comments are masked before raw queries are scanned for parameters and clauses.
 */
class GraphRawQueryMaskSpec extends Specification {

    @Unroll
    void "test masking #description"() {
        expect:
        GraphRawQueryMethodMatcher.maskLiterals(query) == masked
        masked.length() == query.length()

        where:
        description        | query                                     | masked
        'plain query'      | 'MATCH (n) RETURN n'                      | 'MATCH (n) RETURN n'
        'single quotes'    | "WHERE n.name = '\$x' RETURN n"           | "WHERE n.name = '  ' RETURN n"
        'double quotes'    | 'WHERE n.name = "RETURN" RETURN n'        | 'WHERE n.name = "      " RETURN n'
        'backticks'        | 'MATCH (n:`My Label`) RETURN n'           | 'MATCH (n:`        `) RETURN n'
        'escaped quote'    | "WHERE n.name = 'it\\'s' RETURN n"        | "WHERE n.name = '     ' RETURN n"
        'line comment'     | 'MATCH (n) // DELETE n\nRETURN n'         | 'MATCH (n) ' + (' ' * 11) + '\nRETURN n'
        'block comment'    | 'MATCH (n) /* SET \$p */ RETURN n'        | 'MATCH (n)              RETURN n'
        'trailing escape'  | "RETURN '\\"                              | "RETURN ' "
        'unclosed comment' | 'RETURN n /* x'                           | 'RETURN n     '
        'SQL comment'      | 'RETURN n -- not a Cypher comment'        | 'RETURN n -- not a Cypher comment'
    }
}
