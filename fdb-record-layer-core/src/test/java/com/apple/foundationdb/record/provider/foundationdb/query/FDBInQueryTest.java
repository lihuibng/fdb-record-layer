/*
 * FDBInQueryTest.java
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

package com.apple.foundationdb.record.provider.foundationdb.query;

import com.apple.foundationdb.record.EvaluationContext;
import com.apple.foundationdb.record.IndexScanType;
import com.apple.foundationdb.record.PlanHashable;
import com.apple.foundationdb.record.RecordCoreException;
import com.apple.foundationdb.record.RecordCursorIterator;
import com.apple.foundationdb.record.RecordCursorResult;
import com.apple.foundationdb.record.TestHelpers;
import com.apple.foundationdb.record.TestRecordsEnumProto;
import com.apple.foundationdb.record.TestRecordsWithHeaderProto;
import com.apple.foundationdb.record.metadata.Index;
import com.apple.foundationdb.record.metadata.IndexTypes;
import com.apple.foundationdb.record.metadata.Key;
import com.apple.foundationdb.record.metadata.RecordTypeBuilder;
import com.apple.foundationdb.record.metadata.expressions.KeyExpression.FanType;
import com.apple.foundationdb.record.provider.foundationdb.FDBQueriedRecord;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext;
import com.apple.foundationdb.record.query.RecordQuery;
import com.apple.foundationdb.record.query.expressions.Query;
import com.apple.foundationdb.record.query.expressions.QueryComponent;
import com.apple.foundationdb.record.query.plan.RecordQueryPlanner;
import com.apple.foundationdb.record.query.plan.ScanComparisons;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryInParameterJoinPlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryPlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryPlanWithIndex;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryUnionPlan;
import com.apple.test.BooleanSource;
import com.apple.test.Tags;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.apple.foundationdb.record.TestHelpers.assertDiscardedAtMost;
import static com.apple.foundationdb.record.metadata.Key.Expressions.concat;
import static com.apple.foundationdb.record.metadata.Key.Expressions.field;
import static com.apple.foundationdb.record.query.plan.ScanComparisons.range;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.anyFilter;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.bounds;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.descendant;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.filter;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.hasTupleString;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.inParameter;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.inValues;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.indexName;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.indexScan;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.indexScanType;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.primaryKeyDistinct;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.scan;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.unbounded;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.union;
import static com.apple.foundationdb.record.query.plan.match.PlanMatchers.unorderedUnion;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryFilterPlan.filterPlan;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryFilterPlan.queryComponents;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryInParameterJoinPlan.inParameterJoinPlan;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryInValuesJoinPlan.inValuesJoinPlan;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryInValuesJoinPlan.inValuesList;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryIndexPlan.indexPlan;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryPlanWithComparisons.scanComparisons;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryScanPlan.scanPlan;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryUnionPlan.comparisonKey;
import static com.apple.foundationdb.record.query.plan.plans.RecordQueryUnionPlan.unionPlan;
import static com.apple.foundationdb.record.query.plan.temp.matchers.ListMatcher.exactly;
import static com.apple.foundationdb.record.query.plan.temp.matchers.ListMatcher.only;
import static com.apple.foundationdb.record.query.plan.temp.matchers.PrimitiveMatchers.equalsObject;
import static com.apple.foundationdb.record.query.plan.temp.matchers.RelationalExpressionMatchers.descendantPlans;
import static com.apple.foundationdb.record.query.plan.temp.matchers.RelationalExpressionMatchers.selfOrDescendantPlans;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests related to planning queries with an IN clause.
 */
@Tag(Tags.RequiresFDB)
public class FDBInQueryTest extends FDBRecordStoreQueryTestBase {
    /**
     * Verify that an IN without an index is implemented as a filter on a scan, as opposed to a loop of a filter on a scan.
     */
    @Test
    public void testInQueryNoIndex() throws Exception {
        complexQuerySetup(NO_HOOK);
        final QueryComponent filter = Query.field("num_value_2").in(asList(0, 2));
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(filter)
                .build();

        // Scan(<,>) | [MySimpleRecord] | num_value_2 IN [0, 2]
        RecordQueryPlan plan = planner.plan(query);
        
        assertTrue(filterPlan(descendantPlans(scanPlan().where(scanComparisons(ScanComparisons.unbounded()))))
                .where(queryComponents(exactly(equalsObject(filter)))).matchesExactly(plan));

        assertEquals(-1139367278, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-1907300063, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-1694772440, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(67, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValue2(), anyOf(is(0), is(2))),
                context -> assertDiscardedAtMost(33, context)));
    }

    /**
     * Verify that an IN (with parameter) without an index is implemented as a filter on a scan.
     */
    @Test
    public void testInQueryNoIndexWithParameter() throws Exception {
        complexQuerySetup(NO_HOOK);
        final QueryComponent filter = Query.field("num_value_2").in("valuesThree");
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(filter)    // num_value_2 is i%3
                .build();

        // Scan(<,>) | [MySimpleRecord] | num_value_2 IN $valuesThree
        RecordQueryPlan plan = planner.plan(query);
        assertTrue(filterPlan(descendantPlans(scanPlan().where(scanComparisons(ScanComparisons.unbounded()))))
                .where(queryComponents(exactly(equalsObject(filter)))).matchesExactly(plan));
        assertEquals(-1677754212, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-192829430, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(871680640, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(33, querySimpleRecordStore(NO_HOOK, plan,
                () -> EvaluationContext.forBinding("valuesThree", asList(1, 3)),
                record -> assertThat(record.getNumValue2(), anyOf(is(1), is(3))),
                context -> assertDiscardedAtMost(67, context)));
    }

    /**
     * Verify that an IN with an index is implemented as an index scan, with an IN join.
     */
    @Test
    public void testInQueryIndex() throws Exception {
        complexQuerySetup(NO_HOOK);
        List<Integer> ls = asList(1, 2, 4);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("num_value_3_indexed").in(ls))
                .build();

        // Index(MySimpleRecord$num_value_3_indexed [EQUALS $__in_num_value_3_indexed__0]) WHERE __in_num_value_3_indexed__0 IN [1, 2, 4]
        RecordQueryPlan plan = planner.plan(query);
        assertTrue(
                inValuesJoinPlan(
                        indexPlan()
                                .where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$num_value_3_indexed"))
                                .and(scanComparisons(range("[EQUALS $__in_num_value_3_indexed__0]")))
                ).where(inValuesList(equalsObject(ls))).matchesExactly(plan));
        assertEquals(-2004060310, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1111143844, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(619086974, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(60, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValue3Indexed(), anyOf(is(1), is(2), is(4))),
                TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify that an IN (with parameter) with an index is implemented as an index scan, with an IN join.
     */
    @Test
    public void testInQueryParameter() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("num_value_3_indexed").in("valueThrees"))
                .build();

        // Index(MySimpleRecord$num_value_3_indexed [EQUALS $__in_num_value_3_indexed__0]) WHERE __in_num_value_3_indexed__0 IN $valueThrees
        RecordQueryPlan plan = planner.plan(query);
        assertTrue(
                inParameterJoinPlan(
                        indexPlan()
                                .where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$num_value_3_indexed"))
                                .and(scanComparisons(range("[EQUALS $__in_num_value_3_indexed__0]")))
                ).where(RecordQueryInParameterJoinPlan.inParameter(equalsObject("valueThrees"))).matchesExactly(plan));
        assertEquals(883815022, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1054651695, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(562625673, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        int count = querySimpleRecordStore(NO_HOOK, plan,
                () -> EvaluationContext.forBinding("valueThrees", asList(1, 3, 4)),
                myrec -> assertThat(myrec.getNumValue3Indexed(), anyOf(is(1), is(3), is(4))),
                TestHelpers::assertDiscardedNone);
        assertEquals(60, count);
    }

    /**
     * Verify that an in with a bad parameter plans correctly but fails upon execution.
     */
    @Test
    public void testInQueryParameterBad() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("num_value_3_indexed").in("valueThrees"))
                .build();

        // Index(MySimpleRecord$num_value_3_indexed [EQUALS $__in_num_value_3_indexed__0]) WHERE __in_num_value_3_indexed__0 IN $valueThrees
        RecordQueryPlan plan = planner.plan(query);
        assertTrue(
                inParameterJoinPlan(
                        indexPlan()
                                .where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$num_value_3_indexed"))
                                .and(scanComparisons(range("[EQUALS $__in_num_value_3_indexed__0]")))
                ).where(RecordQueryInParameterJoinPlan.inParameter(equalsObject("valueThrees"))).matchesExactly(plan));
        assertEquals(883815022, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1054651695, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(562625673, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(0, querySimpleRecordStore(NO_HOOK, plan,
                () -> EvaluationContext.forBinding("valueThrees", Collections.emptyList()),
                myrec -> fail("There should be no results")));
        assertThrows(RecordCoreException.class, TestHelpers.toCallable(() ->
                assertEquals(0, querySimpleRecordStore(NO_HOOK, plan,
                        EvaluationContext::empty, /* no binding for valueThrees */
                        myrec -> fail("There should be no results")))));
        assertEquals(0, querySimpleRecordStore(NO_HOOK, plan,
                () -> EvaluationContext.forBinding("valueThrees", null), /* no binding for valueThrees */
                myrec -> fail("There should be no results")));
    }


    /**
     * Verify that NOT IN is planned correctly, and fails if no binding is provided.
     */
    @Test
    public void testNotInQueryParameterBad() throws Exception {
        complexQuerySetup(NO_HOOK);
        final QueryComponent filter = Query.not(Query.field("num_value_3_indexed").in("valueThrees"));
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(filter)
                .build();

        // Scan(<,>) | [MySimpleRecord] | Not(num_value_3_indexed IN $valueThrees)
        RecordQueryPlan plan = planner.plan(query);
        assertTrue(filterPlan(descendantPlans(scanPlan().where(scanComparisons(ScanComparisons.unbounded()))))
                .where(queryComponents(exactly(equalsObject(filter)))).matchesExactly(plan));
        assertEquals(1667070490, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1804602975, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-557106421, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(100, querySimpleRecordStore(NO_HOOK, plan,
                () -> EvaluationContext.forBinding("valueThrees", Collections.emptyList()),
                myrec -> {
                },
                TestHelpers::assertDiscardedNone));
        assertEquals(0, querySimpleRecordStore(NO_HOOK, plan,
                () -> EvaluationContext.forBinding("valueThrees", null), /* no binding for valueThrees */
                myrec -> fail("There should be no results")));
    }

    /**
     * Verify that an IN against an unsorted list with an index is implemented as an index scan, with an IN join on
     * a sorted copy of the list.
     */
    @Test
    public void testInQueryIndexSorted() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("num_value_3_indexed").in(asList(1, 4, 2)))
                .setSort(field("num_value_3_indexed"))
                .build();

        // Index(MySimpleRecord$num_value_3_indexed [EQUALS $__in_num_value_3_indexed__0]) WHERE __in_num_value_3_indexed__0 IN [1, 2, 4] SORTED
        RecordQueryPlan plan = planner.plan(query);
        assertTrue(
                inValuesJoinPlan(
                        indexPlan()
                                .where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$num_value_3_indexed"))
                                .and(scanComparisons(range("[EQUALS $__in_num_value_3_indexed__0]")))
                ).where(inValuesList(equalsObject(asList(1, 2, 4)))).matchesExactly(plan));
        assertEquals(-2004060309, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1111138078, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(619081208, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(60, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValue3Indexed(), anyOf(is(1), is(2), is(4))),
                TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify that an IN against an unsorted list with an index is not implemented as an IN JOIN when the query sort is
     * not by the field with an IN filter.
     */
    @Test
    public void testInQueryIndexSortedDifferently() throws Exception {
        complexQuerySetup(NO_HOOK);
        final QueryComponent filter = Query.field("num_value_3_indexed").in(asList(1, 4, 2));
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(filter)
                .setSort(field("str_value_indexed"))
                .build();

        // Index(MySimpleRecord$str_value_indexed <,>) | num_value_3_indexed IN [1, 4, 2]
        RecordQueryPlan plan = planner.plan(query);
        // IN join is cancelled on account of incompatible sorting.
        assertTrue(filterPlan(selfOrDescendantPlans(indexPlan().where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$str_value_indexed")).and(scanComparisons(ScanComparisons.unbounded()))))
                .where(queryComponents(exactly(equalsObject(filter)))).matchesExactly(plan));
        assertEquals(1775865786, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-590700400, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-379100142, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(60, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValue3Indexed(), anyOf(is(1), is(2), is(4))),
                context -> TestHelpers.assertDiscardedAtMost(40, context)));
    }

    /**
     * Verify that an IN query with a sort can be implemented as an ordered union of compound indexes that can satisfy
     * the sort once the equality predicates from the IN have been pushed onto the indexes.
     * @see com.apple.foundationdb.record.query.plan.planning.InExtractor#asOr()
     */
    @ParameterizedTest
    @BooleanSource
    public void inQueryWithSortBySecondFieldOfCompoundIndex(boolean shouldAttemptInAsOr) throws Exception {
        RecordMetaDataHook hook = metaData ->
                metaData.addIndex("MySimpleRecord", "compoundIndex",
                        concat(field("num_value_3_indexed"), field("str_value_indexed")));
        complexQuerySetup(hook);
        final List<Integer> inList = asList(1, 4, 2);
        final QueryComponent filter = Query.field("num_value_3_indexed").in(inList);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(filter)
                .setSort(field("str_value_indexed"))
                .build();

        assertTrue(planner instanceof RecordQueryPlanner); // The configuration is planner-specific.
        RecordQueryPlanner recordQueryPlanner = (RecordQueryPlanner)planner;
        recordQueryPlanner.setConfiguration(recordQueryPlanner.getConfiguration().asBuilder()
                .setAttemptFailedInJoinAsOr(shouldAttemptInAsOr)
                .build());

        // Index(MySimpleRecord$str_value_indexed <,>) | num_value_3_indexed IN [1, 4, 2]
        // Index(compoundIndex [[1],[1]]) ∪[Field { 'str_value_indexed' None}, Field { 'rec_no' None}] Index(compoundIndex [[4],[4]]) ∪[Field { 'str_value_indexed' None}, Field { 'rec_no' None}] Index(compoundIndex [[2],[2]])
        RecordQueryPlan plan = planner.plan(query);
        if (shouldAttemptInAsOr) {
            assertTrue(
                    RecordQueryUnionPlan
                            .unionPlan(inList.stream().map(number ->
                                    indexPlan().where(RecordQueryPlanWithIndex.indexName("compoundIndex"))
                                            .and(scanComparisons(range(String.format("[[%d],[%d]]", number, number)))))
                                    .collect(ImmutableList.toImmutableList()))
                            .where(comparisonKey(concat(field("str_value_indexed"), primaryKey("MySimpleRecord")))).matchesExactly(plan));
            assertEquals(-1813975352, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
            assertEquals(-530950667, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
            assertEquals(-148115282, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        } else {
            assertTrue(
                    filterPlan(indexPlan()
                            .where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$str_value_indexed"))
                            .and(scanComparisons(ScanComparisons.unbounded()))
                    ).where(queryComponents(exactly(equalsObject(filter)))).matchesExactly(plan));

            assertEquals(1775865786, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
            assertEquals(-590700400, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
            assertEquals(-379100142, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        }

        assertEquals(60, querySimpleRecordStore(hook, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValue3Indexed(), anyOf(is(1), is(2), is(4))),
                context -> TestHelpers.assertDiscardedAtMost(40, context)));
    }

    /**
     * Verify that an IN query with a sort and range predicate can be implemented as an ordered union of compound indexes
     * that can satisfy the sort once the equality predicates from the IN have been pushed onto the indexes.
     * @see com.apple.foundationdb.record.query.plan.planning.InExtractor#asOr()
     */
    @ParameterizedTest
    @BooleanSource
    public void inQueryWithSortAndRangePredicateOnSecondFieldOfCompoundIndex(boolean shouldAttemptInAsOr) throws Exception {
        RecordMetaDataHook hook = metaData ->
                metaData.addIndex("MySimpleRecord", "compoundIndex",
                        concat(field("num_value_3_indexed"), field("str_value_indexed")));
        complexQuerySetup(hook);
        final List<Integer> inList = asList(1, 4, 2);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.and(Query.field("num_value_3_indexed").in(inList),
                        Query.field("str_value_indexed").greaterThan("bar"),
                        Query.field("str_value_indexed").lessThan("foo")))
                .setSort(field("str_value_indexed"))
                .build();

        assertTrue(planner instanceof RecordQueryPlanner); // The configuration is planner-specific.
        RecordQueryPlanner recordQueryPlanner = (RecordQueryPlanner)planner;
        recordQueryPlanner.setConfiguration(recordQueryPlanner.getConfiguration().asBuilder()
                .setAttemptFailedInJoinAsOr(shouldAttemptInAsOr)
                .build());

        // Index(MySimpleRecord$str_value_indexed ([bar],[foo])) | num_value_3_indexed IN [1, 4, 2]
        RecordQueryPlan plan = planner.plan(query);
        if (shouldAttemptInAsOr) {
            // IN join is impossible because of incompatible sorting, but we can still plan as an OR on the compound index.
            assertTrue(
                    unionPlan(
                            inList.stream()
                                    .map(number -> indexPlan().where(RecordQueryPlanWithIndex.indexName("compoundIndex")).and(scanComparisons(range(String.format("([%d, bar],[%d, foo])", number, number)))))
                                    .collect(ImmutableList.toImmutableList()))
                            .where(RecordQueryUnionPlan.comparisonKey(equalsObject(concat(field("str_value_indexed"), primaryKey("MySimpleRecord"))))).matchesExactly(plan));
            assertEquals(651476052, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
            assertEquals(2072307751, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
            assertEquals(661634447, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        } else {
            assertTrue(
                    filterPlan(indexPlan()
                            .where(RecordQueryPlanWithIndex.indexName("MySimpleRecord$str_value_indexed")).and(scanComparisons(range("([bar],[foo])")))
                    ).where(queryComponents(only(equalsObject(Query.field("num_value_3_indexed").in(inList))))).matchesExactly(plan));
            assertEquals(-1681846586, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
            assertEquals(1498477022, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
            assertEquals(1666766577, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        }

        assertEquals(30, querySimpleRecordStore(hook, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValue3Indexed(), anyOf(is(1), is(2), is(4))),
                context -> { }));
    }

    /**
     * Verify that an IN predicate that, when converted to an OR of equality predicates, would lead to a very large DNF
     * gets planned as a normal IN query rather than throwing an exception.
     */
    @Test
    public void cnfAsInQuery() throws Exception {
        RecordMetaDataHook hook = metaData ->
                metaData.addIndex("MySimpleRecord", "compoundIndex",
                        concat(field("num_value_3_indexed"), field("str_value_indexed")));
        complexQuerySetup(hook);

        // A CNF whose DNF size doesn't fit in an int, expressed with IN predicates.
        List<QueryComponent> conjuncts = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            conjuncts.add(Query.field("num_value_3_indexed").in(ImmutableList.of(i * 100, i * 100 + 1)));
        }

        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.and(conjuncts))
                .setSort(field("str_value_indexed"))
                .build();
        RecordQueryPlan plan = planner.plan(query);
        // Did not throw an exception
        assertThat(plan, filter(query.getFilter(), indexScan(allOf(indexName("MySimpleRecord$str_value_indexed"), unbounded()))));
    }

    /**
     * Verify that a query with an IN on the second nested field of a multi-index for which there is also a first nested
     * field is translated into an appropriate index scan.
     */
    @Test
    public void testInWithNesting() throws Exception {
        final RecordMetaDataHook recordMetaDataHook = metaData -> {
            metaData.getRecordType("MyRecord")
                    .setPrimaryKey(field("str_value"));
            metaData.addIndex("MyRecord", "ind", field("header").nest(field("rec_no"), field("path")));
        };

        setupRecordsWithHeader(recordMetaDataHook, (i, record) -> {
            record.setStrValue("_" + i);
            record.getHeaderBuilder().setRecNo(i % 5).setPath("String" + i % 50).setNum(i);
        });

        List<String> ls = asList("String6", "String1", "String25", "String11");
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MyRecord")
                .setFilter(Query.field("header").matches(Query.and(
                        Query.field("rec_no").equalsValue(1L),
                        Query.field("path").in(ls))))
                .build();

        // Index(ind [EQUALS 1, EQUALS $__in_path__0]) WHERE __in_path__0 IN [String6, String1, String25, String11]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), indexScan(allOf(indexName("ind"),
                bounds(hasTupleString("[EQUALS 1, EQUALS $__in_path__0]"))))));
        assertEquals(1075889283, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-347431998, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(677597961, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        queryRecordsWithHeader(recordMetaDataHook, plan, cursor ->
                        assertEquals(asList( "_56", "_6", "_1", "_51", "_11", "_61"),
                                cursor.map(m -> m.getStrValue()).asList().get()),
                TestHelpers::assertDiscardedNone);
    }

    /**
     * Verify that a query with multiple INs is translated into an index scan within multiple IN joins.
     */
    @Test
    public void testMultipleInQueryIndex() throws Exception {
        final RecordMetaDataHook recordMetaDataHook = metaData -> {
            metaData.getRecordType("MyRecord")
                    .setPrimaryKey(field("str_value"));
            metaData.addIndex("MyRecord", "ind", field("header").nest(field("rec_no"), field("path")));
        };


        setupRecordsWithHeader(recordMetaDataHook, (i, record) -> {
            record.setStrValue("_" + i);
            record.getHeaderBuilder().setRecNo(i % 5).setPath("String" + i % 50).setNum(i);
        });
        List<Long> longList = asList(1L, 4L);
        List<String> stringList = asList("String6", "String25", "String1", "String34");
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MyRecord")
                .setFilter(Query.field("header").matches(Query.and(
                        Query.field("rec_no").in(longList),
                        Query.field("path").in(stringList))))
                .build();

        // Index(ind [EQUALS $__in_rec_no__0, EQUALS $__in_path__1]) WHERE __in_path__1 IN [String6, String25, String1, String34] WHERE __in_rec_no__0 IN [1, 4]
        RecordQueryPlan plan = planner.plan(query);
        Matcher<RecordQueryPlan> indexMatcher = indexScan(allOf(indexName("ind"),
                bounds(hasTupleString("[EQUALS $__in_rec_no__0, EQUALS $__in_path__1]"))));
        assertThat(plan, anyOf(
                inValues(equalTo(longList), inValues(equalTo(stringList), indexMatcher)),
                inValues(equalTo(stringList), inValues(equalTo(longList), indexMatcher))));
        assertEquals(-1869764109, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(12526355, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(1467763781, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        queryRecordsWithHeader(recordMetaDataHook, plan, cursor ->
                        assertEquals(asList("_56", "_6", "_1", "_51", "_34", "_84"),
                                cursor.map(m -> m.getStrValue()).asList().get()),
                TestHelpers::assertDiscardedNone);
    }

    /**
     * Verify that a query with multiple INs is translated into an index scan within multiple IN joins, when the query
     * sort order is compatible with the nesting of the IN joins.
     */
    @Test
    public void testMultipleInQueryIndexSorted() throws Exception {
        final RecordMetaDataHook recordMetaDataHook = metaData -> {
            metaData.getRecordType("MyRecord")
                    .setPrimaryKey(field("str_value"));
            metaData.addIndex("MyRecord", "ind", field("header").nest(field("rec_no"), field("path")));
        };


        setupRecordsWithHeader(recordMetaDataHook, (i, record) -> {
            record.setStrValue("_" + i);
            record.getHeaderBuilder().setRecNo(i % 5).setPath("String" + i % 50).setNum(i);
        });
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MyRecord")
                .setFilter(Query.field("header").matches(Query.and(
                        Query.field("path").in(asList("String6", "String25", "String1", "String34")),
                        Query.field("rec_no").in(asList(4L, 1L)))))
                .setSort(field("header").nest(field("rec_no"), field("path")))
                .build();

        // Index(ind [EQUALS $__in_rec_no__1, EQUALS $__in_path__0]) WHERE __in_path__0 IN [String1, String25, String34, String6] SORTED WHERE __in_rec_no__1 IN [1, 4] SORTED
        RecordQueryPlan plan = planner.plan(query);
        List<String> sortedStringList = asList("String1", "String25", "String34", "String6");
        List<Long> sortedLongList = asList(1L, 4L);
        assertThat(plan, inValues(equalTo(sortedLongList), inValues(equalTo(sortedStringList),
                indexScan(allOf(indexName("ind"), bounds(hasTupleString("[EQUALS $__in_rec_no__1, EQUALS $__in_path__0]")))))));
        assertEquals(303286809, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-535785429, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-1305077319, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        queryRecordsWithHeader(recordMetaDataHook, plan, cursor ->
                        assertEquals(asList("1:String1", "1:String1", "1:String6", "1:String6", "4:String34", "4:String34"),
                                cursor.map(m -> m.getHeader().getRecNo() + ":" + m.getHeader().getPath()).asList().get()),
                TestHelpers::assertDiscardedNone);
    }

    /**
     * Verify that an IN join is executed correctly when the number of records to retrieve is limited.
     */
    @Test
    public void testInWithLimit() throws Exception {
        final RecordMetaDataHook recordMetaDataHook = metaData -> {
            metaData.getRecordType("MyRecord")
                    .setPrimaryKey(field("str_value"));
            metaData.addIndex("MyRecord", "ind", field("header").nest(field("rec_no"), field("path")));
        };

        setupRecordsWithHeader(recordMetaDataHook, (i, record) -> {
            record.setStrValue("_" + i);
            record.getHeaderBuilder().setRecNo(i % 5).setPath("String" + i % 50).setNum(i);
        });

        List<String> ls = asList("String6", "String1", "String25", "String11");
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MyRecord")
                .setFilter(Query.field("header").matches(Query.and(
                        Query.field("rec_no").equalsValue(1L),
                        Query.field("path").in(ls))))
                .build();

        // Index(ind [EQUALS 1, EQUALS $__in_path__0]) WHERE __in_path__0 IN [String6, String1, String25, String11]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), indexScan(allOf(indexName("ind"), bounds(hasTupleString("[EQUALS 1, EQUALS $__in_path__0]"))))));
        assertEquals(1075889283, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-347431998, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(677597961, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        queryRecordsWithHeader(recordMetaDataHook, plan, null, 3, cursor ->
                        assertEquals(asList( "_56", "_6", "_1"),
                                cursor.map(m -> m.getStrValue()).asList().get()),
                TestHelpers::assertDiscardedNone);
    }

    /**
     * Verify that an IN join is executed correctly when continuations are used.
     */
    @Test
    public void testInWithContinuation() throws Exception {
        final RecordMetaDataHook recordMetaDataHook = metaData -> {
            metaData.getRecordType("MyRecord")
                    .setPrimaryKey(field("str_value"));
            metaData.addIndex("MyRecord", "ind", field("header").nest(field("rec_no"), field("path")));
        };

        setupRecordsWithHeader(recordMetaDataHook, (i, record) -> {
            record.setStrValue("_" + i);
            record.getHeaderBuilder().setRecNo(i % 5).setPath("String" + i % 50).setNum(i);
        });

        List<String> ls = asList("String1", "String6", "String25", "String11");
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MyRecord")
                .setFilter(Query.field("header").matches(Query.and(
                        Query.field("rec_no").equalsValue(1L),
                        Query.field("path").in(ls))))
                .build();

        // Index(ind [EQUALS 1, EQUALS $__in_path__0]) WHERE __in_path__0 IN [String1, String6, String25, String11]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), indexScan(allOf(indexName("ind"), bounds(hasTupleString("[EQUALS 1, EQUALS $__in_path__0]"))))));
        assertEquals(1075745133, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-347576148, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(677597961, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        // result: [ "_1", "_51", "_56", "_6", "_11", "_61"]
        final Holder<byte[]> continuation = new Holder<>();
        queryRecordsWithHeader(recordMetaDataHook, plan, null, 10,
                cursor -> {
                    RecordCursorResult<TestRecordsWithHeaderProto.MyRecord.Builder> result = cursor.getNext();
                    assertEquals("_1", result.get().getStrValue());
                    continuation.value = result.getContinuation().toBytes();
                },
                TestHelpers::assertDiscardedNone);
        queryRecordsWithHeader(recordMetaDataHook, planner.plan(query),
                continuation.value, 10,
                cursor -> {
                    RecordCursorResult<TestRecordsWithHeaderProto.MyRecord.Builder> result = cursor.getNext();
                    assertEquals("_51", result.get().getStrValue());
                    result = cursor.getNext();
                    assertEquals("_56", result.get().getStrValue());
                    continuation.value = result.getContinuation().toBytes();
                },
                TestHelpers::assertDiscardedNone);
        RecordQuery query2 = RecordQuery.newBuilder()
                .setRecordType("MyRecord")
                .setFilter(Query.field("header").matches(Query.and(
                        Query.field("rec_no").equalsValue(1L),
                        Query.field("path").in(asList("String6", "String11")))))
                .build();
        // we miss _6
        // Note, Since we have two equals operands, the continuation ends up being relative to that
        // and is just the id, so we want the id of the continuation point from before ("_56") to be greater than the
        // first id of the new continuation ("_11")
        queryRecordsWithHeader(recordMetaDataHook,
                planner.plan(query2),
                continuation.value, 10,
                cursor -> {
                    RecordCursorResult<TestRecordsWithHeaderProto.MyRecord.Builder> result = cursor.getNext();
                    assertEquals("_11", result.get().getStrValue());
                    result = cursor.getNext();
                    assertEquals("_61", result.get().getStrValue());
                    result = cursor.getNext();
                    assertFalse(result.hasNext());
                },
                TestHelpers::assertDiscardedNone);
    }

    /**
     * Verify that one-of-them queries work with IN.
     */
    @Test
    public void testOneOfThemIn() throws Exception {
        RecordMetaDataHook recordMetaDataHook = metadata ->
                metadata.addIndex("MySimpleRecord", "ind", field("repeater", FanType.FanOut));
        setupSimpleRecordStore(recordMetaDataHook,
                (i, builder) -> builder.setRecNo(i).addAllRepeater(Arrays.asList(10 + i % 4, 20 + i % 4)));
        List<Integer> ls = Arrays.asList(13, 22);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("repeater").oneOfThem().in(ls))
                .build();

        // Index(ind [EQUALS $__in_repeater__0]) | UnorderedPrimaryKeyDistinct() WHERE __in_repeater__0 IN [13, 22]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), primaryKeyDistinct(
                indexScan(allOf(indexName("ind"), bounds(hasTupleString("[EQUALS $__in_repeater__0]")))))));
        assertEquals(503365581, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(936275728, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(953683456, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(50, querySimpleRecordStore(recordMetaDataHook, plan, EvaluationContext::empty,
                record -> assertThat(record.getRecNo() % 4, anyOf(is(3L), is(2L))),
                TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify that one-of-them queries work with IN (with binding).
     */
    @Test
    public void testOneOfThemInParameter() throws Exception {
        RecordMetaDataHook recordMetaDataHook = metadata ->
                metadata.addIndex("MySimpleRecord", "ind", field("repeater", FanType.FanOut));
        setupSimpleRecordStore(recordMetaDataHook,
                (i, builder) -> builder.setRecNo(i).addAllRepeater(Arrays.asList(10 + i % 4, 20 + i % 4)));
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("repeater").oneOfThem().in("values"))
                .build();

        // Index(ind [EQUALS $__in_repeater__0]) | UnorderedPrimaryKeyDistinct() WHERE __in_repeater__0 IN $values
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inParameter(equalTo("values"), primaryKeyDistinct(
                indexScan(allOf(indexName("ind"), bounds(hasTupleString("[EQUALS $__in_repeater__0]")))))));
        assertEquals(-320448635, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1463061327, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(1480470471, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(50, querySimpleRecordStore(recordMetaDataHook, plan,
                () -> EvaluationContext.forBinding("values", Arrays.asList(13L, 11L)),
                record -> assertThat(record.getRecNo() % 4, anyOf(is(3L), is(1L))),
                TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify that one-of-them queries work with IN when sorted on the repeated field.
     */
    @Test
    public void testOneOfThemInSorted() throws Exception {
        RecordMetaDataHook recordMetaDataHook = metadata ->
                metadata.addIndex("MySimpleRecord", "ind", field("repeater", FanType.FanOut));
        setupSimpleRecordStore(recordMetaDataHook,
                (i, builder) -> builder.setRecNo(i).addAllRepeater(Arrays.asList(10 + i % 4, 20 + i % 4)));
        List<Integer> ls = Arrays.asList(13, 22);
        List<Integer> reversed = new ArrayList<>(ls);
        Collections.reverse(reversed);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("repeater").oneOfThem().in(reversed))
                .setSort(field("repeater", FanType.FanOut))
                .build();
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), primaryKeyDistinct(
                indexScan(allOf(indexName("ind"), bounds(hasTupleString("[EQUALS $__in_repeater__0]")))))));
        assertEquals(503365582, plan.planHash());
        assertEquals(50, querySimpleRecordStore(recordMetaDataHook, plan, EvaluationContext::empty,
                record -> assertThat(record.getRecNo() % 4, anyOf(is(3L), is(2L))),
                TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify that IN works with grouped rank indexes.
     */
    @Test
    public void testRecordFunctionInGrouped() throws Exception {
        RecordMetaDataHook recordMetaDataHook = metadata ->
                metadata.addIndex("MySimpleRecord", new Index("rank_by_string", field("num_value_2").groupBy(field("str_value_indexed")),
                        IndexTypes.RANK));
        setupSimpleRecordStore(recordMetaDataHook,
                (i, builder) -> builder.setRecNo(i).setStrValueIndexed("str" + i % 4).setNumValue2(i + 100));

        List<Long> ls = Arrays.asList(1L, 3L, 5L);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.and(
                        Query.field("str_value_indexed").equalsValue("str0"),
                        Query.rank(Key.Expressions.field("num_value_2")
                                .groupBy(Key.Expressions.field("str_value_indexed")))
                                .in(ls)))
                .build();

        // Index(rank_by_string [EQUALS str0, EQUALS $__in_rank([Field { 'str_value_indexed' None}, Field { 'num_value_2' None}] group 1)__0] BY_RANK) WHERE __in_rank([Field { 'str_value_indexed' None}, Field { 'num_value_2' None}] group 1)__0 IN [1, 3, 5]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), indexScan(allOf(indexName("rank_by_string"), indexScanType(IndexScanType.BY_RANK)))));
        assertEquals(-778840248, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(1033565169, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-2129258919, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        List<Long> recNos = new ArrayList<>();
        querySimpleRecordStore(recordMetaDataHook, plan, EvaluationContext::empty,
                record -> recNos.add(record.getRecNo()),
                TestHelpers::assertDiscardedNone);
        assertEquals(Arrays.asList(4L, 12L, 20L), recNos);
    }

    /**
     * Verify that IN works with ungrouped rank indexes.
     */
    @Test
    public void testRecordFunctionInUngrouped() throws Exception {
        RecordMetaDataHook recordMetaDataHook = metadata ->
                metadata.addIndex("MySimpleRecord", new Index("rank", field("num_value_2").ungrouped(),
                        IndexTypes.RANK));
        setupSimpleRecordStore(recordMetaDataHook,
                (i, builder) -> builder.setRecNo(i).setStrValueIndexed("str" + i % 4).setNumValue2(i + 100));

        List<Long> ls = Arrays.asList(1L, 3L, 5L);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.rank("num_value_2").in(ls))
                .build();

        // Index(rank [EQUALS $__in_rank(Field { 'num_value_2' None} group 1)__0] BY_RANK) WHERE __in_rank(Field { 'num_value_2' None} group 1)__0 IN [1, 3, 5]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, inValues(equalTo(ls), indexScan(allOf(indexName("rank"), indexScanType(IndexScanType.BY_RANK)))));
        assertEquals(1518925028, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-2030955860, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(752828544, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        List<Long> recNos = new ArrayList<>();
        querySimpleRecordStore(recordMetaDataHook, plan, EvaluationContext::empty,
                record -> recNos.add(record.getRecNo()),
                TestHelpers::assertDiscardedNone);
        assertEquals(Arrays.asList(1L, 3L, 5L), recNos);
    }

    /**
     * Verify that IN queries can be planned using index scans, then used in a UNION to implement OR with an inequality
     * on the same field, and that the resulting union will be ordered by that field.
     */
    @Test
    public void testInQueryOr() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.or(
                        Query.field("num_value_unique").in(Arrays.asList(903, 905, 901)),
                        Query.field("num_value_unique").greaterThan(950)))
                .build();

        // Index(MySimpleRecord$num_value_unique [EQUALS $__in_num_value_unique__0]) WHERE __in_num_value_unique__0 IN [901, 903, 905] SORTED ∪[Field { 'num_value_unique' None}, Field { 'rec_no' None}] Index(MySimpleRecord$num_value_unique ([950],>)
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, union(
                indexScan("MySimpleRecord$num_value_unique"),
                inValues(equalTo(Arrays.asList(901, 903, 905)),
                        indexScan(allOf(indexName("MySimpleRecord$num_value_unique"), bounds(hasTupleString("[EQUALS $__in_num_value_unique__0]"))))),
                equalTo(concat(field("num_value_unique"), primaryKey("MySimpleRecord")))));
        assertEquals(1116661716, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-923557660, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(851868784, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(53, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> assertThat(record.getNumValueUnique(), anyOf(is(901), is(903), is(905), greaterThan(950))),
                TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify that IN queries can be planned using index scans, then used in a UNION to implement an OR with IN whose
     * elements overlap, and that the union with that comparison key deduplicates the records in the overlap.
     */
    @Test
    public void testInQueryOrOverlap() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.or(
                        Query.field("num_value_unique").in(Arrays.asList(903, 905, 901)),
                        Query.field("num_value_unique").in(Arrays.asList(906, 905, 904))))
                .build();

        // Index(MySimpleRecord$num_value_unique [EQUALS $__in_num_value_unique__0]) WHERE __in_num_value_unique__0 IN [901, 903, 905] SORTED ∪[Field { 'num_value_unique' None}, Field { 'rec_no' None}] Index(MySimpleRecord$num_value_unique [EQUALS $__in_num_value_unique__0]) WHERE __in_num_value_unique__0 IN [904, 905, 906] SORTED
        RecordQueryPlan plan = planner.plan(query);
        // Ordinary equality comparisons would be ordered just by the primary key so that would be the union comparison key.
        // Must compare the IN field here; they are ordered, but not trivially (same value for each).
        assertThat(plan, union(
                inValues(equalTo(Arrays.asList(901, 903, 905)),
                        indexScan(allOf(indexName("MySimpleRecord$num_value_unique"), bounds(hasTupleString("[EQUALS $__in_num_value_unique__0]"))))),
                inValues(equalTo(Arrays.asList(904, 905, 906)),
                        indexScan(allOf(indexName("MySimpleRecord$num_value_unique"), bounds(hasTupleString("[EQUALS $__in_num_value_unique__0]")))))));
        assertEquals(218263868, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-1594325702, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(2007968440, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        Set<Long> dupes = new HashSet<>();
        assertEquals(5, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> {
                    assertTrue(dupes.add(record.getRecNo()), "should not have duplicated records");
                    assertThat(record.getNumValueUnique(), anyOf(is(901), is(903), is(904), is(905), is(906)));
                }, context -> TestHelpers.assertDiscardedAtMost(1, context)));
    }

    /**
     * Verify that an IN requires an unordered union due to incompatible ordering.
     */
    @Test
    public void testInQueryOrDifferentCondition() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.or(
                        Query.field("num_value_unique").lessThan(910),
                        Query.and(
                                Query.field("num_value_unique").greaterThan(990),
                                Query.field("num_value_2").in(Arrays.asList(2, 0)))))
                .build();
        RecordQueryPlan plan = planner.plan(query);
        // Without the join, these would be using the same index and so compatible, even though inequalities.
        // TODO: IN join in filter can prevent index scan merging (https://github.com/FoundationDB/fdb-record-layer/issues/9)
        assertThat(plan, primaryKeyDistinct(unorderedUnion(
                indexScan(allOf(indexName("MySimpleRecord$num_value_unique"), bounds(hasTupleString("([null],[910])")))),
                inValues(equalTo(Arrays.asList(0, 2)), anyFilter(indexScan(allOf(indexName("MySimpleRecord$num_value_unique"), bounds(hasTupleString("([990],>"))))))
        )));
        assertEquals(16, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> {
                    assertThat(record.getNumValueUnique(), anyOf(lessThan(910), greaterThan(990)));
                    if (record.getNumValue3Indexed() > 990) {
                        assertThat(record.getNumValue2(), anyOf(is(2), is(0)));
                    }
                }, context -> TestHelpers.assertDiscardedAtMost(13, context)));
    }

    /**
     * Verify that an a complex query involving IN, AND, and OR is planned using a union of scans and joins on a
     * multi-field index, where the left subset has equality and the final field has an IN plus inequality on that same
     * field.
     */
    @Test
    public void testInQueryOrCompound() throws Exception {
        RecordMetaDataHook hook = complexQuerySetupHook();
        complexQuerySetup(hook);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.and(
                        Query.field("str_value_indexed").equalsValue("odd"),
                        Query.field("num_value_2").equalsValue(0),
                        Query.or(
                                Query.field("num_value_3_indexed").in(Arrays.asList(1, 3)),
                                Query.field("num_value_3_indexed").greaterThanOrEquals(4))))
                .build();

        // Index(multi_index [EQUALS odd, EQUALS 0, EQUALS $__in_num_value_3_indexed__0]) WHERE __in_num_value_3_indexed__0 IN [1, 3] SORTED ∪[Field { 'num_value_3_indexed' None}, Field { 'rec_no' None}] Index(multi_index [[odd, 0, 4],[odd, 0]])
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, union(
                inValues(equalTo(Arrays.asList(1, 3)),
                        indexScan(allOf(indexName("multi_index"), bounds(hasTupleString("[EQUALS odd, EQUALS 0, EQUALS $__in_num_value_3_indexed__0]"))))),
                indexScan(allOf(indexName("multi_index"), bounds(hasTupleString("[[odd, 0, 4],[odd, 0]]"))))));
        assertEquals(468569345, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(2017733085, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-1668679064, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(3 + 4 + 4, querySimpleRecordStore(hook, plan, EvaluationContext::empty,
                record -> {
                    assertThat(record.getStrValueIndexed(), is("odd"));
                    assertThat(record.getNumValue2(), is(0));
                    assertThat(record.getNumValue3Indexed(), anyOf(is(1), is(3), greaterThanOrEqualTo(4)));
                }, TestHelpers::assertDiscardedNone));
    }

    /**
     * Verify an IN clause prevents index usage because the IN loop is not compatible with index ordering.
     * TODO This should change.
     * TODO: IN join in filter can prevent index scan merging (https://github.com/FoundationDB/fdb-record-layer/issues/9)
     */
    @Test
    public void testInQueryOrMultipleIndexes() throws Exception {
        complexQuerySetup(NO_HOOK);
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.or(
                        Query.field("str_value_indexed").equalsValue("odd"),
                        Query.field("num_value_3_indexed").in(Arrays.asList(1, 3))))
                .build();
        RecordQueryPlan plan = planner.plan(query);
        // Two ordinary equals single-column index scans would be compatible on the following primary key, but
        // the IN loop inside one branch prevents that here. A regular filter would not.
        // TODO: IN join in filter can prevent index scan merging (https://github.com/FoundationDB/fdb-record-layer/issues/9)
        assertThat(plan, primaryKeyDistinct(unorderedUnion(
                indexScan(allOf(indexName("MySimpleRecord$str_value_indexed"), bounds(hasTupleString("[[odd],[odd]]")))),
                inValues(equalTo(Arrays.asList(1, 3)), indexScan(allOf(indexName("MySimpleRecord$num_value_3_indexed"), bounds(hasTupleString("[EQUALS $__in_num_value_3_indexed__0]")))))
        )));
        Set<Long> dupes = new HashSet<>();
        assertEquals(50 + 10 + 10, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty,
                record -> {
                    assertTrue(dupes.add(record.getRecNo()), "should not have duplicated records");
                    assertTrue(record.getStrValueIndexed().equals("odd") ||
                               record.getNumValue3Indexed() == 1 ||
                               record.getNumValue3Indexed() == 3);
                }, context -> TestHelpers.assertDiscardedAtMost(20, context)));
    }

    /**
     * Verify that enum field indexes are used to implement IN clauses.
     */
    @Test
    public void enumIn() throws Exception {
        RecordMetaDataHook hook = metaData -> {
            final RecordTypeBuilder type = metaData.getRecordType("MyShapeRecord");
            metaData.addIndex(type, new Index("color", field("color")));
        };
        setupEnumShapes(hook);

        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MyShapeRecord")
                .setFilter(Query.field("color").in(Arrays.asList(
                        TestRecordsEnumProto.MyShapeRecord.Color.RED,
                        TestRecordsEnumProto.MyShapeRecord.Color.BLUE)))
                .build();

        // Index(color [EQUALS $__in_color__0]) WHERE __in_color__0 IN [RED, BLUE]
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, descendant(indexScan("color")));
        assertFalse(plan.hasRecordScan(), "should not use record scan");
        assertEquals(-520431454, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-456008302, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-1863352727, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));

        try (FDBRecordContext context = openContext()) {
            openEnumRecordStore(context, hook);
            int i = 0;
            try (RecordCursorIterator<FDBQueriedRecord<Message>> cursor = recordStore.executeQuery(plan).asIterator()) {
                while (cursor.hasNext()) {
                    FDBQueriedRecord<Message> rec = cursor.next();
                    TestRecordsEnumProto.MyShapeRecord.Builder shapeRec = TestRecordsEnumProto.MyShapeRecord.newBuilder();
                    shapeRec.mergeFrom(rec.getRecord());
                    assertThat(shapeRec.getColor(), is(oneOf(TestRecordsEnumProto.MyShapeRecord.Color.RED, TestRecordsEnumProto.MyShapeRecord.Color.BLUE)));
                    i++;
                }
            }
            assertEquals(18, i);
            TestHelpers.assertDiscardedNone(context);
        }
    }

    /**
     * Verify that an IN with an empty list returns nothing.
     */
    @Test
    public void testInQueryEmptyList() throws Exception {
        complexQuerySetup(NO_HOOK);
        List<Integer> ls = Collections.emptyList();
        RecordQuery query = RecordQuery.newBuilder()
                .setRecordType("MySimpleRecord")
                .setFilter(Query.field("num_value_2").in(ls))
                .build();

        // Scan(<,>) | [MySimpleRecord] | num_value_2 IN []
        RecordQueryPlan plan = planner.plan(query);
        assertThat(plan, filter(query.getFilter(), descendant(scan(unbounded()))));
        assertEquals(-1139440895, plan.planHash(PlanHashable.PlanHashKind.LEGACY));
        assertEquals(-1907402540, plan.planHash(PlanHashable.PlanHashKind.FOR_CONTINUATION));
        assertEquals(-1694845095, plan.planHash(PlanHashable.PlanHashKind.STRUCTURAL_WITHOUT_LITERALS));
        assertEquals(0, querySimpleRecordStore(NO_HOOK, plan, EvaluationContext::empty, (rec) -> {
        }));
    }

}
