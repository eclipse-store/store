
package org.eclipse.store.storage.restclient.exceptions;

/*-
 * #%L
 * EclipseStore Storage REST Client
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

public class StorageViewExceptionMissingTypeDescription extends StorageViewException
{
	private final long missingTypeId;
	
	public StorageViewExceptionMissingTypeDescription(
		final long missingTypeId
	)
	{
		super();
		
		this.missingTypeId = missingTypeId;
	}
	
	public long missingTypeId()
	{
		return this.missingTypeId;
	}
	
	@Override
	public String assembleDetailString()
	{
		return "Missing type description, typeId=" + this.missingTypeId;
	}
	
}
