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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.collections.types.XGettingTable;

import static org.eclipse.serializer.util.X.notNull;
import static org.eclipse.serializer.math.XMath.notNegative;

import java.text.DecimalFormat;
import java.util.Date;

/**
 * User-facing snapshot of a storage's raw file statistics, returned by
 * {@code StorageManager.createStorageStatistics()}.
 * <p>
 * The statistics aggregate three metrics — file count, live data length, total data length —
 * across the entire storage and break them down per channel ({@link ChannelStatistics}) and per
 * file ({@link FileStatistics}). The ratio of live data length to total data length expresses the
 * storage's space efficiency and is the primary indicator for how much benefit the next file
 * cleanup pass can deliver.
 *
 * @see StorageRawFileStatisticsItem
 */
public interface StorageRawFileStatistics extends StorageRawFileStatisticsItem
{
	/**
	 * Returns the wall-clock time at which this statistics snapshot was taken.
	 *
	 * @return the snapshot creation time.
	 */
	public Date creationTime();

	/**
	 * Returns the number of channels covered by this statistics snapshot.
	 *
	 * @return the channel count.
	 */
	public int channelCount();

	/**
	 * Returns the per-channel statistics keyed by channel index.
	 *
	 * @return a table of {@link ChannelStatistics} keyed by channel index.
	 */
	public XGettingTable<Integer, ? extends ChannelStatistics> channelStatistics();



	/**
	 * Pseudo-constructor method to create a new {@link StorageRawFileStatistics} from the passed
	 * aggregate values and per-channel breakdown.
	 *
	 * @param creationTime      the snapshot creation time; must be non-{@code null}.
	 * @param fileCount         the total file count across all channels; must be non-negative.
	 * @param liveDataLength    the total live data length across all channels; must be non-negative.
	 * @param totalDataLength   the total file length across all channels; must be non-negative.
	 * @param channelStatistics the per-channel statistics keyed by channel index; must be non-{@code null}.
	 *
	 * @return a new {@link StorageRawFileStatistics}.
	 */
	public static StorageRawFileStatistics New(
		final Date                                                creationTime     ,
		final long                                                fileCount        ,
		final long                                                liveDataLength   ,
		final long                                                totalDataLength  ,
		final XGettingTable<Integer, ? extends ChannelStatistics> channelStatistics
	)
	{
		return new StorageRawFileStatistics.Default(
			    notNull(creationTime)     ,
			notNegative(fileCount)        ,
			notNegative(liveDataLength)   ,
			notNegative(totalDataLength)  ,
			    notNull(channelStatistics)
		);
	}

	/**
	 * Default immutable {@link StorageRawFileStatistics} implementation. Inherits the aggregate
	 * counters from {@link StorageRawFileStatisticsItem.Abstract} and provides a human-readable
	 * {@link #toString() multi-line summary} suitable for diagnostic output.
	 */
	public final class Default
	extends StorageRawFileStatisticsItem.Abstract
	implements StorageRawFileStatistics
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final Date creationTime;

		final XGettingTable<Integer, ? extends ChannelStatistics> channelStatistics;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final Date creationTime   ,
			final long fileCount      ,
			final long liveDataLength ,
			final long totalDataLength,
			final XGettingTable<Integer, ? extends ChannelStatistics> channelStatistics
		)
		{
			super(fileCount, liveDataLength, totalDataLength);
			this.creationTime      = notNull(creationTime)     ;
			this.channelStatistics = notNull(channelStatistics);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final Date creationTime()
		{
			return this.creationTime;
		}

		@Override
		public final int channelCount()
		{
			return (int)this.channelStatistics.size();
		}

		@Override
		public final XGettingTable<Integer, ? extends ChannelStatistics> channelStatistics()
		{
			return this.channelStatistics;
		}

		private static double ratio(final long value1, final long value2)
		{
			return value2 == 0 ? 0 : (double)value1 / value2;
		}

		public final VarString assembleString(final VarString vs)
		{
			final DecimalFormat ratioFormat = new DecimalFormat("0.00%");

			vs
			.add("Storage Statistics " + this.creationTime()).lf()
			.tab().add("global file count:\t"        + this.fileCount      ).lf()
			.tab().add("global live data length:\t"  + this.liveDataLength ).lf()
			.tab().add("global total data length:\t" + this.totalDataLength).lf()
			.tab().add("global space efficiency:\t"  + ratioFormat.format(
				ratio(this.liveDataLength, this.totalDataLength))
			).lf()
			.tab().add("channel count:\t" + this.channelCount()).lf()
			;
			for(final ChannelStatistics cs : this.channelStatistics.values())
			{
				vs
				.lf()
				.add("Channel " + cs.channelIndex()).lf()
				.tab().add("file count:\t"        + cs.fileCount()      ).lf()
				.tab().add("live data length:\t"  + cs.liveDataLength() ).lf()
				.tab().add("total data length:\t" + cs.totalDataLength()).lf()
				.tab().add("space efficiency:\t"  + ratioFormat.format(
					ratio(cs.liveDataLength(), cs.totalDataLength()))
				).lf()
				;

				for(final FileStatistics fs : cs.files())
				{
					vs
					.tab().add(fs.file())
					.add(" (").add(fs.liveDataLength()).add(" / ").add(fs.totalDataLength())
					.add(", ").add(ratioFormat.format(ratio(fs.liveDataLength(), fs.totalDataLength())))
					.add(")").lf()
					;
				}
			}

			return vs;
		}

		@Override
		public final String toString()
		{
			return this.assembleString(VarString.New()).toString();
		}

	}



	/**
	 * Per-channel slice of a {@link StorageRawFileStatistics}, listing the channel's index and its
	 * file-level breakdown along with the same aggregate counters as the parent statistics.
	 */
	public interface ChannelStatistics extends StorageRawFileStatisticsItem
	{
		/**
		 * Returns the index of the channel this slice describes.
		 *
		 * @return the channel index.
		 */
		public int channelIndex();

		/**
		 * Returns the per-file statistics for this channel.
		 *
		 * @return a sequence of {@link FileStatistics} for this channel's data files.
		 */
		public XGettingSequence<? extends FileStatistics> files();



		/**
		 * Pseudo-constructor method to create a new {@link ChannelStatistics}.
		 *
		 * @param channelIndex    the channel index; must be non-negative.
		 * @param fileCount       the channel's file count; must be non-negative.
		 * @param liveDataLength  the channel's live data length; must be non-negative.
		 * @param totalDataLength the channel's total data length; must be non-negative.
		 * @param files           the per-file statistics for this channel; must be non-{@code null}.
		 *
		 * @return a new {@link ChannelStatistics}.
		 */
		public static ChannelStatistics New(
			final int                                        channelIndex   ,
			final long                                       fileCount      ,
			final long                                       liveDataLength ,
			final long                                       totalDataLength,
			final XGettingSequence<? extends FileStatistics> files
		)
		{
			return new ChannelStatistics.Default(
				notNegative(channelIndex)   ,
				notNegative(fileCount)      ,
				notNegative(liveDataLength) ,
				notNegative(totalDataLength),
					notNull(files)
			);
		}

		/**
		 * Default immutable {@link ChannelStatistics} implementation: a value holder combining the
		 * channel index and its file-level breakdown with the aggregate counters from
		 * {@link StorageRawFileStatisticsItem.Abstract}.
		 */
		public final class Default
		extends StorageRawFileStatisticsItem.Abstract
		implements ChannelStatistics
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			final int                                        channelIndex;
			final XGettingSequence<? extends FileStatistics> files       ;



			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			Default(
				final int                                        channelIndex   ,
				final long                                       fileCount      ,
				final long                                       liveDataLength ,
				final long                                       totalDataLength,
				final XGettingSequence<? extends FileStatistics> files
			)
			{
				super(fileCount, liveDataLength, totalDataLength);
				this.channelIndex = channelIndex;
				this.files        = files       ;
			}



			///////////////////////////////////////////////////////////////////////////
			// override methods //
			/////////////////////

			@Override
			public final int channelIndex()
			{
				return this.channelIndex;
			}

			@Override
			public final XGettingSequence<? extends FileStatistics> files()
			{
				return this.files;
			}

		}

	}



	/**
	 * Per-file slice of a {@link ChannelStatistics}: the file's per-channel number, its identifying
	 * path string, and its live/total data lengths.
	 */
	public interface FileStatistics extends StorageRawFileStatisticsItem
	{
		/**
		 * Returns the per-channel file number.
		 *
		 * @return the per-channel file number.
		 */
		public long fileNumber();

		/**
		 * Returns the file's identifier (typically the path).
		 *
		 * @return the file identifier.
		 */
		public String file();



		/**
		 * Pseudo-constructor method to create a new {@link FileStatistics}.
		 *
		 * @param fileNumber      the per-channel file number.
		 * @param file            the file identifier.
		 * @param liveDataLength  the file's live data length in bytes.
		 * @param totalDataLength the file's total length in bytes.
		 *
		 * @return a new {@link FileStatistics}.
		 */
		public static FileStatistics New(
			final long   fileNumber     ,
			final String file           ,
			final long   liveDataLength ,
			final long   totalDataLength
		)
		{
			return new FileStatistics.Default(
				fileNumber     ,
				file           ,
				liveDataLength ,
				totalDataLength
			);
		}

		/**
		 * Default immutable {@link FileStatistics} implementation: a value holder for a single
		 * data file's metrics.
		 */
		public final class Default
		extends StorageRawFileStatisticsItem.Abstract
		implements FileStatistics
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			final long   fileNumber;
			final String file      ;



			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			Default(
				final long   fileNumber     ,
				final String file           ,
				final long   liveDataLength ,
				final long   totalDataLength
			)
			{
				super(1, liveDataLength, totalDataLength);
				this.fileNumber = fileNumber;
				this.file       = file     ;
			}


			@Override
			public final long fileNumber()
			{
				return this.fileNumber;
			}

			@Override
			public final String file()
			{
				return this.file;
			}

		}

	}

}
