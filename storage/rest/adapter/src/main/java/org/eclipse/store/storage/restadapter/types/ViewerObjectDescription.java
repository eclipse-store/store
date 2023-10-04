package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
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

public class ViewerObjectDescription
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private String objectId;
	private String typeId;
	private String length;
	private String[] variableLength;
	private boolean simplified;
	private Object[] data;
	private ViewerObjectDescription[] references;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerObjectDescription()
	{
		super();
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public String getObjectId()
	{
		return this.objectId;
	}

	public void setObjectId(final String objectId)
	{
		this.objectId = objectId;
	}

	public String getTypeId()
	{
		return this.typeId;
	}

	public void setTypeId(final String typeId)
	{
		this.typeId = typeId;
	}

	public String getLength()
	{
		return this.length;
	}

	public void setLength(final String length)
	{
		this.length = length;
	}

	public Object[] getData()
	{
		return this.data;
	}

	public void setData(final Object[] data)
	{
		this.data = data;
	}

	public ViewerObjectDescription[] getReferences()
	{
		return this.references;
	}

	public void setReferences(final ViewerObjectDescription[] references)
	{
		this.references = references;
	}

	public String[] getVariableLength()
	{
		return this.variableLength ;
	}

	public void setVariableLength(final String[] variableLength)
	{
		this.variableLength = variableLength;
	}

	public void setSimplified(final boolean simplified)
	{
		this.simplified = simplified;
	}

	public boolean getSimplified()
	{
		return this.simplified;
	}


}
