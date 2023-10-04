package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

import org.eclipse.serializer.math.XMath;

@FunctionalInterface
public interface StorageInitialDataFileNumberProvider
{
	public int provideInitialDataFileNumber(int channelIndex);
	
	
	
	public final class Default implements StorageInitialDataFileNumberProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final int constantInitialFileNumber;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public Default(final int constantInitialFileNumber)
		{
			super();
			this.constantInitialFileNumber = XMath.notNegative(constantInitialFileNumber);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public int provideInitialDataFileNumber(final int channelIndex)
		{
			return this.constantInitialFileNumber;
		}
		
	}
	
}
