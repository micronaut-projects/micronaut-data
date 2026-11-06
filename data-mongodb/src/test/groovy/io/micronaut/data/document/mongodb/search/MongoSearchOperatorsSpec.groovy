package io.micronaut.data.document.mongodb.search

import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.mongodb.operations.MongoSearchOperators
import spock.lang.Specification

class MongoSearchOperatorsSpec extends Specification {

    private static String render(String query, MatchMode mode, List<String> paths = ["title", "body"], Map<String, Number> boosts = [:]) {
        MongoSearchOperators.render(query, mode, paths, boosts).toJson()
    }

    void "any-term queries become should clauses and negations mustNot clauses"() {
        expect:
        render("coffee", MatchMode.ANY) == '{"text": {"query": "coffee", "path": ["title", "body"]}}'
        render("coffee tea", MatchMode.ANY) == '{"compound": {"should": [{"text": {"query": "coffee", "path": ["title", "body"]}}, {"text": {"query": "tea", "path": ["title", "body"]}}], "minimumShouldMatch": 1}}'
        render("coffee -cake", MatchMode.ANY) == '{"compound": {"should": [{"text": {"query": "coffee", "path": ["title", "body"]}}], "minimumShouldMatch": 1, "mustNot": [{"text": {"query": "cake", "path": ["title", "body"]}}]}}'
        render('"ken burns" tea', MatchMode.ANY) == '{"compound": {"should": [{"phrase": {"query": "ken burns", "path": ["title", "body"]}}, {"text": {"query": "tea", "path": ["title", "body"]}}], "minimumShouldMatch": 1}}'
    }

    void "all-terms queries become must clauses"() {
        expect:
        render("coffee tea", MatchMode.ALL) == '{"compound": {"must": [{"text": {"query": "coffee", "path": ["title", "body"]}}, {"text": {"query": "tea", "path": ["title", "body"]}}]}}'
    }

    void "OR groups become should clauses of an outer compound"() {
        expect:
        render("coffee tea OR cake", MatchMode.ALL) == '{"compound": {"should": [{"compound": {"must": [{"text": {"query": "coffee", "path": ["title", "body"]}}, {"text": {"query": "tea", "path": ["title", "body"]}}]}}, {"text": {"query": "cake", "path": ["title", "body"]}}], "minimumShouldMatch": 1}}'
    }

    void "phrase, prefix and fuzzy modes pick the operator"() {
        expect:
        render("fat rats", MatchMode.PHRASE) == '{"phrase": {"query": "fat rats", "path": ["title", "body"]}}'
        render("Mic", MatchMode.PREFIX, ["title"]) == '{"wildcard": {"query": "mic*", "path": "title", "allowAnalyzedField": true}}'
        render("coffe", MatchMode.FUZZY, ["title"]) == '{"text": {"query": "coffe", "path": "title", "fuzzy": {"maxEdits": 1}}}'
    }

    void "weighted paths are searched by separate boosted operators"() {
        expect:
        render("coffee", MatchMode.ANY, ["title", "body"], [title: 10]) == '{"compound": {"should": [{"text": {"query": "coffee", "path": "title", "score": {"boost": {"value": 10.0}}}}, {"text": {"query": "coffee", "path": "body"}}], "minimumShouldMatch": 1}}'
    }

    void "an empty query matches nothing"() {
        expect:
        render("", MatchMode.ANY).contains("mustNot")
        render(null, MatchMode.ANY).contains("mustNot")
        render("-cake", MatchMode.ANY) == '{"compound": {"mustNot": [{"text": {"query": "cake", "path": ["title", "body"]}}], "must": [{"exists": {"path": "title"}}]}}'
        render("", MatchMode.ANY) == '{"compound": {"must": [{"exists": {"path": "title"}}], "mustNot": [{"exists": {"path": "title"}}]}}'
    }
}
