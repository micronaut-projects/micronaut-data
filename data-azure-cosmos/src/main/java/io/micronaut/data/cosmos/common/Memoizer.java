/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.cosmos.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Memoize function computation results. Mainly used to cache stuff like annotations properties.
 *
 * @param <I> the type of the input to the function
 * @param <O> the type of the output of the function
 */
public final class Memoizer<I, O> {

    private final Map<I, O> cache = new ConcurrentHashMap<>();

    private Memoizer() {
    }

    /**
     * Put function computation results into Memoizer.
     *
     * @param <I> the type of the input to the function
     * @param <O> the type of the output of the function
     * @param function represents a function that accepts one argument and produces a result
     * @return Function
     */
    public static <I, O> Function<I, O> memoize(Function<I, O> function) {
        return new Memoizer<I, O>().internalMemoize(function);
    }

    private Function<I, O> internalMemoize(Function<I, O> function) {
        return input -> cache.computeIfAbsent(input, function);
    }

}
