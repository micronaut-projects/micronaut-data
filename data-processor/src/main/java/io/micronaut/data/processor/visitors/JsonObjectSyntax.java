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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Checks that an annotation value is one JSON object (RFC 8259), so JSON the document stores would only
 * reject when the index is created fails the compilation instead. Only the syntax is checked; the processor
 * has no JSON library to bind it with.
 *
 * @since 5.3.0
 */
@Internal
final class JsonObjectSyntax {

    private final String json;
    private int position;

    private JsonObjectSyntax(String json) {
        this.json = json;
    }

    /**
     * @param json The JSON
     * @return Why the JSON is not one JSON object, or {@code null} when it is
     */
    @Nullable
    static String objectError(String json) {
        JsonObjectSyntax syntax = new JsonObjectSyntax(json);
        try {
            syntax.skipWhitespace();
            syntax.object();
            syntax.skipWhitespace();
            if (syntax.position < json.length()) {
                throw syntax.unexpected();
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private void value() {
        skipWhitespace();
        switch (peek()) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> number();
        }
    }

    private void object() {
        expect('{');
        skipWhitespace();
        if (consume('}')) {
            return;
        }
        do {
            skipWhitespace();
            string();
            skipWhitespace();
            expect(':');
            value();
            skipWhitespace();
        } while (consume(','));
        expect('}');
    }

    private void array() {
        expect('[');
        skipWhitespace();
        if (consume(']')) {
            return;
        }
        do {
            value();
            skipWhitespace();
        } while (consume(','));
        expect(']');
    }

    private void string() {
        expect('"');
        while (true) {
            char c = next();
            if (c == '"') {
                return;
            }
            if (c == '\\') {
                escape();
            } else if (c < 0x20) {
                throw error("unescaped control character in a string");
            }
        }
    }

    private void escape() {
        char c = next();
        if (c == 'u') {
            for (int i = 0; i < 4; i++) {
                if (Character.digit(next(), 16) < 0) {
                    throw error("invalid unicode escape");
                }
            }
        } else if ("\"\\/bfnrt".indexOf(c) < 0) {
            throw error("invalid escape '\\" + c + "'");
        }
    }

    private void literal(String word) {
        if (!json.startsWith(word, position)) {
            throw unexpected();
        }
        position += word.length();
    }

    private void number() {
        consume('-');
        if (!consume('0')) {
            digits();
        }
        if (consume('.')) {
            digits();
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            digits();
        }
    }

    private void digits() {
        int start = position;
        while (position < json.length() && json.charAt(position) >= '0' && json.charAt(position) <= '9') {
            position++;
        }
        if (position == start) {
            throw unexpected();
        }
    }

    private void skipWhitespace() {
        while (position < json.length() && " \t\r\n".indexOf(json.charAt(position)) >= 0) {
            position++;
        }
    }

    private char peek() {
        if (position >= json.length()) {
            throw error("unexpected end of the JSON");
        }
        return json.charAt(position);
    }

    private char next() {
        char c = peek();
        position++;
        return c;
    }

    private boolean consume(char c) {
        if (position < json.length() && json.charAt(position) == c) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!consume(c)) {
            throw position < json.length() ? error("expected '" + c + "' but found '" + json.charAt(position) + "'") : error("expected '" + c + "' but the JSON ends");
        }
    }

    private IllegalArgumentException unexpected() {
        return position < json.length() ? error("unexpected character '" + json.charAt(position) + "'") : error("unexpected end of the JSON");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at offset " + position);
    }
}
