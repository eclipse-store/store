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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.XUtilsCollection;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.exceptions.StorageExceptionExportFailed;


public interface StorageRequestTaskExportEntitiesByType extends StorageRequestTask
{
	public StorageEntityTypeExportStatistics result();



	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<StorageEntityTypeExportStatistics.ChannelStatistic>
	implements StorageRequestTaskExportEntitiesByType
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final Predicate<? super StorageEntityTypeHandler>                                  isExportType  ;
		private final Function<? super StorageEntityTypeHandler, Predicate<? super StorageEntity>> predicateEntityProvider;
		private final StorageEntityTypeExportFileProvider                                          fileProvider  ;
		private final StorageEntityTypeExportStatistics.ChannelStatistic[]                                                           channelResults;
		private       BulkList<ExportItem>                                                         exportTypes   ;
		private       StorageEntityTypeExportStatistics                                            result        ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                                                                         timestamp              ,
			final int                                                                          channelCount           ,
			final StorageEntityTypeExportFileProvider                                          fileProvider           ,
			final Predicate<? super StorageEntityTypeHandler>                                  isExportType           ,
			final Function<? super StorageEntityTypeHandler, Predicate<? super StorageEntity>> predicateEntityProvider,
			final StorageOperationController                                                   controller
		)
		{
			super(timestamp, channelCount, controller);
			this.fileProvider            = notNull(fileProvider);
			this.isExportType            = isExportType != null ? isExportType : e -> !e.isPrimitiveType();
			this.predicateEntityProvider = predicateEntityProvider != null ? predicateEntityProvider : t -> null;
			this.channelResults          = new StorageEntityTypeExportStatistics.ChannelStatistic[channelCount];
		}
		
		Default(
			final long                                        timestamp   ,
			final int                                         channelCount,
			final StorageEntityTypeExportFileProvider         fileProvider,
			final Predicate<? super StorageEntityTypeHandler> isExportType,
			final StorageOperationController                  controller
		)
		{
			this(timestamp, channelCount, fileProvider, isExportType, null, controller);
		}

		Default(
			final long                                timestamp   ,
			final int                                 channelCount,
			final StorageEntityTypeExportFileProvider fileProvider,
			final StorageOperationController          controller
		)
		{
			this(timestamp, channelCount, fileProvider, null, null, controller);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		final void acceptExportType(final StorageEntityTypeHandler type)
		{
			if(!this.isExportType.test(type))
			{
				return;
			}

			this.exportTypes.add(
				new ExportItem(
					this.channelCount(),
					type,
					this.fileProvider.provideExportFile(type),
					this.predicateEntityProvider.apply(type)
				)
			);
		}

		private synchronized BulkList<ExportItem> getExportTypes(final StorageChannel channel)
		{
			if(this.exportTypes == null)
			{
				this.exportTypes = new BulkList<>();
				channel.typeDictionary().iterateTypeHandlers(this::acceptExportType);
			}
			return this.exportTypes;
		}

		@Override
		protected final StorageEntityTypeExportStatistics.ChannelStatistic internalProcessBy(final StorageChannel channel)
		{
			final EqHashTable<Long, StorageEntityTypeExportStatistics.TypeStatistic.Default> typeMap     = EqHashTable.New();
			final BulkList<ExportItem>                     exportItems = this.getExportTypes(channel);

			for(final ExportItem exportItem : exportItems)
			{
				try
				{
					synchronized(exportItem)
					{
						while(!exportItem.isCurrentChannel(channel))
						{
							exportItem.wait();
						}
					}

					final long tStart = System.nanoTime();
					final KeyValue<Long, Long> result = exportItem.predicateEntity == null
						? channel.exportTypeEntities(exportItem.type, exportItem.file)
						: channel.exportTypeEntities(exportItem.type, exportItem.file, exportItem.predicateEntity)
					;
					if(exportItem.isLastChannel(channel))
					{
						// close channel when it's guaranteed to not be needed any more. Do NOT wait until clean up.
						exportItem.cleanUp();
					}
					exportItem.incrementProgress();

					if(result.value() == 0)
					{
						// don't create a static entry for nothing (also don't list files that don't exist)
						continue;
					}

					final StorageEntityTypeExportStatistics.TypeStatistic.Default ts = new StorageEntityTypeExportStatistics.TypeStatistic.Default(
						exportItem.type.typeId(),
						exportItem.type.typeName(),
						exportItem.file
					);
					ts.update(result.value(), result.key(), tStart, System.nanoTime());

					typeMap.add(exportItem.type.typeId(), ts);
				}
				catch(final Exception e)
				{
					/* Interruption (abort export) is actually not an error, but it is a problem to
					 * abort only one thread and let the others continue, so an exception has to be
					 * thrown nevertheless.
					 */
					throw new StorageExceptionExportFailed("Problem while exporting " + exportItem.type.typeName(), e);
				}
			}

			/* note that results get closed in any case via cleanUp() in a thread-synchronized way,
			 * so not thread-local finally block necessary/reasonable here.
			 */

			return new StorageEntityTypeExportStatistics.ChannelStatistic.Default(channel.channelIndex(), typeMap);
		}

		@Override
		protected synchronized void succeed(final StorageChannel channel, final StorageEntityTypeExportStatistics.ChannelStatistic result)
		{
			this.channelResults[channel.channelIndex()] = result;
		}

		@Override
		protected final synchronized void cleanUp(final StorageChannel channel)
		{
			if(this.exportTypes == null)
			{
				return;
			}

			// ensure all channels are closed at the end (e.g. in case of failure before)
			for(final ExportItem item : this.exportTypes)
			{
				item.cleanUp();
			}

			this.exportTypes = null;
		}

		@Override
		public synchronized StorageEntityTypeExportStatistics result()
		{
			if(this.result == null)
			{
				this.result = new StorageEntityTypeExportStatistics.Default(
					XUtilsCollection.toTable(this.channelResults)
				);
			}
			return this.result;
		}

	}

	static final class ExportItem
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final int                              lastChannelIndex;
		final AWritableFile file            ;
		final StorageEntityTypeHandler         type            ;
		final Predicate<? super StorageEntity> predicateEntity ;

		private final AtomicInteger currentChannel = new AtomicInteger();



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		ExportItem(
			final int                              channelCount   ,
			final StorageEntityTypeHandler         type           ,
			final AWritableFile                    file           ,
			final Predicate<? super StorageEntity> predicateEntity
		)
		{
			super();
			this.lastChannelIndex = channelCount - 1;
			this.file             = file            ;
			this.type             = type            ;
			this.predicateEntity  = predicateEntity ;
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		final synchronized void incrementProgress()
		{
			this.currentChannel.incrementAndGet();
			this.notifyAll();
		}

		final boolean isCurrentChannel(final StorageChannel channel)
		{
			return this.currentChannel.get() == channel.channelIndex();
		}

		final boolean isLastChannel(final StorageChannel channel)
		{
			return this.lastChannelIndex == channel.channelIndex();
		}

		final synchronized void cleanUp()
		{
			if(!this.file.isOpen())
			{
				return;
			}
			
			if(this.file.isEmpty())
			{
				this.file.delete();
			}
			else
			{
				this.file.close();
			}
			
			this.file.release();
		}

	}

}
