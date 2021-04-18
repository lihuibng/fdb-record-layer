/*
 * ValuePredicate.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2020 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.record.query.predicates;

import com.apple.foundationdb.annotation.API;
import com.apple.foundationdb.annotation.SpotBugsSuppressWarnings;
import com.apple.foundationdb.record.EvaluationContext;
import com.apple.foundationdb.record.PlanHashable;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecord;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStoreBase;
import com.apple.foundationdb.record.query.expressions.Comparisons.Comparison;
import com.apple.foundationdb.record.query.plan.temp.AliasMap;
import com.apple.foundationdb.record.query.plan.temp.CorrelationIdentifier;
import com.apple.foundationdb.record.query.plan.temp.matchers.AllOfMatcher;
import com.apple.foundationdb.record.query.plan.temp.matchers.BindingMatcher;
import com.apple.foundationdb.record.query.plan.temp.matchers.PrimitiveMatchers;
import com.apple.foundationdb.record.query.plan.temp.matchers.TypedMatcherWithExtractAndDownstream;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

/**
 * A predicate consisting of a {@link Value} and a {@link Comparison}.
 */
@API(API.Status.EXPERIMENTAL)
public class ValuePredicate implements PredicateWithValue {
    @Nonnull
    private final Value value;
    @Nonnull
    private final Comparison comparison;

    public ValuePredicate(@Nonnull Value value, @Nonnull Comparison comparison) {
        this.value = value;
        this.comparison = comparison;
    }

    @Nonnull
    public Comparison getComparison() {
        return comparison;
    }

    @Nonnull
    @Override
    public Value getValue() {
        return value;
    }

    @Nonnull
    @Override
    public ValuePredicate withValue(@Nonnull final Value value) {
        return new ValuePredicate(value, comparison);
    }

    @Nullable
    @Override
    public <M extends Message> Boolean eval(@Nonnull final FDBRecordStoreBase<M> store, @Nonnull final EvaluationContext context, @Nullable final FDBRecord<M> record, @Nullable final M message) {
        return comparison.eval(store, context, value.eval(store, context, record, message));
    }

    @Nonnull
    @Override
    public Set<CorrelationIdentifier> getCorrelatedToWithoutChildren() {
        return value.getCorrelatedTo();
    }

    @Nonnull
    @Override
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public QueryPredicate rebaseLeaf(@Nonnull final AliasMap translationMap) {
        Value rebasedValue = value.rebase(translationMap);
        // TODO rebase comparison if needed
        if (value != rebasedValue) { // reference comparison intended
            return new ValuePredicate(rebasedValue, comparison);
        }
        return this;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @SpotBugsSuppressWarnings("EQ_UNUSUAL")
    @Override
    public boolean equals(final Object other) {
        return semanticEquals(other, AliasMap.identitiesFor(getCorrelatedTo()));
    }

    @Override
    public int hashCode() {
        return semanticHashCode();
    }

    @Override
    public boolean equalsWithoutChildren(@Nonnull final QueryPredicate other, @Nonnull final AliasMap equivalenceMap) {
        if (!PredicateWithValue.super.equalsWithoutChildren(other, equivalenceMap)) {
            return false;
        }
        final ValuePredicate that = (ValuePredicate)other;
        return value.semanticEquals(that.value, equivalenceMap) &&
               comparison.equals(that.comparison);
    }
    
    @Override
    public int semanticHashCode() {
        return Objects.hash(value.semanticHashCode(), comparison);
    }

    @Override
    public int planHash(@Nonnull final PlanHashKind hashKind) {
        return PlanHashable.objectsPlanHash(hashKind, value, comparison);
    }

    @Override
    public String toString() {
        return value.toString() + " " + comparison.toString();
    }

    @Nonnull
    public static <V extends Value> BindingMatcher<ValuePredicate> valuePredicate(@Nonnull final BindingMatcher<V> downstreamValue,
                                                                                  @Nonnull final Comparison comparison) {
        return valuePredicate(downstreamValue, PrimitiveMatchers.equalsObject(comparison));
    }

    @Nonnull
    public static <V extends Value, C extends Comparison> BindingMatcher<ValuePredicate> valuePredicate(@Nonnull final BindingMatcher<V> downstreamValue,
                                                                                                        @Nonnull final BindingMatcher<C> downstreamComparison) {
        return TypedMatcherWithExtractAndDownstream.typedWithDownstream(ValuePredicate.class,
                t -> t,
                AllOfMatcher.matchingAllOf(ValuePredicate.class,
                        ImmutableList.of(TypedMatcherWithExtractAndDownstream.typedWithDownstream(ValuePredicate.class, ValuePredicate::getValue, downstreamValue),
                                TypedMatcherWithExtractAndDownstream.typedWithDownstream(ValuePredicate.class, ValuePredicate::getComparison, downstreamComparison))));
    }
}
