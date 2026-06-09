package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.annotations.Identity;
import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.IndexKind;
import org.eclipse.store.gigamap.annotations.Unique;

import java.util.List;

/**
 * Test entity exercising a representative spread of index kinds. All fields are private and reached
 * through their getters:
 * <ul>
 *   <li>annotations on private fields, read through the matching getter,</li>
 *   <li>an annotation on the getter rather than the field ({@code city}),</li>
 *   <li>{@code @Unique} natural number (binary), {@code BIT_SLICED} numeric, enum and multi-value.</li>
 * </ul>
 */
public class Person
{
	@Index
	private String firstName;

	@Index
	private int age;

	@Index
	@Unique
	private long ssn;

	@Index(kind = IndexKind.BIT_SLICED)
	private int score;

	@Index
	private Color color;

	@Index
	private List<String> tags;

	@Index
	@Identity
	private String email;

	@Index
	private String nickname;

	private String city;

	public Person()
	{
		super();
	}

	public Person(
		final String       firstName,
		final int          age      ,
		final long         ssn      ,
		final int          score    ,
		final Color        color    ,
		final List<String> tags     ,
		final String       email    ,
		final String       nickname ,
		final String       city
	)
	{
		this.firstName = firstName;
		this.age       = age;
		this.ssn       = ssn;
		this.score     = score;
		this.color     = color;
		this.tags      = tags;
		this.email     = email;
		this.nickname  = nickname;
		this.city      = city;
	}

	public String getFirstName()
	{
		return this.firstName;
	}

	public int getAge()
	{
		return this.age;
	}

	public long getSsn()
	{
		return this.ssn;
	}

	public int getScore()
	{
		return this.score;
	}

	public Color getColor()
	{
		return this.color;
	}

	public List<String> getTags()
	{
		return this.tags;
	}

	public String getEmail()
	{
		return this.email;
	}

	public String getNickname()
	{
		return this.nickname;
	}

	@Index
	public String getCity()
	{
		return this.city;
	}
}
