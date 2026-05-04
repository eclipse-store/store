package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.collections.ConstHashTable;
import org.eclipse.serializer.collections.EqHashEnum;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.X;

/**
 * Result of an id-analysis pass over a storage's persisted entities.
 * <p>
 * The analysis carries the highest observed value for each {@link Persistence.IdType} (type id,
 * object id, constant id) and the set of type ids that were actually encountered. The storage
 * uses these values during startup to seed its id provider so that newly assigned ids do not
 * collide with previously persisted ones, and to detect type ids in the persisted data that are
 * no longer registered.
 *
 * @see Persistence.IdType
 */
public interface StorageIdAnalysis
{
	/**
	 * Returns the highest observed id per {@link Persistence.IdType}, indexed by id type.
	 *
	 * @return a table mapping each {@link Persistence.IdType} to the highest id of that kind seen
	 *         during analysis.
	 */
	public XGettingTable<Persistence.IdType, Long> highestIdsPerType();

	/**
	 * Returns the set of type ids that were actually encountered during analysis.
	 *
	 * @return the encountered type ids; never {@code null}, but may be empty.
	 */
	public XGettingEnum<Long> occurringTypeIds();


	/**
	 * Returns a shared empty {@link StorageIdAnalysis} instance with no recorded ids.
	 *
	 * @return an empty {@link StorageIdAnalysis}.
	 */
	public static StorageIdAnalysis Empty()
	{
		return new StorageIdAnalysis.Default(X.emptyTable(), null);
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageIdAnalysis} from the passed highest
	 * id values, with an unknown set of occurring type ids.
	 *
	 * @param highestTid the highest type id observed, or {@code null} if not available.
	 * @param highestOid the highest object id observed, or {@code null} if not available.
	 * @param highestCid the highest constant id observed, or {@code null} if not available.
	 *
	 * @return a new {@link StorageIdAnalysis}.
	 */
	public static StorageIdAnalysis New(final Long highestTid, final Long highestOid, final Long highestCid)
	{
		return New(
			highestTid,
			highestOid,
			highestCid,
			null
		);
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageIdAnalysis} from the passed highest
	 * id values and the set of occurring type ids.
	 *
	 * @param highestTid       the highest type id observed, or {@code null} if not available.
	 * @param highestOid       the highest object id observed, or {@code null} if not available.
	 * @param highestCid       the highest constant id observed, or {@code null} if not available.
	 * @param occurringTypeIds the set of encountered type ids, or {@code null} for an empty set.
	 *
	 * @return a new {@link StorageIdAnalysis}.
	 */
	public static StorageIdAnalysis New(
		final Long               highestTid      ,
		final Long               highestOid      ,
		final Long               highestCid      ,
		final XGettingEnum<Long> occurringTypeIds
	)
	{
		return New(
			ConstHashTable.New(
				X.KeyValue(Persistence.IdType.TID, highestTid),
				X.KeyValue(Persistence.IdType.OID, highestOid),
				X.KeyValue(Persistence.IdType.CID, highestCid)
			),
			occurringTypeIds
		);
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageIdAnalysis} from a generic sequence of
	 * id-type to highest-id mappings and the set of occurring type ids.
	 *
	 * @param values           the id-type to highest-id mappings; must be non-{@code null}.
	 * @param occurringTypeIds the set of encountered type ids, or {@code null} for an empty set.
	 *
	 * @return a new {@link StorageIdAnalysis}.
	 */
	public static StorageIdAnalysis New(
		final XGettingSequence<KeyValue<Persistence.IdType, Long>> values          ,
		final XGettingEnum<Long>                                   occurringTypeIds
	)
	{
		return new StorageIdAnalysis.Default(
			ConstHashTable.New(notNull(values)),
			occurringTypeIds == null
				? X.empty()
				: EqHashEnum.New(occurringTypeIds)
		);
	}

	/**
	 * Default immutable {@link StorageIdAnalysis} implementation: a value holder for the highest
	 * ids per type and the set of occurring type ids.
	 */
	public final class Default implements StorageIdAnalysis
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final XGettingTable<Persistence.IdType, Long> highestIdsPerType;
		final XGettingEnum<Long>                      occurringTypeIds ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final XGettingTable<Persistence.IdType, Long> highestIdsPerType,
			final XGettingEnum<Long>                      occurringTypeIds
		)
		{
			super();
			this.highestIdsPerType = highestIdsPerType;
			this.occurringTypeIds  = occurringTypeIds ;
		}

		@Override
		public final XGettingTable<Persistence.IdType, Long> highestIdsPerType()
		{
			return this.highestIdsPerType;
		}
		
		@Override
		public final XGettingEnum<Long> occurringTypeIds()
		{
			return this.occurringTypeIds;
		}

	}

}
