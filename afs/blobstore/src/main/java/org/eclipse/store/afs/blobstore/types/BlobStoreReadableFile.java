package org.eclipse.store.afs.blobstore.types;

/*-
 * #%L
 * afs-blobstore
 * %%
 * Copyright (C) 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AReadableFile;

public interface BlobStoreReadableFile extends AReadableFile, BlobStoreFileWrapper
{
    public static BlobStoreReadableFile New(
    	final AFile         actual,
    	final Object        user  ,
    	final BlobStorePath path
    )
    {
        return new BlobStoreReadableFile.Default<>(
            notNull(actual),
            notNull(user)  ,
            notNull(path)
        );
    }


	public class Default<U> extends BlobStoreFileWrapper.Abstract<U> implements BlobStoreReadableFile
	{
		protected Default(
			final AFile actual,
			final U             user  ,
			final BlobStorePath path
		)
		{
			super(actual, user, path);
		}

	}

}
