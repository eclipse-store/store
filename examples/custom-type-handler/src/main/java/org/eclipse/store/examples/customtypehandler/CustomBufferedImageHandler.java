package org.eclipse.store.examples.customtypehandler;

/*-
 * #%L
 * EclipseStore Example Custom Type Handler
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustomValue;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;

public class CustomBufferedImageHandler extends AbstractBinaryHandlerCustomValue<BufferedImage, byte[]>
{
	private static byte[] instanceState(final BufferedImage instance)
	{
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try ( ImageOutputStream ios = new MemoryCacheImageOutputStream(bos))
		{
			ImageIO.write(instance, "png", ios);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
		return bos.toByteArray();
	}
	
	private static byte[] binaryState(final Binary data)
	{
		return data.build_bytes();
	}
	
	
	public CustomBufferedImageHandler()
	{
		super(
			BufferedImage.class,
			CustomFields(
				bytes("imageData")
			)
		);
	}

	@Override
	public void store(
		final Binary bytes,
		final BufferedImage instance,
		final long objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		bytes.store_bytes(
			this.typeId(),
			objectId,
			instanceState(instance)
		);
	}
	
	@Override
	public BufferedImage create(
		final Binary data,
		final PersistenceLoadHandler handler
	)
	{
		final byte[] imageData = binaryState(data);
		
		BufferedImage image = null;
			
		try(ByteArrayInputStream bis = new ByteArrayInputStream(imageData))
		{
			image = ImageIO.read(bis);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}

		return image;
	}

	@Override
	public byte[] getValidationStateFromInstance(BufferedImage instance)
	{
		return instanceState(instance);
	}

	@Override
	public byte[] getValidationStateFromBinary(Binary data)
	{
		return binaryState(data);
	}

	@Override
	public boolean hasVaryingPersistedLengthInstances()
	{
		return true;
	}

}
