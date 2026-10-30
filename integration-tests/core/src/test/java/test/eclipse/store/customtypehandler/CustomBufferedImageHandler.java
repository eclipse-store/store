package test.eclipse.store.customtypehandler;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustomValue;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;


public class CustomBufferedImageHandler extends AbstractBinaryHandlerCustomValue<BufferedImage, BufferedImage>
{

    static boolean stored = false; // just to check, if the handler was called

    public CustomBufferedImageHandler()
    {
        super(BufferedImage.class, CustomFields(CustomField(long.class, "capacity"), bytes("value")));
    }

    @Override
    public boolean hasVaryingPersistedLengthInstances()
    {
        return false;
    }

    @Override
    public void store(final Binary bytes, final BufferedImage instance, final long objectId, final PersistenceStoreHandler handler)
    {
        stored = true; // just to check, if the handler was called
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            ImageIO.write(instance, "png", ios);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        bytes.store_bytes(this.typeId(), objectId, bos.toByteArray());
    }

    @Override
    public BufferedImage create(final Binary bytes, final PersistenceLoadHandler handler)
    {
        final byte[] blob = bytes.build_bytes();

        BufferedImage image;

        try (ByteArrayInputStream bis = new ByteArrayInputStream(blob)) {
            image = ImageIO.read(bis);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return image;
    }

    @Override
    public void validateState(final Binary data, final BufferedImage instance, final PersistenceLoadHandler handler)
    {
    }

    @Override
    public BufferedImage getValidationStateFromInstance(BufferedImage bufferedImage)
    {
        return bufferedImage;
    }

    @Override
    public BufferedImage getValidationStateFromBinary(Binary binary)
    {
        //TODO fix temporal -
        return null;
    }
}
