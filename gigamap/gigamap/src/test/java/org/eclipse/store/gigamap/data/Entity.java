
package org.eclipse.store.gigamap.data;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.types.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;


public class Entity
{
	public final static IndexerCharacter<Entity> firstCharIndex = new IndexerCharacter.Abstract<>()
	{
		@Override
		public Character getCharacter(final Entity entity)
		{
			return entity.text.charAt(0);
		}
	};
	
	public final static IndexerString<Entity> wordIndex = new IndexerString.Abstract<>()
	{
		public String name()
		{
			return "word";
		}
		
		@Override
		protected String getString(final Entity entity)
		{
			return entity.word;
		}
	};
	
	public final static BinaryIndexerString<Entity> uniqueWordIndex = new BinaryIndexerString.Abstract<>()
	{
		public String name()
		{
			return "word";
		}
		
		@Override
		protected String getString(final Entity entity)
		{
			return entity.word;
		}
	};
	
	public final static IndexerInteger<Entity> intValueIndex = new IndexerInteger.Abstract<>()
	{
		@Override
		protected Integer getInteger(final Entity entity)
		{
			return entity.intValue;
		}
	};
	
	public final static IndexerDouble<Entity> doubleValueIndex = new IndexerDouble.Abstract<>()
	{
		@Override
		protected Double getDouble(final Entity entity)
		{
			return entity.doubleValue;
		}
	};
	
	public final static IndexerLocalDateTime<Entity> localDateTimeIndex = new IndexerLocalDateTime.Abstract<>()
	{
		@Override
		protected LocalDateTime getLocalDateTime(final Entity entity)
		{
			return entity.localDateTime;
		}
	};
	
	public final static BinaryIndexerUUID<Entity> uuidIndex = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(final Entity entity)
		{
			return entity.uuid;
		}
	};
	
	public final static IndexerFloat<Entity> subEntityFloatValueIndex = new IndexerFloat.Abstract<>()
	{
		public String name()
		{
			return "subEntity.floatValue";
		}
				
		@Override
		protected Float getFloat(final Entity entity)
		{
			return entity.singleSubEntity.getFloatValue();
		}
	};
	
	public final static IndexerCharacter<Entity> subEntityCharValueIndex = new IndexerCharacter.Abstract<>()
	{
		public String name()
		{
			return "subEntity.charValue";
		}
		
		@Override
		public Character getCharacter(final Entity entity)
		{
			return entity.singleSubEntity.getCharValue();
		}
	};
	
	public final static IndexerLong<Entity> subEntityInstantMilliIndex = new IndexerLong.Abstract<>()
	{
		public String name()
		{
			return "subEntity.floatInstant";
		}
		
		@Override
		protected Long getLong(final Entity entity)
		{
			return entity.singleSubEntity.getInstant().toEpochMilli();
		}
	};
	
	public final static NumberRangeIndexer numberRangeIndex = new NumberRangeIndexer();
	
	public static class NumberRangeIndexer extends IndexerByte.Abstract<Entity>
	{
		private final static int MID_START  =  100;
		private final static int HIGH_START = 1000;
		
		private final static byte LOW  = 0;
		private final static byte MID  = 1;
		private final static byte HIGH = 2;
		
		@Override
		protected Byte getByte(final Entity entity)
		{
			if(entity.intValue >= HIGH_START)
			{
				return HIGH;
			}
			if(entity.intValue >= MID_START)
			{
				return MID;
			}
			return LOW;
		}
		
		public Condition<Entity> isLow()
		{
			return this.is(LOW);
		}
		
		public Condition<Entity> isMid()
		{
			return this.is(MID);
		}
		
		public Condition<Entity> isHigh()
		{
			return this.is(HIGH);
		}
		
	}
		
	
	public static Entity Random()
	{
		return Random(new Faker());
	}
	
	public static Entity Random(final Faker faker)
	{
		return new Entity(
			faker.lorem().paragraph(),
			faker.color().name(),
			faker.random().nextInt(0, 2500),
			faker.random().nextDouble(),
			LocalDateTime.now(),
			UUID.randomUUID(),
			SubEntity.Random(faker),
			IntStream.range(0, faker.random().nextInt(100))
				.mapToObj(i -> SubEntity.Random(faker))
				.collect(toList())
		);
	}
	
	public static Entity RandomFlat(final Faker faker)
	{
		return new Entity(
			faker.lorem().paragraph(),
			faker.color().name(),
			faker.random().nextInt(0, 2500),
			faker.random().nextDouble(),
			LocalDateTime.now(),
			UUID.randomUUID(),
			SubEntity.DUMMY,
			Collections.emptyList()
		);
	}
	
	public static List<Entity> RandomList(final int maxAmount)
	{
		final Faker faker = new Faker();
		return IntStream.range(0, faker.random().nextInt(1, maxAmount))
			.mapToObj(i -> Random(faker))
			.collect(toList())
		;
	}
	
	public static List<Entity> FixedList(final int amount)
	{
		final Faker faker = new Faker();
		return IntStream.range(0, amount)
			.mapToObj(i -> Random(faker))
			.collect(toList())
		;
	}
	
	
		
	
	private String          text;
	private String          word;
	private int             intValue;
	private double          doubleValue;
	private LocalDateTime   localDateTime;
	private UUID            uuid;
	private SubEntity       singleSubEntity;
	private List<SubEntity> multiSubEntities;
	
	public Entity(
		final String text,
		final String word,
		final int intValue,
		final double doubleValue,
		final LocalDateTime localDateTime,
		final UUID uuid,
		final SubEntity singleSubEntity,
		final List<SubEntity> multiSubEntities)
	{
		super();
		this.text             = text;
		this.word             = word;
		this.intValue         = intValue;
		this.doubleValue      = doubleValue;
		this.localDateTime    = localDateTime;
		this.uuid             = uuid;
		this.singleSubEntity  = singleSubEntity;
		this.multiSubEntities = multiSubEntities;
	}

	public String getText()
	{
		return this.text;
	}

	public Entity setText(final String text)
	{
		this.text = text;
		return this;
	}

	public String getWord()
	{
		return this.word;
	}
	
	public Entity setWord(final String word)
	{
		this.word = word;
		return this;
	}
	
	public int getIntValue()
	{
		return this.intValue;
	}

	public Entity setIntValue(final int intValue)
	{
		this.intValue = intValue;
		return this;
	}

	public double getDoubleValue()
	{
		return this.doubleValue;
	}

	public Entity setDoubleValue(final double doubleValue)
	{
		this.doubleValue = doubleValue;
		return this;
	}

	public LocalDateTime getLocalDateTime()
	{
		return this.localDateTime;
	}

	public Entity setLocalDateTime(final LocalDateTime localDateTime)
	{
		this.localDateTime = localDateTime;
		return this;
	}

	public UUID getUuid()
	{
		return this.uuid;
	}

	public Entity setUuid(final UUID uuid)
	{
		this.uuid = uuid;
		return this;
	}

	public SubEntity getSingleSubEntity()
	{
		return this.singleSubEntity;
	}

	public Entity setSingleSubEntity(final SubEntity singleSubEntity)
	{
		this.singleSubEntity = singleSubEntity;
		return this;
	}

	public List<SubEntity> getMultiSubEntities()
	{
		return this.multiSubEntities;
	}

	public Entity setMultiSubEntities(final List<SubEntity> multiSubEntities)
	{
		this.multiSubEntities = multiSubEntities;
		return this;
	}

}
