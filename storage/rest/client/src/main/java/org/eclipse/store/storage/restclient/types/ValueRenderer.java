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

import java.util.function.BiFunction;

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.collections.types.XTable;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;

public interface ValueRenderer extends BiFunction<String, ViewerObjectDescription, String>
{
	public static Provider DefaultProvider()
	{
		final ValueRenderer stringLiteralRenderer    = ValueRenderer.StringLiteral   ();
		final ValueRenderer characterLiteralRenderer = ValueRenderer.CharacterLiteral();
		
		final XTable<String, ValueRenderer> valueRenderers = EqHashTable.New();
		valueRenderers.put(String.class.getName()       , stringLiteralRenderer   );
		valueRenderers.put(StringBuffer.class.getName() , stringLiteralRenderer   );
		valueRenderers.put(StringBuilder.class.getName(), stringLiteralRenderer   );
		valueRenderers.put(VarString.class.getName()    , stringLiteralRenderer   );
		valueRenderers.put(char.class.getName()         , characterLiteralRenderer);
		
		return new Provider.Default(
			valueRenderers.immure(), 
			ValueRenderer.Default()
		);
	}
	
	
	public static interface Provider
	{
		public ValueRenderer provideValueRenderer(
			String typeName
		);
		
		
		public static class Default implements Provider
		{
			private final XGettingTable<String, ValueRenderer> valueRenderers;
			private final ValueRenderer                        defaultRenderer;
			
			Default(
				final XGettingTable<String, ValueRenderer> valueRenderers,
				final ValueRenderer defaultRenderer
			)
			{
				super();
			
				this.valueRenderers  = valueRenderers;
				this.defaultRenderer = defaultRenderer;
			}
			
			@Override
			public ValueRenderer provideValueRenderer(
				final String typeName
			)
			{
				final ValueRenderer renderer = this.valueRenderers.get(typeName);
				return renderer != null
					? renderer
					: this.defaultRenderer;
			}
		}
	}
	
	
	public static ValueRenderer Default()
	{
		return (value, reference) -> value;
	}
		
	public static ValueRenderer StringLiteral()
	{
		return (value, reference) -> {
			
			final VarString vs = VarString.New(value.length() + 2)
				.add('"');
			
			for(int i = 0, len = value.length(); i < len; i++)
			{
				final char ch = value.charAt(i);
				
				switch(ch)
				{
					case '\b':
						vs.add("\\b");
					break;
					case '\t':
						vs.add("\\t");
					break;
					case '\n':
						vs.add("\\n");
					break;
					case '\f':
						vs.add("\\f");
					break;
					case '\r':
						vs.add("\\r");
					break;
					case '\"':
						vs.add("\\\"");
					break;
					case '\\':
						vs.add("\\\\");
					break;
					default:
						vs.add(ch);
					break;
				}
			}
			
			return vs.add('"')
				.toString();
		};
	}
	
	public static ValueRenderer CharacterLiteral()
	{
		return (value, reference) -> {

			final VarString vs = VarString.New(4)
				.add('\'');
			
			final char ch = value.charAt(0);
			switch(ch)
			{
				case '\b':
					vs.add("\\b");
				break;
				case '\t':
					vs.add("\\t");
				break;
				case '\n':
					vs.add("\\n");
				break;
				case '\f':
					vs.add("\\f");
				break;
				case '\r':
					vs.add("\\r");
				break;
				case '\'':
					vs.add("\\'");
				break;
				case '\\':
					vs.add("\\\\");
				break;
				default:
					vs.add(ch);
				break;
			}
			
			return vs.add('\'')
				.toString();
		};
	}
	
}
