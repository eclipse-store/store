package org.eclipse.store.afs.aws.dynamodb.types;

/*-
 * #%L
 * EclipseStore Abstract File System AWS DynamoDB
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

import static org.eclipse.serializer.util.X.checkArrayRange;
import static org.eclipse.serializer.util.X.notNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.serializer.concurrency.XThreads;
import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.serializer.io.ByteBufferInputStream;
import org.eclipse.store.afs.blobstore.types.BlobStoreConnector;
import org.eclipse.store.afs.blobstore.types.BlobStorePath;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.paginators.QueryIterable;
import software.amazon.awssdk.services.dynamodb.paginators.ScanIterable;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

/**
 * Connector for the <a href="https://aws.amazon.com/dynamodb/">Amazon DynamoDB database service</a>.
 * <p>
 * First create a connection to the <a href="https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/examples-dynamodb.html">DynamoDB service</a>.
 * <pre>
 * DynamoDbClient client = ...
 * BlobStoreFileSystem fileSystem = BlobStoreFileSystem.New(
 * 	DynamoDbConnector.Caching(client)
 * );
 * </pre>
 *
 * 
 *
 */
public interface DynamoDbConnector extends BlobStoreConnector
{
	/**
	 * Pseudo-constructor method which creates a new {@link DynamoDbConnector}.
	 *
	 * @param client connection to the DynamoDB service
	 * @return a new {@link DynamoDbConnector}
	 */
	public static DynamoDbConnector New(
		final DynamoDbClient client
	)
	{
		return new DynamoDbConnector.Default(
			notNull(client),
			false
		);
	}
	
	/**
	 * Pseudo-constructor method which creates a new {@link DynamoDbConnector} with cache.
	 *
	 * @param client connection to the DynamoDB service
	 * @return a new {@link DynamoDbConnector}
	 */
	public static DynamoDbConnector Caching(
		final DynamoDbClient client
	)
	{
		return new DynamoDbConnector.Default(
			notNull(client),
			true
		);
	}


	public static class Default
	extends    BlobStoreConnector.Abstract<Map<String, AttributeValue>>
	implements DynamoDbConnector
	{
		private final static String FIELD_KEY  = "key" ;
		private final static String FIELD_SEQ  = "seq" ;
		private final static String FIELD_SIZE = "size";
		private final static String FIELD_DATA = "data";

		// https://docs.aws.amazon.com/de_de/amazondynamodb/latest/developerguide/Limits.html
		private final static long MAX_BLOB_SIZE     =   400_000;
		private final static long MAX_REQUEST_SIZE  = 4_000_000;
		private final static long MAX_REQUEST_ITEMS =        25;

		private final DynamoDbClient                client;
		private final Map<String, TableDescription> tables;

		Default(
			final DynamoDbClient client  ,
			final boolean        useCache
		)
		{
			super(
				blob -> blob.get(FIELD_KEY).s(),
				blob -> Long.parseLong(blob.get(FIELD_SIZE).n()),
				useCache
			);
			this.client = client         ;
			this.tables = new HashMap<>();
		}

		private TableDescription table(
			final BlobStorePath path
		)
		{
			return this.tables.computeIfAbsent(
				path.container() ,
				this::createTable
			);
		}

		private TableDescription createTable(
			final String name
		)
		{
			try
			{
				return this.client.describeTable(builder -> builder.tableName(name)).table();
			}
			catch(final ResourceNotFoundException e)
			{
				final CreateTableRequest request = CreateTableRequest.builder()
					.tableName(name)
					.keySchema(
						KeySchemaElement.builder()
							.attributeName(FIELD_KEY)
							.keyType(KeyType.HASH)
							.build(),
						KeySchemaElement.builder()
							.attributeName(FIELD_SEQ)
							.keyType(KeyType.RANGE)
							.build()
					)
					.attributeDefinitions(
						AttributeDefinition.builder()
							.attributeName(FIELD_KEY)
							.attributeType(ScalarAttributeType.S)
							.build(),
						AttributeDefinition.builder()
							.attributeName(FIELD_SEQ)
							.attributeType(ScalarAttributeType.N)
							.build()
					)
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.build()
				;
				TableDescription description = this.client.createTable(request).tableDescription();
				while(description.tableStatus() != TableStatus.ACTIVE)
				{
					XThreads.sleep(1000l);
					
					description = this.client.describeTable(
						DescribeTableRequest.builder().tableName(name).build()
					).table();
				}
				return description;
			}
		}

		private Stream<Map<String, AttributeValue>> blobs(
			final BlobStorePath file    ,
			final boolean       withData
		)
		{
			final Map<String, String> attributeNames = new HashMap<>();
			attributeNames.put("#" + FIELD_KEY, FIELD_KEY);
			attributeNames.put("#" + FIELD_SEQ, FIELD_SEQ);
			attributeNames.put("#" + FIELD_SIZE, FIELD_SIZE);
			
			final Map<String, AttributeValue> attributeValues = new HashMap<>();
			attributeValues.put(
				":" + FIELD_KEY,
				AttributeValue.builder()
					.s(file.fullQualifiedName())
					.build()
			);
			
			final QueryRequest.Builder builder = QueryRequest.builder()
				.tableName(file.container())
				.keyConditionExpression("#" + FIELD_KEY + "=:" + FIELD_KEY)
				.expressionAttributeNames(attributeNames)
				.expressionAttributeValues(attributeValues)
			;
			
			if(!withData)
			{
				builder.projectionExpression("#" + FIELD_KEY + ",#" + FIELD_SEQ + ",#" + FIELD_SIZE);
			}
			
			try
			{
				/*
				 * DynamoDB queries return paginated results capped at 1 MB per page.
				 * A multi-blob file's seq rows (each up to MAX_BLOB_SIZE = 400 000 B)
				 * easily exceed one page, so we MUST consume the full paginator,
				 * otherwise file size / existence / read-back will be silently
				 * truncated to the first page.
				 */
				final QueryIterable pages = this.client.queryPaginator(builder.build());
				final List<Map<String, AttributeValue>> items = new ArrayList<>();
				for(final QueryResponse page : pages)
				{
					if(page.hasItems())
					{
						items.addAll(page.items());
					}
				}
				if(items.isEmpty())
				{
					return Stream.empty();
				}
				return items.stream().sorted(this.blobComparator());
			}
			catch(final ResourceNotFoundException e)
			{
				// no items found
			}

			return Stream.empty();
		}

		private Map<String, AttributeValue> createKey(
			final BlobStorePath               file,
			final Map<String, AttributeValue> blob
		)
		{
			final Map<String, AttributeValue> key = new HashMap<>();
			key.put(
				FIELD_KEY,
				AttributeValue.builder()
					.s(file.fullQualifiedName())
					.build()
			);
			key.put(
				FIELD_SEQ,
				AttributeValue.builder()
					.n(Long.toString(this.blobNumber(blob)))
					.build()
			);
			return key;
		}

		@Override
		protected long blobNumber(final Map<String, AttributeValue> blob)
		{
			return Long.parseLong(blob.get(FIELD_SEQ).n());
		}

		@Override
		protected Stream<Map<String, AttributeValue>> blobs(
			final BlobStorePath file
		)
		{
			return this.blobs(file, false);
		}

		@Override
		protected Stream<String> childKeys(
			final BlobStorePath directory
		)
		{
			final Map<String, String> attributeNames = new HashMap<>();
			attributeNames.put("#" + FIELD_KEY, FIELD_KEY);
			
			final Map<String, AttributeValue> expressionValues = new HashMap<>();
			expressionValues.put(
				":" + FIELD_KEY,
				AttributeValue.builder()
					.s(toChildKeysPrefixWithContainer(directory))
					.build()
			);
			
			final ScanRequest request = ScanRequest.builder()
				.tableName(directory.container())
				.filterExpression("begins_with(#" + FIELD_KEY + ",:" + FIELD_KEY + ")")
				.projectionExpression("#" + FIELD_KEY)
				.expressionAttributeNames(attributeNames)
				.expressionAttributeValues(expressionValues)
				.build()
			;

			try
			{
				/*
				 * DynamoDB scans page at ~1 MB per response. A directory containing
				 * multiple multi-blob files (a normal Eclipse Store channel does)
				 * exceeds that, so we MUST iterate the full paginator. Returning only
				 * the first page would silently hide files from {@link
				 * org.eclipse.store.storage.types.StorageFileManager}'s startup
				 * inventory and cause "Non-deleted non-empty data file not found".
				 */
				final Pattern pattern = Pattern.compile(childKeysRegexWithContainer(directory));
				final ScanIterable pages = this.client.scanPaginator(request);
				return pages.items().stream()
					.map(item -> item.get(FIELD_KEY).s())
					.filter(key -> pattern.matcher(key).matches())
					.distinct()
				;
			}
			catch(final ResourceNotFoundException e)
			{
				// no items found
			}

			return Stream.empty();
		}

		@Override
		protected String fileNameOfKey(
			final String key
		)
		{
			return key.substring(
				key.lastIndexOf(BlobStorePath.SEPARATOR_CHAR) + 1
			);
		}

		@Override
		protected boolean internalFileExists(
			final BlobStorePath file
		)
		{
			final Map<String, String> attributeNames = new HashMap<>();
			attributeNames.put("#" + FIELD_KEY, FIELD_KEY);
			
			final Map<String, AttributeValue> attributeValues = new HashMap<>();
			attributeValues.put(
				":" + FIELD_KEY,
				AttributeValue.builder()
					.s(file.fullQualifiedName())
					.build()
			);
			
			final QueryRequest request = QueryRequest.builder()
				.tableName(file.container())
				.select(Select.COUNT)
				.keyConditionExpression("#" + FIELD_KEY + "=:" + FIELD_KEY)
				.expressionAttributeNames(attributeNames)
				.expressionAttributeValues(attributeValues)
				.build()
			;
			
			try
			{
				final QueryResponse response = this.client.query(request);
				return response.count() > 0;
			}
			catch(final ResourceNotFoundException e)
			{
				// no items found
				return false;
			}
		}

		@Override
		protected void internalReadBlobData(
			final BlobStorePath               file        ,
			final Map<String, AttributeValue> blob        ,
			final ByteBuffer                  targetBuffer,
			final long                        offset      ,
			final long                        length
		)
		{
			final GetItemRequest request = GetItemRequest.builder()
				.tableName(file.container())
				.key(this.createKey(file, blob))
				.attributesToGet(FIELD_DATA)
				.build()
			;
			
			final GetItemResponse response = this.client.getItem(request);
			final Map<String, AttributeValue> item = response.item();
			targetBuffer.put(
				item.get(FIELD_DATA).b().asByteArrayUnsafe(),
				checkArrayRange(offset),
				checkArrayRange(length)
			);
		}

		@Override
		protected boolean internalDeleteBlobs(
			final BlobStorePath                               file ,
			final List<? extends Map<String, AttributeValue>> blobs
		)
		{
			final BatchDelete batchDelete = new BatchDelete(this.client);
			for(final Map<String, AttributeValue> item : blobs)
			{
				final Delete delete = Delete.builder()
					.tableName(file.container())
					.key(this.createKey(file, item))
					.build()
				;
				batchDelete.add(delete);
			}
			
			batchDelete.finish();

			return batchDelete.hasWritten();
		}

		@Override
		protected long internalWriteData(
			final BlobStorePath                  file         ,
			final Iterable<? extends ByteBuffer> sourceBuffers
		)
		{
			final BatchPut              batchPut           = new BatchPut(this.client);
			final String                tableName          = this.table(file).tableName();
			      long                  nextBlobNumber     = this.nextBlobNumber(file);
			final long                  totalSize          = this.totalSize(sourceBuffers);
			final ByteBufferInputStream buffersInputStream = ByteBufferInputStream.New(sourceBuffers);
			      long                  available          = totalSize;
			while(available > 0)
			{
				final long currentBatchSize = Math.min(
					available,
					MAX_BLOB_SIZE
				);

				try
				{
					/*
					 * Read directly from the (in-memory) ByteBufferInputStream into the
					 * batch array. Earlier versions wrapped this in BufferedInputStream,
					 * but BufferedInputStream's 8 KiB internal pre-fetch buffer caused
					 * silent data loss at blob boundaries: bytes pulled from the source
					 * into the BIS buffer that were never returned to the caller were
					 * discarded when the BIS was closed (try-with-resources, recreated
					 * per batch). The next batch then started reading from a source
					 * position that was up to 8 KiB past where it should have been.
					 *
					 * The offset into batch[] must also advance with each read; an
					 * InputStream.read may legitimately return fewer bytes than
					 * requested, and a fixed offset of 0 would overwrite earlier bytes.
					 */
					final byte[] batch = new byte[checkArrayRange(currentBatchSize)];
					      int    off   = 0;
					      int    read;
					while(off < batch.length &&
						(read = buffersInputStream.read(
							batch,
							off,
							batch.length - off)
						) != -1
					)
					{
						off += read;
					}
					if(off < batch.length)
					{
						throw new IORuntimeException(new IOException(
							"Source stream exhausted: expected "
							+ batch.length + " bytes, got " + off
						));
					}

					final Map<String, AttributeValue> item = new HashMap<>();
					item.put(
						FIELD_KEY,
						AttributeValue.builder()
							.s(file.fullQualifiedName())
							.build()
					);
					item.put(
						FIELD_SEQ,
						AttributeValue.builder()
							.n(Long.toString(nextBlobNumber++))
							.build()
					);
					item.put(
						FIELD_SIZE,
						AttributeValue.builder()
							.n(Long.toString(currentBatchSize))
							.build()
					);
					item.put(
						FIELD_DATA,
						AttributeValue.builder()
							.b(SdkBytes.fromByteArrayUnsafe(batch))
							.build()
					);
					
					final Put put = Put.builder()
						.tableName(tableName)
						.item(item)
						.build()
					;
					batchPut.add(put);
				}
				catch(final IOException e)
				{
					throw new IORuntimeException(e);
				}

				available -= currentBatchSize;
			}

			batchPut.finish();

			return totalSize;
		}

		private static abstract class BatchWrite
		{
			final DynamoDbClient          client         ;
			final List<TransactWriteItem> items          ;
			      boolean                 written = false;

			BatchWrite(
				final DynamoDbClient client
			)
			{
				super();
				this.client    = client           ;
				this.items     = new ArrayList<>();
			}
			
			int addItem(final TransactWriteItem item)
			{
				this.items.add(item);
				return this.items.size();
			}

			void finish()
			{
				if(!this.items.isEmpty())
				{
					this.write();
				}
			}

			void write()
			{
				final TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
	        		.transactItems(this.items)
					.build()
				;
	        	this.client.transactWriteItems(request);
				this.items.clear();
	        	this.written = true;
			}
			
			boolean hasWritten()
			{
				return this.written;
			}
			
		}

		private static class BatchDelete extends BatchWrite
		{
			BatchDelete(
				final DynamoDbClient client
			)
			{
				super(client);
			}


			void add(final Delete delete)
			{
				final int itemCount = this.addItem(
					TransactWriteItem.builder()
						.delete(delete)
						.build()
				);
				if(itemCount >= MAX_REQUEST_ITEMS)
				{
					this.write();
				}
			}
			
		}

		private static class BatchPut extends BatchWrite
		{
			BatchPut(
				final DynamoDbClient client
			)
			{
				super(client);
			}


			void add(final Put put)
			{
				final int itemCount = this.addItem(
					TransactWriteItem.builder()
						.put(put)
						.build()
				);
				if(itemCount >= MAX_REQUEST_ITEMS
				|| itemCount * MAX_BLOB_SIZE >= MAX_REQUEST_SIZE
				)
				{
					this.write();
				}
			}
			
		}

	}

}
