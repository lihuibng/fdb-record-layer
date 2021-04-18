/*
 * ReferenceMatchers.java
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
import com.apple.foundationdb.record.query.plan.plans.RecordQueryPlan;
import com.apple.foundationdb.record.query.plan.temp.ExpressionRef;
import com.apple.foundationdb.record.query.plan.temp.RelationalExpression;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.apple.foundationdb.record.query.plan.temp.matchers.MultiMatcher.all;

/**
 * Matchers for {@link ExpressionRef}s.
 */
@API(API.Status.EXPERIMENTAL)
public class ReferenceMatchers {
    private ReferenceMatchers() {
        // do not instantiate
    }

    @SuppressWarnings("unchecked")
    public static <R extends ExpressionRef<? extends RelationalExpression>> BindingMatcher<R> anyRef() {
        return new TypedMatcher<>((Class<R>)(Class<?>)ExpressionRef.class);
    }

    public static BindingMatcher<? extends ExpressionRef<? extends RelationalExpression>> anyRefOverOnlyPlans() {
        return grouping(all(RelationalExpressionMatchers.ofType(RecordQueryPlan.class)));
    }

    @SuppressWarnings("unchecked")
    public static <R extends ExpressionRef<? extends RelationalExpression>, C extends Collection<? extends RelationalExpression>> BindingMatcher<R> grouping(@Nonnull final BindingMatcher<C> downstream) {
        return TypedMatcherWithExtractAndDownstream.typedWithDownstream((Class<R>)(Class<?>)ExpressionRef.class,
                r -> r.getMembers(),
                downstream);
    }
}
