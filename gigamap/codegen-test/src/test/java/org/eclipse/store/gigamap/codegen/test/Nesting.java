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

import org.eclipse.store.gigamap.annotations.Index;

/**
 * Holder for a nested entity. Its metamodel must be named {@code Nesting_Customer_} (qualified with
 * the enclosing type) so it cannot shadow a same-named top-level entity's metamodel.
 */
public final class Nesting
{
	private Nesting()
	{
		// holder
	}

	public static class Customer
	{
		@Index
		private String name;

		public Customer()
		{
			super();
		}

		public Customer(final String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return this.name;
		}
	}
}
