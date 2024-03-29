package org.eclipse.store.afs.oraclecloud.objectstorage.types;

/*-
 * #%L
 * EclipseStore Abstract File System Oracle Cloud ObjectStorage
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

import java.util.regex.Pattern;

import org.eclipse.store.afs.blobstore.types.BlobStorePath;

public interface OracleCloudObjectStoragePathValidator extends BlobStorePath.Validator
{

	public static OracleCloudObjectStoragePathValidator New()
	{
		return new OracleCloudObjectStoragePathValidator.Default();
	}


	public static class Default implements OracleCloudObjectStoragePathValidator
	{
		Default()
		{
			super();
		}

		@Override
		public void validate(
			final BlobStorePath path
		)
		{
			this.validateBucketName(path.container());

		}

		/*
		 * No documentation found for bucket naming limitations.
		 * This is just the check taken from the console web interface.
		 */
		void validateBucketName(
			final String bucketName
		)
		{
			if(!Pattern.matches(
				"[a-zA-Z0-9_\\-]*",
				bucketName
			))
			{
				throw new IllegalArgumentException(
					"bucket name can contain only letters, numbers, underscores (_) and dashes (-)"
				);
			}
		}

	}

}
