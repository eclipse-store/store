
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

import java.util.HashMap;


public class MyBusinessApp
{
	private final HashMap<Integer, BusinessYear> businessYears = new HashMap<>();
	
	public MyBusinessApp()
	{
		super();
	}
	
	public HashMap<Integer, BusinessYear> getBusinessYears()
	{
		return this.businessYears;
	}
}
