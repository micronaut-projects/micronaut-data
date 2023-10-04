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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.processor.visitors.MatchFailedException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The method name parser.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class MethodNameParser {

    private final List<MatchStep> matchSteps;

    private MethodNameParser(List<MatchStep> matchSteps) {
        this.matchSteps = matchSteps;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Match> tryMatch(String input) {
        List<Match> matches = new ArrayList<>();
        Iterator<MatchStep> iterator = matchSteps.iterator();
        MatchChain matchChain = new MatchChain() {
            @Override
            public void matched(MatchId matchId, String matchedText, String next) {
                matches.add(new Match(matchId, matchedText));
                next(next);
            }

            @Override
            public void notMatched(MatchId matchId, String input) {
                next(input);
            }

            private void next(String input) {
                if (iterator.hasNext() && !input.isEmpty()) {
                    iterator.next().match(input, this);
                }
            }
        };
        if (iterator.hasNext()) {
            iterator.next().match(input, matchChain);
        }
        return matches;
    }

    /**
     * The builder.
     */
    public static final class Builder {

        private final List<MatchStep> matchSteps = new ArrayList<>();

        private Builder() {
        }

        public Builder match(MatchId matchId, String... prefixes) {
            String prefixPattern = String.join("|", prefixes);
            Pattern pattern = Pattern.compile("^(" + prefixPattern + ")(.*)$");
            matchSteps.add((input, chain) -> {
                Matcher matcher = pattern.matcher(input);
                if (matcher.matches()) {
                    chain.matched(matchId, matcher.group(1), matcher.group(2));
                }
            });
            return this;
        }

        public Builder tryMatch(MatchId matchId, String... prefixes) {
            String prefixPattern = String.join("|", prefixes);
            Pattern pattern = Pattern.compile("^(" + prefixPattern + ")(.*)$");
            matchSteps.add((input, chain) -> {
                Matcher matcher = pattern.matcher(input);
                if (matcher.matches()) {
                    chain.matched(matchId, matcher.group(1), matcher.group(2));
                } else {
                    chain.notMatched(matchId, input);
                }
            });
            return this;
        }

        public Builder tryMatchExactly(MatchId matchId, String text) {
            matchSteps.add((input, chain) -> {
                if (input.equals(text)) {
                    chain.matched(matchId, text, "");
                } else {
                    chain.notMatched(matchId, input);
                }
            });
            return this;
        }

        public Builder tryMatchPrefixedNumber(MatchId matchId, String... prefixes) {
            Pattern pattern = Pattern.compile("^(" + String.join("|", prefixes) + ")(\\d+)(.*)$");
            matchSteps.add((input, chain) -> {
                Matcher matcher = pattern.matcher(input);
                if (matcher.matches()) {
                    chain.matched(matchId, matcher.group(2), matcher.group(3));
                } else {
                    chain.notMatched(matchId, input);
                }
            });
            return this;
        }

        public Builder tryMatchLast(MatchId matchId, String... suffixes) {
            matchSteps.add((input, chain) -> {
                for (String suffix : suffixes) {
                    if (input.endsWith(suffix)) {
                        chain.matched(matchId, suffix, input.substring(0, input.length() - suffix.length()));
                        return;
                    }
                }
                chain.notMatched(matchId, input);
            });
            return this;
        }

        public Builder tryMatchLastOccurrencePrefixed(MatchId matchId, String error, String... prefixes) {
            matchSteps.add((input, chain) -> {
                for (String prefix : prefixes) {
                    int matchIndex = input.lastIndexOf(prefix);
                    if (matchIndex != -1) {
                        String prefixedWord = input.substring(matchIndex + prefix.length());
                        if (!prefixedWord.isEmpty() || error == null) {
                            chain.matched(matchId, prefixedWord, input.substring(0, matchIndex));
                            return;
                        } else {
                            throw new MatchFailedException(error);
                        }
                    }
                }
                chain.notMatched(matchId, input);
            });
            return this;
        }

        public Builder tryMatchFirstOccurrencePrefixed(MatchId matchId, String... prefixes) {
            matchSteps.add((input, chain) -> {
                for (String prefix : prefixes) {
                    int matchIndex = input.indexOf(prefix);
                    if (matchIndex != -1) {
                        String prefixedWord = input.substring(matchIndex + prefix.length());
                        if (!prefixedWord.isEmpty()) {
                            chain.matched(matchId, prefixedWord, input.substring(0, matchIndex));
                            return;
                        }
                    }
                }
                chain.notMatched(matchId, input);
            });
            return this;
        }

        public Builder takeRest(MatchId matchId) {
            matchSteps.add((input, chain) -> {
                if (!input.isEmpty()) {
                    chain.matched(matchId, input, "");
                }
            });
            return this;
        }

        public Builder failOnRest(String error) {
            matchSteps.add((input, chain) -> {
                if (!input.isEmpty()) {
                    throw new MatchFailedException(error + ": " + input);
                }
            });
            return this;
        }

        public MethodNameParser build() {
            return new MethodNameParser(matchSteps);
        }

    }

    /**
     * The match ID.
     */
    public interface MatchId {

    }

    /**
     * The match.
     *
     * @param id   The id
     * @param part The string part
     */
    public record Match(MatchId id, String part) {
    }

    private interface MatchChain {

        void matched(MatchId matchId, String matchedText, String next);

        void notMatched(MatchId matchId, String input);

    }

    private interface MatchStep {

        void match(String input, MatchChain chain);

    }

}
