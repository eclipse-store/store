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

import org.eclipse.serializer.persistence.types.Unpersistable;

final class TypeInFile implements Unpersistable
{
	final StorageEntityType.Default   type    ;
	final StorageLiveDataFile.Default file    ;
	      TypeInFile                  hashNext;
	
	TypeInFile(
		final StorageEntityType.Default   type    ,
		final StorageLiveDataFile.Default file    ,
		final TypeInFile                  hashNext
	)
	{
		super();
		this.type     = type    ;
		this.file     = file    ;
		this.hashNext = hashNext;
	}

}
