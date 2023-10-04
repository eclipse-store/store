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

import org.eclipse.store.storage.exceptions.StorageException;


public enum StorageTransactionsEntryType
{
	FILE_CREATION  ("CREATION"  , StorageTransactionsAnalysis.Logic.TYPE_FILE_CREATION  , StorageTransactionsAnalysis.Logic.LENGTH_FILE_CREATION  ),
	DATA_STORE     ("STORE"     , StorageTransactionsAnalysis.Logic.TYPE_STORE          , StorageTransactionsAnalysis.Logic.LENGTH_STORE          ),
	DATA_TRANSFER  ("TRANSFER"  , StorageTransactionsAnalysis.Logic.TYPE_TRANSFER       , StorageTransactionsAnalysis.Logic.LENGTH_TRANSFER       ),
	FILE_TRUNCATION("TRUNCATION", StorageTransactionsAnalysis.Logic.TYPE_FILE_TRUNCATION, StorageTransactionsAnalysis.Logic.LENGTH_FILE_TRUNCATION),
	FILE_DELETION  ("DELETION"  , StorageTransactionsAnalysis.Logic.TYPE_FILE_DELETION  , StorageTransactionsAnalysis.Logic.LENGTH_FILE_DELETION  );
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final String typeName;
	private final byte   code    ;
	private final int    length  ;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	private StorageTransactionsEntryType(final String typeName, final byte code, final int length)
	{
		this.typeName = typeName;
		this.code     = code    ;
		this.length   = length  ;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	public byte code()
	{
		return this.code;
	}
	
	public String typeName()
	{
		return this.typeName;
	}
	
	public int length()
	{
		return this.length;
	}
	
	@Override
	public String toString()
	{
		return this.typeName + "(" + this.code + "," + this.length + ")";
	}
	
	public static StorageTransactionsEntryType fromCode(final byte code)
	{
		switch(code)
		{
			case StorageTransactionsAnalysis.Logic.TYPE_FILE_CREATION  : return StorageTransactionsEntryType.FILE_CREATION  ;
			case StorageTransactionsAnalysis.Logic.TYPE_STORE          : return StorageTransactionsEntryType.DATA_STORE     ;
			case StorageTransactionsAnalysis.Logic.TYPE_TRANSFER       : return StorageTransactionsEntryType.DATA_TRANSFER  ;
			case StorageTransactionsAnalysis.Logic.TYPE_FILE_TRUNCATION: return StorageTransactionsEntryType.FILE_TRUNCATION;
			case StorageTransactionsAnalysis.Logic.TYPE_FILE_DELETION  : return StorageTransactionsEntryType.FILE_DELETION  ;
			default:
			{
				throw new StorageException("Unknown transactions entry type: " + code);
			}
		}
	}
	
}
