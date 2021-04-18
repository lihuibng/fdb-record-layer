/*
 * AllOfMatcher.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2018 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.foundationdb.record.query.plan.temp.matchers;

import com.apple.foundationdb.annotation.API;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * A matcher that only matches anything if all of its downstream matchers produce bindings. This matcher is
 * intended to be used to express a logical <em>and</em> between matchers on an object.
 *
 * As an example
 *
 * {@code
 * matchinAllOf(greaterThan(0), divisibleBy(2)).matches(2))
 * }
 *
 * produces a stream of one binding which binds to {@code 2}.
 *
 * @param <T> the type that this matcher binds to
 */
@API(API.Status.EXPERIMENTAL)
public class AllOfMatcher<T> implements BindingMatcher<T> {
    private final Class<T> staticClassOfT;
    private final List<BindingMatcher<?>> extractingMatchers;

    private AllOfMatcher(@Nonnull final Class<T> staticClassOfT, @Nonnull final Collection<? extends BindingMatcher<?>> matchingExtractors) {
        this.staticClassOfT = staticClassOfT;
        this.extractingMatchers = ImmutableList.copyOf(matchingExtractors);
    }

    @Nonnull
    @Override
    public Class<T> getRootClass() {
        return staticClassOfT;
    }

    /**
     * Attempts to match this matcher against the given object.
     *
     * @param outerBindings preexisting bindings to be used by the matcher
     * @param in the bindable we attempt to match
     * @return a stream of {@link PlannerBindings} containing the matched bindings, or an empty stream is no match was found
     */
    @Nonnull
    @Override
    public Stream<PlannerBindings> bindMatchesSafely(@Nonnull PlannerBindings outerBindings, @Nonnull T in) {
        Stream<PlannerBindings> bindingStream = Stream.of(PlannerBindings.empty());

        for (final BindingMatcher<?> extractingMatcher : extractingMatchers) {
            bindingStream = bindingStream.flatMap(bindings -> extractingMatcher.bindMatches(outerBindings, in).map(bindings::mergedWith));
        }

        return bindingStream;
    }

    public static <T> AllOfMatcher<T> matchingAllOf(@Nonnull final Class<T> staticClassOfT,
                                                    @Nonnull final Collection<? extends BindingMatcher<?>> matchingExtractors) {
        return new AllOfMatcher<>(staticClassOfT, matchingExtractors);
    }
}
