/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.jakarta.tck;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullUnmarked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@NullUnmarked
@Internal
final class DeploymentDir {
    final Path root;
    final Path source;
    final Path target;
    final Path lib;

    DeploymentDir() throws IOException {
        this.root = Files.createTempDirectory("odi-arquillian-");

        this.source = Files.createDirectory(root.resolve("source"));
        this.target = Files.createDirectory(root.resolve("target"));
        this.lib = Files.createDirectory(root.resolve("lib"));
    }
}
