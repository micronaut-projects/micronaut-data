package io.micronaut.data.nitrite.model.query.builder.compile

import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryState
import spock.lang.Specification

class CompileExpressionHandlerUnitSpec extends Specification {

    void "test resolveValue with literal"() {
        given:
        def handler = new CompileExpressionHandler()
        def queryState = new NitriteQueryState(null)

        expect:
        handler.resolveValue(queryState, null, new LiteralExpression("test")) == "test"
        handler.resolveValue(queryState, null, new LiteralExpression(new LiteralExpression("test"))) == "test"
        handler.resolveValue(queryState, null, new LiteralExpression(new RegexPattern(".*"))) == ".*"
        
        // fallback to RuntimeExpressionHandler for non-literals
        handler.resolveValue(queryState, null, "not-literal") == "not-literal"
    }

    void "test handleRegex with literal"() {
        given:
        def handler = new CompileExpressionHandler()
        def queryState = new NitriteQueryState(null)
        def literal = new LiteralExpression("John")

        expect:
        // isLike = true
        handler.handleRegex("name", false, false, false, false, literal, true, queryState, null) == '^John$'
        handler.handleRegex("name", false, false, false, false, new LiteralExpression('J_hn%'), true, queryState, null) == '^J.hn.*$'
        handler.handleRegex("name", false, false, false, false, new LiteralExpression(new LiteralExpression('4_')), true, queryState, null) == '^4.$'
        
        // startsWith = true
        handler.handleRegex("name", false, false, true, false, literal, false, queryState, null) == '^\\QJohn\\E.*'
        
        // endsWith = true
        handler.handleRegex("name", false, false, false, true, literal, false, queryState, null) == '.*\\QJohn\\E$'
        
        // contains
        handler.handleRegex("name", false, false, false, false, literal, false, queryState, null) == '.*\\QJohn\\E.*'
        
        // ignoreCase = true
        handler.handleRegex("name", true, false, false, false, literal, false, queryState, null) == '(?i).*\\QJohn\\E.*'
    }

    void "test resolveRegexValue"() {
        given:
        def handler = new CompileExpressionHandler()
        def queryState = new NitriteQueryState(null)

        expect:
        handler.resolveRegexValue(queryState, null, new LiteralExpression("pattern")) == "pattern"
        handler.resolveRegexValue(queryState, null, new LiteralExpression(new LiteralExpression("pattern"))) == "pattern"
        
        // fallback to resolveValue for non-string literals
        handler.resolveRegexValue(queryState, null, new LiteralExpression(123)) == 123
    }

    void "test resolveCollectionValue"() {
        given:
        def handler = new CompileExpressionHandler()
        def queryState = new NitriteQueryState(null)

        expect:
        // Iterable inside literal
        handler.resolveCollectionValue(queryState, null, new LiteralExpression(["a", "b"])) == ["a", "b"]
    }
}
