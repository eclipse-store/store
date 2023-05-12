package org.eclipse.storage.base.bytes;

/*-
 * #%L
 * Eclipse Store Base utilities
 * %%
 * Copyright (C) 2019 - 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.serializer.exceptions.ArrayCapacityException;
import org.eclipse.serializer.math.XMath;

import java.io.*;
import java.nio.charset.Charset;

public final class VarByte implements Externalizable
{
	// (24.07.2013 TM)FIXME: Overhaul VarByte via VarString implementation

	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////

	//have to be 2^n values
	private static final int
		CAPACITY_MIN   =  4, //needed for appendNull algorithm (and performance)
		CAPACITY_SMALL = 64
	;

	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	private static final int boundPow2(final int n)
	{
		//if desired capacity is not boundable by shifting, max capacity is required
		if(XMath.isGreaterThanHighestPowerOf2(n))
		{
			return Integer.MAX_VALUE;
		}

		//normal case: start at min capacity and double it until it fits the desired capacity
		int p2 = CAPACITY_MIN;
		while(p2 < n)
		{
			p2 <<= 1;
		}
		return p2;
	}

	public static VarByte New()
	{
		return new VarByte(CAPACITY_SMALL);
	}


	/**
	 * Use this constructor only if really a specific size is needed or list of bytes to be handled is huge.<br>
	 * Otherwise, use the factory methods as they are faster due to skipping capacity checks and bounds adjustment.<br>
	 * <p>
	 * Note that the given {@code initialCapacity} will still be adjusted to the next higher 2^n bounding value.
	 * @param initialCapacity the initial size of the buffer
	 * @return a new VarByte instance
	 */
	public static VarByte New(final int initialCapacity)
	{
		if(initialCapacity < 0)
		{
			throw new IllegalArgumentException("initial capacity may not be negative: " + initialCapacity);
		}

		return new VarByte(boundPow2(initialCapacity));
	}



	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	byte[] data;
	int    size;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	// to satisfy requirements of Externalizable
	private VarByte()
	{
		this(CAPACITY_MIN);
	}

	private VarByte(final int uncheckedInitialCapacity)
	{
		super();
		this.data = new byte[uncheckedInitialCapacity];
		this.size = 0;
	}



	///////////////////////////////////////////////////////////////////////////
	// override methods //
	/////////////////////

	@Override
	public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException
	{
		final int size;
		final byte[] data = new byte[boundPow2(size = in.read())];

		for(int i = 0; i < size; i++)
		{
			data[i] = in.readByte();
		}
		this.data = data;
		this.size = size;
	}

	@Override
	public void writeExternal(final ObjectOutput out) throws IOException
	{
		final int size;
		final byte[] data = this.data;

		out.write(size = this.size);
		for(int i = 0; i < size; i++)
		{
			out.writeByte(data[i]);
		}
	}

	@Override
	public String toString()
	{
		return new String(this.data, 0, this.size);
	}

	public String toString(final Charset charset) throws UnsupportedEncodingException
	{
		return new String(this.data, 0, this.size, charset);
	}



	///////////////////////////////////////////////////////////////////////////
	// declared methods //
	/////////////////////

	// copied from BulkList. Maintain there!
	public void ensureFreeCapacity(final int requiredFreeCapacity)
	{
		// as opposed to ensureCapacity(size + requiredFreeCapacity), this subtraction is overflow-safe
		if(this.data.length - this.size >= requiredFreeCapacity)
		{
			return; // already enough free capacity
		}

		// overflow-safe check for unreachable capacity
		if(Integer.MAX_VALUE - this.size < requiredFreeCapacity)
		{
			throw new ArrayCapacityException((long)requiredFreeCapacity + this.size);
		}

		// calculate new capacity
		final int newSize = this.size + requiredFreeCapacity;
		int newCapacity;
		if(XMath.isGreaterThanHighestPowerOf2(newSize))
		{
			// JVM technical limit
			newCapacity = Integer.MAX_VALUE;
		}
		else
		{
			newCapacity = this.data.length;
			while(newCapacity < newSize)
			{
				newCapacity <<= 1;
			}
		}

		// rebuild storage
		final byte[] data = new byte[newCapacity];
		System.arraycopy(this.data, 0, data, 0, this.size);
		this.data = data;
	}


	private void internalAppend(final byte[] bytes, final int offset, final int length)
	{
		this.ensureFreeCapacity(length);
		System.arraycopy(bytes, offset, this.data, this.size, length);
		this.size += length;
	}

	public VarByte append(final byte[] bytes, final int offset, final int length)
	{
		this.internalAppend(bytes, offset, length);
		return this;
	}


	/**
	 * Only preferable for security reasons.
	 * 
	 * @return this
	 */
	public VarByte clear()
	{
		final byte[] data = this.data;
		for(int i = 0, length = data.length; i < length; i++)
		{
			data[i] = 0;
		}
		this.size = 0;
		return this;
	}


}
