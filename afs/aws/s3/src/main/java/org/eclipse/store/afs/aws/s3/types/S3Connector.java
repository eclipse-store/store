package org.eclipse.store.afs.aws.s3.types;

/*-
 * #%L
 * EclipseStore Abstract File System AWS S3
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

import static java.util.stream.Collectors.toList;
import static org.eclipse.serializer.util.X.notNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.serializer.io.ByteBufferInputStream;
import org.eclipse.store.afs.blobstore.types.BlobStoreConnector;
import org.eclipse.store.afs.blobstore.types.BlobStorePath;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Connector for the <a href="https://aws.amazon.com/s3/">Amazon Simple Storage Service (Amazon S3)</a>.
 * <p>
 * First create a connection to the <a href="https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/s3-examples.html">S3 storage</a>.
 * <pre>
 * S3Client client = ...
 * BlobStoreFileSystem fileSystem = BlobStoreFileSystem.New(
 * 	S3Connector.Caching(client)
 * );
 * </pre>
 *
 * <p>
 * If you are using general purpose buckets, create a connector with {@link #New(S3Client)} or {@link #Caching(S3Client)},
 * if you want to use directory buckets, like Express One Zone, use {@link #NewDirectory(S3Client)} or {@link #CachingDirectory(S3Client)}.
 *
 */
public interface S3Connector extends BlobStoreConnector
{
	/**
	 * Pseudo-constructor method which creates a new {@link S3Connector} for general purpose buckets.
	 *
	 * @param s3 connection to the S3 storage
	 * @return a new {@link S3Connector}
	 */
	public static S3Connector New(
		final S3Client s3
	)
	{
		return new S3Connector.Default(
			notNull(s3),
			false
		);
	}
	
	/**
	 * Pseudo-constructor method which creates a new {@link S3Connector} with cache for general purpose buckets.
	 *
	 * @param s3 connection to the S3 storage
	 * @return a new {@link S3Connector}
	 */
	public static S3Connector Caching(
		final S3Client s3
	)
	{
		return new S3Connector.Default(
			notNull(s3),
			true
		);
	}
	/**
	 * Pseudo-constructor method which creates a new {@link S3Connector} for directory buckets.
	 *
	 * @param s3 connection to the S3 storage
	 * @return a new {@link S3Connector}
	 */
	public static S3Connector NewDirectory(
		final S3Client s3
	)
	{
		return new S3Connector.Directory(
			notNull(s3),
			false
		);
	}
	
	/**
	 * Pseudo-constructor method which creates a new {@link S3Connector} with cache for directory buckets.
	 *
	 * @param s3 connection to the S3 storage
	 * @return a new {@link S3Connector}
	 */
	public static S3Connector CachingDirectory(
		final S3Client s3
	)
	{
		return new S3Connector.Directory(
			notNull(s3),
			true
		);
	}


	public static class Default
	extends    BlobStoreConnector.Abstract<S3Object>
	implements S3Connector
	{
		protected final S3Client s3;

		Default(
			final S3Client s3      ,
			final boolean  useCache
		)
		{
			super(
				S3Object::key,
				S3Object::size,
				S3PathValidator.New(),
				useCache
			);
			this.s3 = s3;
		}

		@Override
		protected Stream<S3Object> blobs(final BlobStorePath file)
		{
			final String         prefix            = toBlobKeyPrefix(file);
			final Pattern        pattern           = Pattern.compile(blobKeyRegex(prefix));
			final List<S3Object> blobs             = new ArrayList<>();
			String               continuationToken = null;
			do
			{
				final ListObjectsV2Request request = ListObjectsV2Request
	                .builder()
	                .bucket(file.container())
	                .prefix(prefix)
	                .continuationToken(continuationToken)
	                .build();
				final ListObjectsV2Response response = this.s3.listObjectsV2(request);
				blobs.addAll(response.contents());
				continuationToken = response.isTruncated()
					? response.nextContinuationToken()
					: null
				;
			}
			while(continuationToken != null);
			
			return blobs.stream()
				.filter(obj -> pattern.matcher(obj.key()).matches())
				.sorted(this.blobComparator())
			;
		}

		@Override
		protected Stream<String> childKeys(
			final BlobStorePath directory
		)
		{
			final Set<String> childKeys         = new LinkedHashSet<>();
			final String      prefix            = toChildKeysPrefix(directory);
			String            continuationToken = null;
			do
			{
				final ListObjectsV2Request request = ListObjectsV2Request
	                .builder()
	                .bucket(directory.container())
	                .prefix(prefix)
	                .delimiter(BlobStorePath.SEPARATOR)
	                .continuationToken(continuationToken)
	                .build()
	            ;
				final ListObjectsV2Response response = this.s3.listObjectsV2(request);
				// add "directories"
				childKeys.addAll(
					response
						.commonPrefixes()
						.stream()
						.map(CommonPrefix::prefix)
						.collect(toList())
				);
				// add "files"
				childKeys.addAll(
					response
						.contents()
						.stream()
						.map(S3Object::key)
						.collect(toList())
				);
				continuationToken = response.isTruncated()
					? response.nextContinuationToken()
					: null
				;
			}
			while(continuationToken != null);
			
			return childKeys
				.stream()
				/*
				 * Requested base "directory" will be returned as well if it was created explicitly
				 * but we don't need it in the child keys listing.
				 */
				.filter(path -> !path.equals(prefix))
			;
		}

		@Override
		protected void internalReadBlobData(
			final BlobStorePath file        ,
			final S3Object      blob        ,
			final ByteBuffer    targetBuffer,
			final long          offset      ,
			final long          length
		)
		{
			final GetObjectRequest request = GetObjectRequest.builder()
				.bucket(file.container())
				.key(blob.key())
				.range("bytes=" + offset + "-" + (offset + length - 1))
				.build()
			;
			final ResponseBytes<GetObjectResponse> response = this.s3.getObjectAsBytes(request);
			targetBuffer.put(response.asByteBuffer());
		}

		@Override
		protected boolean internalDirectoryExists(
			final BlobStorePath directory
		)
		{
			try
			{
				final String containerKey = toContainerKey(directory);
				if(containerKey.isBlank() || BlobStorePath.SEPARATOR.equals(containerKey))
				{
					return true;
				}
				
				final ListObjectsV2Request request = ListObjectsV2Request
	                .builder()
	                .bucket(directory.container())
	                .prefix(containerKey)
	                .delimiter(BlobStorePath.SEPARATOR)
	                .maxKeys(1)
	                .build()
	            ;
				final List<S3Object> objects = this.s3.listObjectsV2(request).contents();
				return !objects.isEmpty();
			}
			catch(final NoSuchBucketException | NoSuchKeyException e)
			{
				return false;
			}
		}
		
		@Override
		protected boolean internalFileExists(
			final BlobStorePath file
		)
		{
			try
			{
				return super.internalFileExists(file);
			}
			catch(final NoSuchBucketException e)
			{
				return false;
			}
		}

		@Override
		protected boolean internalCreateDirectory(
			final BlobStorePath directory
		)
		{
			final String containerKey = toContainerKey(directory);
			if(containerKey.isBlank() || BlobStorePath.SEPARATOR.equals(containerKey))
			{
				return true;
			}
			
			final PutObjectRequest request = PutObjectRequest.builder()
				.bucket(directory.container())
				.key(containerKey)
				.build()
			;
			this.s3.putObject(
				request,
				RequestBody.empty()
			);
			
			return true;
		}

		@Override
		protected boolean internalDeleteBlobs(
			final BlobStorePath            file,
			final List<? extends S3Object> blobs
		)
		{
			final int limit = 1000;
			if(blobs.size() <= limit)
			{
				return this.internalDeleteBlobs0(file, blobs);
			}
			
			boolean success = true;
			final List<? extends S3Object> toDelete = new ArrayList<>(blobs);
			while(!toDelete.isEmpty())
			{
				final List<? extends S3Object> subList = toDelete.subList(0, Math.min(limit, toDelete.size()));
				toDelete.removeAll(subList);
				if(!this.internalDeleteBlobs0(file, subList))
				{
					success = false;
				}
			}
			return success;
		}
		
		protected boolean internalDeleteBlobs0(
			final BlobStorePath            file,
			final List<? extends S3Object> blobs
		)
		{
			final List<ObjectIdentifier> objects = blobs.stream()
				.map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
				.collect(toList())
			;
			final DeleteObjectsRequest request = DeleteObjectsRequest.builder()
				.bucket(file.container())
				.delete(Delete.builder().objects(objects).build())
				.build()
			;
			final DeleteObjectsResponse response = this.s3.deleteObjects(request);
			return response.deleted().size() == blobs.size();
		}

		@Override
		protected long internalWriteData(
			final BlobStorePath                  file         ,
			final Iterable<? extends ByteBuffer> sourceBuffers
		)
		{
			final long nextBlobNumber = this.nextBlobNumber(file);
			final long totalSize      = this.totalSize(sourceBuffers);

			final PutObjectRequest request = PutObjectRequest.builder()
				.bucket(file.container())
				.key(toBlobKey(file, nextBlobNumber))
				.build()
			;
			
			try(final BufferedInputStream inputStream = new BufferedInputStream(
					ByteBufferInputStream.New(sourceBuffers)
			))
			{
				final RequestBody body = RequestBody.fromContentProvider(
					() -> inputStream,
					totalSize,
					Mimetype.MIMETYPE_OCTET_STREAM
				);
				
				this.s3.putObject(request, body);
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}

			return totalSize;
		}

	}
	
	
	public static class Directory extends Default
	{

		Directory(final S3Client s3, final boolean useCache)
		{
			super(s3, useCache);
		}
		
		/*
		 * Needs to load all objects in a "directory" and filter afterwards,
		 * because directory buckets only support listing with prefixes ending in '/'.
		 */
		@Override
		protected Stream<S3Object> blobs(final BlobStorePath file)
		{
			final String         prefix            = toChildKeysPrefix(file.parentPath());
			final Pattern        pattern           = Pattern.compile(blobKeyRegex(toBlobKeyPrefix(file)));
			final List<S3Object> blobs             = new ArrayList<>();
			String               continuationToken = null;
			do
			{
				final ListObjectsV2Request request = ListObjectsV2Request
	                .builder()
	                .bucket(file.container())
	                .prefix(prefix)
	                .continuationToken(continuationToken)
	                .build();
				final ListObjectsV2Response response = this.s3.listObjectsV2(request);
				blobs.addAll(response.contents());
				continuationToken = response.isTruncated()
					? response.nextContinuationToken()
					: null
				;
			}
			while(continuationToken != null);
			
			return blobs.stream()
				.filter(obj -> pattern.matcher(obj.key()).matches())
				.sorted(this.blobComparator())
			;
		}
		
	}

}
