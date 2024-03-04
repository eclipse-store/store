
package org.eclipse.store.integrations.cdi.types;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */



import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.store.integrations.cdi.Storage;


@Storage
public class Agenda
{

	private final Set<String> names;

	public Agenda()
	{
		this.names = new ConcurrentSkipListSet<>();
	}

	public void add(final String name)
	{
		this.names.add(name);
	}

	public Set<String> getNames()
	{
		return Collections.unmodifiableSet(this.names);
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || this.getClass() != o.getClass())
		{
			return false;
		}
		final Agenda agenda = (Agenda)o;
		return Objects.equals(this.names, agenda.names);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hashCode(this.names);
	}
	
	@Override
	public String toString()
	{
		return "Agenda{"
			+
			"names="
			+ this.names
			+
			'}';
	}
}
