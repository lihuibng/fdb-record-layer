/*
 * GeoPointWithinDistanceComponent.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2019 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.record.spatial.common;

import com.apple.foundationdb.annotation.API;
import com.apple.foundationdb.record.EvaluationContext;
import com.apple.foundationdb.record.PlanHashable;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecord;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStoreBase;
import com.apple.foundationdb.record.query.expressions.ComponentWithNoChildren;
import com.apple.foundationdb.record.query.expressions.Query;
import com.apple.foundationdb.record.query.plan.temp.ExpressionRef;
import com.apple.foundationdb.record.query.plan.temp.PlannerExpression;
import com.apple.foundationdb.record.query.plan.temp.view.Source;
import com.apple.foundationdb.record.query.predicates.QueryPredicate;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Query filter for points (latitude, longitude) within a given distance of a given center.
 */
@API(API.Status.EXPERIMENTAL)
public class GeoPointWithinDistanceComponent implements ComponentWithNoChildren {
    @Nonnull
    private final DoubleValueOrParameter centerLatitude;
    @Nonnull
    private final DoubleValueOrParameter centerLongitude;
    @Nonnull
    private final DoubleValueOrParameter distance;
    @Nonnull
    private final String latitudeFieldName;
    @Nonnull
    private final String longitudeFieldName;
    @Nonnull
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public GeoPointWithinDistanceComponent(@Nonnull DoubleValueOrParameter centerLatitude, @Nonnull DoubleValueOrParameter centerLongitude,
                                           @Nonnull DoubleValueOrParameter distance,
                                           @Nonnull String latitudeFieldName, @Nonnull String longitudeFieldName) {
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
        this.distance = distance;
        this.latitudeFieldName = latitudeFieldName;
        this.longitudeFieldName = longitudeFieldName;
    }

    @Nullable
    @Override
    public <M extends Message> Boolean evalMessage(@Nonnull FDBRecordStoreBase<M> store, @Nonnull EvaluationContext context, @Nullable FDBRecord<M> record, @Nullable Message message) {
        Double distanceValue = distance.getValue(context);
        Double centerLatitudeValue = centerLatitude.getValue(context);
        Double centerLongitudeValue = centerLongitude.getValue(context);
        if (distanceValue == null || centerLatitudeValue == null || centerLongitudeValue == null) {
            return null;
        }
        Double pointLatitudeValue = getCoordinateField(message, latitudeFieldName);
        Double pointLongitudeValue = getCoordinateField(message, longitudeFieldName);
        if (pointLatitudeValue == null || pointLongitudeValue == null) {
            return null;
        }
        Geometry center = geometryFactory.createPoint(new Coordinate(centerLatitudeValue, centerLongitudeValue));
        Geometry point = geometryFactory.createPoint(new Coordinate(pointLatitudeValue, pointLongitudeValue));
        return point.isWithinDistance(center, distanceValue);
    }

    @Nullable
    private Double getCoordinateField(@Nullable Message message, @Nonnull String fieldName) {
        if (message == null) {
            return null;
        }
        final Descriptors.FieldDescriptor field = message.getDescriptorForType().findFieldByName(fieldName);
        if (field == null) {
            throw new Query.InvalidExpressionException("Missing field " + fieldName);
        }
        return (Double)message.getField(field);
    }

    @Override
    public void validate(@Nonnull Descriptors.Descriptor descriptor) {
        validateCoordinateField(descriptor, latitudeFieldName);
        validateCoordinateField(descriptor, longitudeFieldName);
    }

    private void validateCoordinateField(@Nonnull Descriptors.Descriptor descriptor, @Nonnull String fieldName) {
        Descriptors.FieldDescriptor field = descriptor.findFieldByName(fieldName);
        if (field == null) {
            throw new Query.InvalidExpressionException("Missing field " + fieldName);
        }
        if (field.getJavaType() != Descriptors.FieldDescriptor.JavaType.DOUBLE) {
            throw new Query.InvalidExpressionException("Required double field for " + fieldName);
        }
        if (field.isRepeated()) {
            throw new Query.InvalidExpressionException("Required scalar field, but got repeated field " + fieldName);
        }
    }

    @Nonnull
    @Override
    public Iterator<? extends ExpressionRef<? extends PlannerExpression>> getPlannerExpressionChildren() {
        return Collections.emptyIterator();
    }

    @Nonnull
    @Override
    public QueryPredicate normalizeForPlanner(@Nonnull Source rootSource, @Nonnull List<String> fieldNamePrefix) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public int planHash() {
        return PlanHashable.objectsPlanHash(centerLatitude, centerLongitude, distance, latitudeFieldName, longitudeFieldName);
    }

    @Override
    public String toString() {
        return String.format("(%s,%s) WITHIN %s OF (%s,%s)", latitudeFieldName, longitudeFieldName, distance, centerLatitude, centerLongitude);
    }

    @Override
    public boolean equalsWithoutChildren(@Nonnull PlannerExpression otherExpression) {
        return equals(otherExpression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeoPointWithinDistanceComponent that = (GeoPointWithinDistanceComponent)o;
        return centerLatitude.equals(that.centerLatitude) &&
               centerLongitude.equals(that.centerLongitude) &&
               distance.equals(that.distance) &&
               latitudeFieldName.equals(that.latitudeFieldName) &&
               longitudeFieldName.equals(that.longitudeFieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerLatitude, centerLongitude, distance, latitudeFieldName, longitudeFieldName);
    }
}
