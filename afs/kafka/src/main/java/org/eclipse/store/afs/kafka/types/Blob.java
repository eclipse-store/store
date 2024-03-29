package org.eclipse.store.afs.kafka.types;

/*-
 * #%L
 * EclipseStore Abstract File System Kafka
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

import static org.eclipse.serializer.chars.XChars.notEmpty;

import java.util.Objects;

public interface Blob
{
	public String topic();

	public int partition();

	public long offset();

	public long start();

	public long end();

	public long size();


	public static Blob New(
		final String topic    ,
		final int    partition,
		final long   offset   ,
		final long   start    ,
		final long   end
	)
	{
		if(start < 0)
		{
			throw new IllegalArgumentException("start < 0");
		}
		if(start < 0 || end <= start)
		{
			throw new IllegalArgumentException("end <= start");
		}

		return new Blob.Default(
			notEmpty(topic),
			partition,
			offset,
			start,
			end
		);
	}


	public static class Default implements Blob
	{
		private final String topic    ;
		private final int    partition;
		private final long   offset   ;
		private final long   start    ;
		private final long   end      ;

		Default(
			final String topic    ,
			final int    partition,
			final long   offset   ,
			final long   start    ,
			final long   end
		)
		{
			super();
			this.topic     = topic    ;
			this.partition = partition;
			this.offset    = offset   ;
			this.start     = start    ;
			this.end       = end      ;
		}

		@Override
		public String topic()
		{
			return this.topic;
		}

		@Override
		public int partition()
		{
			return this.partition;
		}

		@Override
		public long offset()
		{
			return this.offset;
		}

		@Override
		public long start()
		{
			return this.start;
		}

		@Override
		public long end()
		{
			return this.end;
		}

		@Override
		public long size()
		{
			return this.end - this.start + 1;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(
				this.topic,
				this.partition,
				this.offset,
				this.start,
				this.end
			);
		}

		@Override
		public boolean equals(final Object obj)
		{
			if(this == obj)
			{
				return true;
			}
			if(!(obj instanceof Default))
			{
				return false;
			}
			final Default other = (Default)obj;
			return Objects.equals(this.topic, other.topic)
				&& this.partition == other.partition
				&& this.offset    == other.offset
				&& this.start     == other.start
				&& this.end       == other.end
			;
		}



	}

}
