/*
 * Copyright 2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.cosmos.operations

import spock.lang.Specification

final class JacksonNodeConverterSpec extends Specification {

    static class MyPojo {
        String name
        int value
    }

    def "jackson 3 to jackson 2 and back (via converter)"() {
        given:
        // Build a com.fasterxml tree, then convert to tools and back
        def fRoot = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
        fRoot.put("i", 123)
        fRoot.put("l", 1234567890123L)
        fRoot.put("bd", new BigDecimal("123.45"))
        fRoot.put("s", "hello")
        fRoot.put("b", true)
        fRoot.putNull("n")
        def fArr = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()
        fArr.add(1)
        fArr.add("a")
        fRoot.set("arr", fArr)
        byte[] bytes = [1, 2, 3, 4] as byte[]
        fRoot.put("bin", bytes)
        def pojo = new MyPojo(name: "foo", value: 42)
        fRoot.putPOJO("pojo", pojo)

        when:
        def tNode = JacksonNodeConverter.toJackson3JsonNode(fRoot)
        def fNode = JacksonNodeConverter.toJackson2JsonNode(tNode)

        then:
        tNode != null
        tNode.isObject()
        fNode != null
        fNode.isObject()
        // Validate structure and numeric types preserved after round-trip
        fNode.get("i").intValue() == 123
        fNode.get("i").numberType() == com.fasterxml.jackson.core.JsonParser.NumberType.INT
        fNode.get("l").longValue() == 1234567890123L
        fNode.get("l").numberType() == com.fasterxml.jackson.core.JsonParser.NumberType.LONG
        fNode.get("bd").decimalValue() == new BigDecimal("123.45")
        fNode.get("bd").numberType() == com.fasterxml.jackson.core.JsonParser.NumberType.BIG_DECIMAL
        fNode.get("s").textValue() == "hello"
        fNode.get("b").booleanValue()
        fNode.get("n").isNull()
        fNode.get("arr").isArray()
        fNode.get("arr").size() == 2
        fNode.get("arr").get(0).intValue() == 1
        fNode.get("arr").get(1).textValue() == "a"
        fNode.get("bin").isBinary()
        Arrays.equals(bytes, fNode.get("bin").binaryValue())
        fNode.get("pojo").isPojo()
        fNode.get("pojo") instanceof com.fasterxml.jackson.databind.node.POJONode
        ((com.fasterxml.jackson.databind.node.POJONode) fNode.get("pojo")).getPojo() == pojo
    }

    def "jackson2 to jackson3 and back"() {
        given:
        def fRoot = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
        fRoot.put("i", 321)
        fRoot.put("l", 9876543210987L)
        fRoot.put("bd", new BigDecimal("987.65"))
        fRoot.put("s", "world")
        fRoot.put("b", false)
        fRoot.putNull("n")
        def fArr = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()
        fArr.add(2)
        fArr.add("b")
        fRoot.set("arr", fArr)
        byte[] bytes = [9, 8, 7, 6] as byte[]
        fRoot.put("bin", bytes)
        def pojo = new MyPojo(name: "bar", value: 24)
        fRoot.putPOJO("pojo", pojo)

        when:
        def tNode = JacksonNodeConverter.toJackson3JsonNode(fRoot)
        def fRound = JacksonNodeConverter.toJackson2JsonNode(tNode)

        then:
        tNode != null
        tNode.isObject()
        fRound != null
        fRound.isObject()
        // Validate structure and numeric types preserved after round-trip
        fRound.get("i").intValue() == 321
        fRound.get("i").numberType() == com.fasterxml.jackson.core.JsonParser.NumberType.INT
        fRound.get("l").longValue() == 9876543210987L
        fRound.get("l").numberType() == com.fasterxml.jackson.core.JsonParser.NumberType.LONG
        fRound.get("bd").decimalValue() == new BigDecimal("987.65")
        fRound.get("bd").numberType() == com.fasterxml.jackson.core.JsonParser.NumberType.BIG_DECIMAL
        fRound.get("s").textValue() == "world"
        !fRound.get("b").booleanValue()
        fRound.get("n").isNull()
        fRound.get("arr").isArray()
        fRound.get("arr").size() == 2
        fRound.get("arr").get(0).intValue() == 2
        fRound.get("arr").get(1).textValue() == "b"
        fRound.get("bin").isBinary()
        Arrays.equals(bytes, fRound.get("bin").binaryValue())
        fRound.get("pojo").isPojo()
        fRound.get("pojo") instanceof com.fasterxml.jackson.databind.node.POJONode
        ((com.fasterxml.jackson.databind.node.POJONode) fRound.get("pojo")).getPojo() == pojo
    }

    def "missing node preserved"() {
        given:
        def fMissing = com.fasterxml.jackson.databind.node.MissingNode.getInstance()

        when:
        def tMissing = JacksonNodeConverter.toJackson3JsonNode(fMissing)
        def fMissing2 = JacksonNodeConverter.toJackson2JsonNode(tMissing)

        then:
        tMissing != null
        tMissing.isMissingNode()
        fMissing2 != null
        fMissing2.isMissingNode()
    }
}
