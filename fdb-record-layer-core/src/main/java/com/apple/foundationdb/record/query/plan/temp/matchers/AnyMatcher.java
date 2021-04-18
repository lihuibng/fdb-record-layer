/*
 * AnyMatcher.java
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

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * A binding matcher that is a {@link CollectionMatcher} that binds to individual elements contained in a collection
 * separately.
 *
 * As an example
 *
 * {@code
 * any(equalsObject(5)).matches(ImmutableList.of(1, 5, 2, 3, 5))
 * }
 *
 * produces a stream of two bindings which both bind to {@code 5} each.
 *
 * @param <T> the type that this matcher binds to
 */
@API(API.Status.EXPERIMENTAL)
public class AnyMatcher<T> implements CollectionMatcher<T> {
    private final BindingMatcher<T> downstream;

    public AnyMatcher(@Nonnull final BindingMatcher<T> downstream) {
        this.downstream = downstream;
    }

    /**
     * Attempt to match this matcher against the given object.
     *
     * @param outerBindings preexisting bindings to be used by the matcher
     * @param in the object we attempt to match
     * @return a stream of {@link PlannerBindings} containing the matched bindings, or an empty stream is no match was found
     */
    @Nonnull
    @Override
    public Stream<PlannerBindings> bindMatchesSafely(@Nonnull PlannerBindings outerBindings, @Nonnull Collection<? extends T> in) {
        return in.stream()
                .flatMap(item -> downstream.bindMatches(outerBindings, item));
    }

    @Nonnull
    public static <T> AnyMatcher<T> any(@Nonnull final BindingMatcher<T> downstream) {
        return new AnyMatcher<>(downstream);
    }
}
