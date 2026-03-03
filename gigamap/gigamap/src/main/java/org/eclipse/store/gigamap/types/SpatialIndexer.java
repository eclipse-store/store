package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Indexing logic for geographic coordinates (latitude/longitude).
 * <p>
 * Decomposes each coordinate into 4 order-preserving bytes using fixed-point integer
 * encoding ({@code value * 10^7}, giving ~1.1cm precision) and uses the existing
 * {@link HashingCompositeIndexer} infrastructure with 8 sub-indices
 * (positions 0-3 for latitude, 4-7 for longitude).
 *
 * @param <E> the entity type
 */
public interface SpatialIndexer<E> extends HashingCompositeIndexer<E>
{
	static final double EARTH_RADIUS_KM = 6371.0;

	/**
	 * Creates a condition matching entities at the exact given coordinates.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param latitude the latitude
	 * @param longitude the longitude
	 * @return a condition for exact coordinate match
	 */
	public <S extends E> Condition<S> at(double latitude, double longitude);

	/**
	 * Creates a condition matching entities with null coordinates.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @return a condition for null coordinates
	 */
	public <S extends E> Condition<S> isNull();

	/**
	 * Creates a condition matching entities with latitude in the given inclusive range.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param minInclusive the minimum latitude (inclusive)
	 * @param maxInclusive the maximum latitude (inclusive)
	 * @return a condition for the latitude range
	 */
	public <S extends E> Condition<S> latitudeBetween(double minInclusive, double maxInclusive);

	/**
	 * Creates a condition matching entities with longitude in the given inclusive range.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param minInclusive the minimum longitude (inclusive)
	 * @param maxInclusive the maximum longitude (inclusive)
	 * @return a condition for the longitude range
	 */
	public <S extends E> Condition<S> longitudeBetween(double minInclusive, double maxInclusive);

	/**
	 * Creates a condition matching entities with latitude at or above the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param minInclusive the minimum latitude (inclusive)
	 * @return a condition for latitude >= minInclusive
	 */
	public <S extends E> Condition<S> latitudeAbove(double minInclusive);

	/**
	 * Creates a condition matching entities with latitude at or below the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param maxInclusive the maximum latitude (inclusive)
	 * @return a condition for {@code latitude <= maxInclusive}
	 */
	public <S extends E> Condition<S> latitudeBelow(double maxInclusive);

	/**
	 * Creates a condition matching entities with longitude at or above the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param minInclusive the minimum longitude (inclusive)
	 * @return a condition for longitude >= minInclusive
	 */
	public <S extends E> Condition<S> longitudeAbove(double minInclusive);

	/**
	 * Creates a condition matching entities with longitude at or below the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param maxInclusive the maximum longitude (inclusive)
	 * @return a condition for {@code longitude <= maxInclusive}
	 */
	public <S extends E> Condition<S> longitudeBelow(double maxInclusive);

	/**
	 * Creates a condition matching entities within the given bounding box.
	 * If {@code minLon > maxLon}, the box is treated as crossing the antimeridian.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param minLat the minimum latitude (inclusive)
	 * @param maxLat the maximum latitude (inclusive)
	 * @param minLon the minimum longitude (inclusive)
	 * @param maxLon the maximum longitude (inclusive)
	 * @return a condition for the bounding box
	 */
	public <S extends E> Condition<S> withinBox(double minLat, double maxLat, double minLon, double maxLon);

	/**
	 * Creates a condition matching entities within a bounding box approximation
	 * of the given radius around the given point. The bounding box is computed
	 * using a Haversine-based approximation and handles antimeridian wrapping.
	 * <p>
	 * For exact circular distance filtering, combine with {@link Abstract#withinRadius(double, double, double)}.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param latitude the center latitude
	 * @param longitude the center longitude
	 * @param radiusKm the radius in kilometers
	 * @return a condition for the proximity bounding box
	 */
	public <S extends E> Condition<S> near(double latitude, double longitude, double radiusKm);

	/**
	 * Computes the Haversine distance in kilometers between two geographic points.
	 *
	 * @param lat1 latitude of the first point
	 * @param lon1 longitude of the first point
	 * @param lat2 latitude of the second point
	 * @param lon2 longitude of the second point
	 * @return the distance in kilometers
	 */
	public static double haversineDistance(
		final double lat1, final double lon1,
		final double lat2, final double lon2
	)
	{
		final double dLat = Math.toRadians(lat2 - lat1);
		final double dLon = Math.toRadians(lon2 - lon1);
		final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}


	/**
	 * Abstract base class for a spatial coordinate {@link Indexer}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends HashingCompositeIndexer.Abstract<E> implements SpatialIndexer<E>
	{
		private static final int LAT_OFFSET      = 0;
		private static final int LON_OFFSET      = 4;
		private static final int COMPOSITE_SIZE  = 8; // Integer.BYTES * 2
		private static final int SCALE           = 10_000_000; // 10^7 -> ~1.1cm precision
		private static final int BYTES_PER_COORD = Integer.BYTES;

		protected Abstract()
		{
			super();
		}

		/**
		 * Extracts the latitude from the given entity. Return {@code null} if the entity has no coordinates.
		 * If latitude is null, longitude must also be null.
		 *
		 * @param entity the entity
		 * @return the latitude, or null
		 */
		protected abstract Double getLatitude(E entity);

		/**
		 * Extracts the longitude from the given entity. Return {@code null} if the entity has no coordinates.
		 * If longitude is null, latitude must also be null.
		 *
		 * @param entity the entity
		 * @return the longitude, or null
		 */
		protected abstract Double getLongitude(E entity);

		@Override
		public Object[] index(final E entity, final Object[] carrier)
		{
			final Double lat = this.getLatitude(entity);
			final Double lon = this.getLongitude(entity);

			if(lat == null && lon == null)
			{
				return NULL();
			}
			if(lat == null || lon == null)
			{
				throw new IllegalArgumentException("Latitude and longitude must both be null or both be non-null");
			}
			if(Double.isNaN(lat) || Double.isNaN(lon) || Double.isInfinite(lat) || Double.isInfinite(lon))
			{
				throw new IllegalArgumentException("Latitude and longitude must be finite numbers");
			}
			if(lat < -90.0 || lat > 90.0)
			{
				throw new IllegalArgumentException("Latitude must be between -90 and 90, but was " + lat);
			}
			if(lon < -180.0 || lon > 180.0)
			{
				throw new IllegalArgumentException("Longitude must be between -180 and 180, but was " + lon);
			}

			Object[] c = carrier;
			if(c == null || c.length != COMPOSITE_SIZE)
			{
				c = new Object[COMPOSITE_SIZE];
			}
			else
			{
				Arrays.fill(c, null);
			}

			fillCarrier(lat, lon, c);
			return c;
		}

		@Override
		public <S extends E> Condition<S> isNull()
		{
			return this.is(NULL());
		}

		@Override
		public <S extends E> Condition<S> at(final double latitude, final double longitude)
		{
			final Object[] carrier = new Object[COMPOSITE_SIZE];
			fillCarrier(latitude, longitude, carrier);
			return this.is(carrier);
		}

		@Override
		public <S extends E> Condition<S> latitudeAbove(final double minInclusive)
		{
			return this.coordinateGreaterThanEqual(toUnsignedBytes(minInclusive), LAT_OFFSET);
		}

		@Override
		public <S extends E> Condition<S> latitudeBelow(final double maxInclusive)
		{
			return this.coordinateLessThanEqual(toUnsignedBytes(maxInclusive), LAT_OFFSET);
		}

		@Override
		public <S extends E> Condition<S> longitudeAbove(final double minInclusive)
		{
			return this.coordinateGreaterThanEqual(toUnsignedBytes(minInclusive), LON_OFFSET);
		}

		@Override
		public <S extends E> Condition<S> longitudeBelow(final double maxInclusive)
		{
			return this.coordinateLessThanEqual(toUnsignedBytes(maxInclusive), LON_OFFSET);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S extends E> Condition<S> latitudeBetween(final double minInclusive, final double maxInclusive)
		{
			return (Condition<S>)this.latitudeAbove(minInclusive).and(this.latitudeBelow(maxInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S extends E> Condition<S> longitudeBetween(final double minInclusive, final double maxInclusive)
		{
			return (Condition<S>)this.longitudeAbove(minInclusive).and(this.longitudeBelow(maxInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S extends E> Condition<S> withinBox(
			final double minLat, final double maxLat,
			final double minLon, final double maxLon
		)
		{
			if(minLon > maxLon)
			{
				// Crosses antimeridian: split into two longitude ranges
				return (Condition<S>)this.latitudeBetween(minLat, maxLat).and(
					this.longitudeBetween(minLon, 180.0).or(this.longitudeBetween(-180.0, maxLon))
				);
			}

			return (Condition<S>)this.latitudeBetween(minLat, maxLat).and(this.longitudeBetween(minLon, maxLon));
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S extends E> Condition<S> near(
			final double latitude, final double longitude, final double radiusKm
		)
		{
			final double latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);

			final double minLat = Math.max(latitude - latDelta, -90.0);
			final double maxLat = Math.min(latitude + latDelta,  90.0);

			// Near the poles, cos(latitude) approaches zero making lonDelta infinite.
			// In this case, restrict only by latitude and allow full longitude range.
			if(Math.abs(latitude) > 89.9)
			{
				return (Condition<S>)this.latitudeBetween(minLat, maxLat);
			}

			final double lonDelta = Math.toDegrees(
				radiusKm / (EARTH_RADIUS_KM * Math.cos(Math.toRadians(latitude)))
			);
			final double minLon = longitude - lonDelta;
			final double maxLon = longitude + lonDelta;

			if(minLon < -180.0)
			{
				// Wraps west past antimeridian
				return (Condition<S>)this.latitudeBetween(minLat, maxLat).and(
					this.longitudeBetween(minLon + 360.0, 180.0)
					.or(this.longitudeBetween(-180.0, maxLon))
				);
			}
			else if(maxLon > 180.0)
			{
				// Wraps east past antimeridian
				return (Condition<S>)this.latitudeBetween(minLat, maxLat).and(
					this.longitudeBetween(minLon, 180.0)
					.or(this.longitudeBetween(-180.0, maxLon - 360.0))
				);
			}

			return this.withinBox(minLat, maxLat, minLon, maxLon);
		}

		/**
		 * Creates a predicate that tests whether an entity is within the given radius
		 * of a point using Haversine distance. Intended for post-filtering query results
		 * from {@link #near(double, double, double)}.
		 *
		 * @param latitude the center latitude
		 * @param longitude the center longitude
		 * @param radiusKm the radius in kilometers
		 * @return a predicate for exact circular distance filtering
		 */
		public Predicate<E> withinRadius(
			final double latitude, final double longitude, final double radiusKm
		)
		{
			return entity ->
			{
				final Double eLat = this.getLatitude(entity);
				final Double eLon = this.getLongitude(entity);
				if(eLat == null || eLon == null)
				{
					return false;
				}
				return haversineDistance(latitude, longitude, eLat, eLon) <= radiusKm;
			};
		}


		// ---- internal range query building ----

		@SuppressWarnings({"unchecked", "rawtypes"})
		private <S extends E> Condition<S> coordinateLessThan(final int[] boundValues, final int startOffset)
		{
			Condition result = this.is(new FieldPredicate(
				startOffset,
				b -> b < boundValues[0]
			));
			for(int i = 1; i < BYTES_PER_COORD; i++)
			{
				final int byteIdx = i;
				result = result.or(
					this.is(new EqualsUntilPredicate(startOffset, startOffset + i - 1, boundValues))
					.and(this.is(new FieldPredicate(
						startOffset + i,
						b -> b < boundValues[byteIdx]
					)))
				);
			}
			return result;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private <S extends E> Condition<S> coordinateGreaterThan(final int[] boundValues, final int startOffset)
		{
			Condition result = this.is(new FieldPredicate(
				startOffset,
				b -> b > boundValues[0]
			));
			for(int i = 1; i < BYTES_PER_COORD; i++)
			{
				final int byteIdx = i;
				result = result.or(
					this.is(new EqualsUntilPredicate(startOffset, startOffset + i - 1, boundValues))
					.and(this.is(new FieldPredicate(
						startOffset + i,
						b -> b > boundValues[byteIdx]
					)))
				);
			}
			return result;
		}

		@SuppressWarnings("unchecked")
		private <S extends E> Condition<S> coordinateLessThanEqual(final int[] boundValues, final int startOffset)
		{
			return (Condition<S>)this.coordinateIsEqual(boundValues, startOffset)
				.or(this.coordinateLessThan(boundValues, startOffset));
		}

		@SuppressWarnings("unchecked")
		private <S extends E> Condition<S> coordinateGreaterThanEqual(final int[] boundValues, final int startOffset)
		{
			return (Condition<S>)this.coordinateIsEqual(boundValues, startOffset)
				.or(this.coordinateGreaterThan(boundValues, startOffset));
		}

		private <S extends E> Condition<S> coordinateIsEqual(final int[] boundValues, final int startOffset)
		{
			final Object[] carrier = new Object[COMPOSITE_SIZE];
			for(int i = 0; i < BYTES_PER_COORD; i++)
			{
				carrier[startOffset + i] = boundValues[i];
			}
			// Non-relevant positions remain null, which ObjectSampleBased skips
			return this.is(carrier);
		}


		// ---- encoding ----

		private static void fillCarrier(final double latitude, final double longitude, final Object[] carrier)
		{
			final int[] unsignedBytes = new int[BYTES_PER_COORD];

			coordinateToUnsignedBytes(latitude, unsignedBytes);
			for(int i = 0; i < BYTES_PER_COORD; i++)
			{
				carrier[LAT_OFFSET + i] = unsignedBytes[i];
			}

			coordinateToUnsignedBytes(longitude, unsignedBytes);
			for(int i = 0; i < BYTES_PER_COORD; i++)
			{
				carrier[LON_OFFSET + i] = unsignedBytes[i];
			}
		}

		private static int[] toUnsignedBytes(final double value)
		{
			final int[] values = new int[BYTES_PER_COORD];
			coordinateToUnsignedBytes(value, values);
			return values;
		}

		private static void coordinateToUnsignedBytes(final double value, final int[] target)
		{
			final int fixed   = (int)(value * SCALE);
			final int ordered = fixed ^ 0x80000000;
			target[0] = (ordered >>> 24) & 0xFF;
			target[1] = (ordered >>> 16) & 0xFF;
			target[2] = (ordered >>>  8) & 0xFF;
			target[3] =  ordered         & 0xFF;
		}


		// ---- predicate classes ----

		static class FieldPredicate implements CompositePredicate<Object[]>
		{
			final int          subKeyPosition;
			final IntPredicate predicate;

			FieldPredicate(final int subKeyPosition, final IntPredicate predicate)
			{
				this.subKeyPosition = subKeyPosition;
				this.predicate      = predicate;
			}

			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition == this.subKeyPosition;
			}

			@Override
			public boolean test(final Object[] keys)
			{
				return this.test(this.subKeyPosition, keys[this.subKeyPosition]);
			}

			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition == this.subKeyPosition
					&& subKey instanceof Integer
					&& this.predicate.test((Integer)subKey);
			}
		}


		static class EqualsUntilPredicate implements CompositePredicate<Object[]>
		{
			final int   startOffset;
			final int   maxSubKeyPosition;
			final int[] boundValues;

			EqualsUntilPredicate(
				final int   startOffset,
				final int   maxSubKeyPosition,
				final int[] boundValues
			)
			{
				this.startOffset       = startOffset;
				this.maxSubKeyPosition = maxSubKeyPosition;
				this.boundValues       = boundValues;
			}

			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition >= this.startOffset && subKeyPosition <= this.maxSubKeyPosition;
			}

			@Override
			public boolean test(final Object[] keys)
			{
				for(int i = this.startOffset; i <= this.maxSubKeyPosition; i++)
				{
					if(!this.test(i, keys[i]))
					{
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition >= this.startOffset
					&& subKeyPosition <= this.maxSubKeyPosition
					&& subKey instanceof Integer
					&& this.boundValues[subKeyPosition - this.startOffset] == (Integer)subKey;
			}
		}

	}

}
