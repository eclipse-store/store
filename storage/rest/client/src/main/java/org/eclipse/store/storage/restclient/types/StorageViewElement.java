
package org.eclipse.store.storage.restclient.types;

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

import org.eclipse.serializer.chars.VarString;

import java.util.List;

public interface StorageViewElement
{
	public StorageView view();
	
	public StorageViewElement parent();
	
	public default <T extends StorageViewElement> T parentOfType(final Class<T> parentType)
	{
		StorageViewElement parent = this;
		while((parent = parent.parent()) != null)
		{
			if(parentType.isInstance(parent))
			{
				return parentType.cast(parent);
			}
		}
		return null;
	}
	
	public String name();
	
	public String value();
	
	public String simpleTypeName();
	
	public String qualifiedTypeName();
	
	public boolean hasMembers();
	
	public List<StorageViewElement> members(boolean forceRefresh);
	
	
	public static abstract class Abstract implements StorageViewElement
	{
		private final StorageView.Default view;
		private final StorageViewElement  parent;
		private final String              name;
		private final String              value;
		private final String              typeName;
		
		Abstract(
			final StorageView.Default view,
			final StorageViewElement parent,
			final String name,
			final String value,
			final String typeName
		)
		{
			super();
			
			this.view     = view;
			this.parent   = parent;
			this.name     = name;
			this.value    = value;
			this.typeName = typeName;
		}
		
		@Override
		public StorageView.Default view()
		{
			return this.view;
		}
		
		@Override
		public StorageViewElement parent()
		{
			return this.parent;
		}
		
		@Override
		public String name()
		{
			return this.name;
		}
		
		@Override
		public String value()
		{
			return this.value;
		}

		@Override
		public String simpleTypeName()
		{
			final String qualifiedTypeName = this.qualifiedTypeName();
			final int i = qualifiedTypeName.lastIndexOf('.');
			return i == -1
				? qualifiedTypeName
				: qualifiedTypeName.substring(i + 1);
		}

		@Override
		public String qualifiedTypeName()
		{
			final String typeName = this.typeName;
			return typeName == null
				? ""
				: typeName.startsWith("[")
					? qualifiedName(typeName)
					: typeName;
		}
		
		private static String qualifiedName(final String binaryName)
		{
			switch(binaryName.charAt(0))
			{
				case '[': return qualifiedName(binaryName.substring(1)).concat("[]");
				case 'L': return binaryName.substring(1, binaryName.length() - 1);
				case 'B': return "byte";
				case 'C': return "char";
				case 'D': return "double";
				case 'F': return "float";
				case 'I': return "int";
				case 'J': return "long";
				case 'S': return "short";
				case 'Z': return "boolean";
				default:
					return binaryName;
			}
		}
		
		@Override
		public String toString()
		{
			final String name;
			if((name = this.name) != null && name.length() > 0)
			{
				final VarString vs = VarString.New();
				vs.add(name);
				
				final String value;
				if((value = this.value) != null && value.length() > 0)
				{
					vs.add(" = ").add(value);
				}
				
				final String typeName;
				if((typeName = this.simpleTypeName()) != null && typeName.length() > 0)
				{
					vs.add(" (").add(typeName).add(")");
				}
				
				return vs.toString();
			}
			
			return super.toString();
		}
		
	}
	
}
