
package org.eclipse.store.examples.helloworld;

/*-
 * #%L
 * EclipseStore Example Hello World
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

public class DataRoot
{
	private String content;

	public DataRoot()
	{
		super();
	}

	public String getContent()
	{
		return this.content;
	}

	public void setContent(final String content)
	{
		this.content = content;
	}

	@Override
	public String toString()
	{
		return "Root: " + this.content;
	}
}
