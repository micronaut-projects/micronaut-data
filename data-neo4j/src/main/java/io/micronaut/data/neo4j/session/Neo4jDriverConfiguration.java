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
package io.micronaut.data.neo4j.session;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * The configuration of an additional Neo4j driver for graph repositories, under
 * {@code micronaut.data.neo4j.drivers.<name>}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachProperty("micronaut.data.neo4j.drivers")
public final class Neo4jDriverConfiguration {

    private final String name;
    private String uri = "bolt://localhost:7687";
    private @Nullable String username;
    private @Nullable String password;

    /**
     * @param name The name of the driver
     */
    public Neo4jDriverConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The name of the driver
     */
    public String getName() {
        return name;
    }

    /**
     * @return The URI of the server
     */
    public String getUri() {
        return uri;
    }

    /**
     * @param uri The URI of the server
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return The user name, or null to connect without authentication
     */
    public @Nullable String getUsername() {
        return username;
    }

    /**
     * @param username The user name
     */
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    /**
     * @return The password
     */
    public @Nullable String getPassword() {
        return password;
    }

    /**
     * @param password The password
     */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }
}
