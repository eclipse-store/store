
package org.eclipse.store.examples.lazyLoading;

/*-
 * #%L
 * EclipseStore Example Lazy Loading
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.serializer.reference.Lazy;


public class BusinessYear
{
	private Lazy<List<Turnover>> turnovers;

	public BusinessYear()
	{
		super();
	}

	private List<Turnover> getTurnovers()
	{
		return Lazy.get(this.turnovers);
	}

	public void addTurnover(final Turnover turnover)
	{
		List<Turnover> turnovers = this.getTurnovers();
		if(turnovers == null)
		{
			this.turnovers = Lazy.Reference(turnovers = new ArrayList<>());
		}
		turnovers.add(turnover);
	}

	public Stream<Turnover> turnovers()
	{
		final List<Turnover> turnovers = this.getTurnovers();
		return turnovers != null ? turnovers.stream() : Stream.empty();
	}
}
