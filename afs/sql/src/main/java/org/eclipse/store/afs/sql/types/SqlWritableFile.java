package org.eclipse.store.afs.sql.types;

/*-
 * #%L
 * EclipseStore Abstract File System SQL
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

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AWritableFile;

public interface SqlWritableFile extends SqlReadableFile, AWritableFile
{

    public static SqlWritableFile New(
    	final AFile actual ,
    	final Object  user   ,
    	final SqlPath path
    )
    {
        return new SqlWritableFile.Default<>(
            notNull(actual) ,
            notNull(user)   ,
            notNull(path)
        );
    }


	public class Default<U> extends SqlReadableFile.Default<U> implements SqlWritableFile
    {
		protected Default(
			final AFile   actual ,
			final U       user   ,
			final SqlPath path
		)
		{
			super(actual, user, path);
		}

    }

}
