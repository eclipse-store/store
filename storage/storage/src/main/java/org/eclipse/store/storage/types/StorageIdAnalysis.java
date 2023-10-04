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

public interface StorageIdAnalysis
{
	public XGettingTable<Persistence.IdType, Long> highestIdsPerType();
	
	public XGettingEnum<Long> occuringTypeIds();


	public static StorageIdAnalysis Empty()
	{
		return new StorageIdAnalysis.Default(X.emptyTable(), null);
	}

	public static StorageIdAnalysis New(final Long highestTid, final Long highestOid, final Long highestCid)
	{
		return New(
			highestTid,
			highestOid,
			highestCid,
			null
		);
	}
	
	public static StorageIdAnalysis New(
		final Long               highestTid     ,
		final Long               highestOid     ,
		final Long               highestCid     ,
		final XGettingEnum<Long> occuringTypeIds
	)
	{
		return New(
				ConstHashTable.New(
				X.KeyValue(Persistence.IdType.TID, highestTid),
				X.KeyValue(Persistence.IdType.OID, highestOid),
				X.KeyValue(Persistence.IdType.CID, highestCid)
			),
				occuringTypeIds
		);
	}

	public static StorageIdAnalysis New(
		final XGettingSequence<KeyValue<Persistence.IdType, Long>> values         ,
		final XGettingEnum<Long>                               occuringTypeIds
	)
	{
		return new StorageIdAnalysis.Default(
			ConstHashTable.New(notNull(values)),
			occuringTypeIds == null
				? X.empty()
				: EqHashEnum.New(occuringTypeIds)
		);
	}

	public final class Default implements StorageIdAnalysis
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final XGettingTable<Persistence.IdType, Long> highestIdsPerType;
		final XGettingEnum<Long>                      occuringTypeIds  ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final XGettingTable<Persistence.IdType, Long> highestIdsPerType,
			final XGettingEnum<Long>          occuringTypeIds
		)
		{
			super();
			this.highestIdsPerType = highestIdsPerType;
			this.occuringTypeIds   = occuringTypeIds  ;
		}

		@Override
		public final XGettingTable<Persistence.IdType, Long> highestIdsPerType()
		{
			return this.highestIdsPerType;
		}
		
		@Override
		public final XGettingEnum<Long> occuringTypeIds()
		{
			return this.occuringTypeIds;
		}

	}

}
