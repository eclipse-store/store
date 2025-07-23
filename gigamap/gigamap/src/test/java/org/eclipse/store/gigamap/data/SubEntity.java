
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

import java.time.Instant;


public class SubEntity
{
	public static SubEntity Random(final Faker faker)
	{
		return new SubEntity(
			(float)faker.random().nextDouble(),
			(char)faker.random().nextInt(Character.MAX_CODE_POINT),
			Instant.now()
		);
	}
	

	public static SubEntity DUMMY = new SubEntity(0, ' ', Instant.now());
	
	
	private float   floatValue;
	private char    charValue;
	private Instant instant;
	
	public SubEntity(final float floatValue, final char charValue, final Instant instant)
	{
		super();
		this.floatValue = floatValue;
		this.charValue  = charValue;
		this.instant    = instant;
	}
	
	public float getFloatValue()
	{
		return this.floatValue;
	}
	
	public void setFloatValue(final float floatValue)
	{
		this.floatValue = floatValue;
	}
	
	public char getCharValue()
	{
		return this.charValue;
	}
	
	public void setCharValue(final char charValue)
	{
		this.charValue = charValue;
	}
	
	public Instant getInstant()
	{
		return this.instant;
	}
	
	public void setInstant(final Instant instant)
	{
		this.instant = instant;
	}
	
}
