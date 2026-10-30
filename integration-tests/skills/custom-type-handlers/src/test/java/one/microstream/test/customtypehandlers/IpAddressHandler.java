package one.microstream.test.customtypehandlers;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - custom-type-handlers
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

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryField;
import org.eclipse.serializer.persistence.binary.types.CustomBinaryHandler;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;

/**
 * Pattern B (pure primitive, constructor-initializable) from SKILL.md.
 */
public class IpAddressHandler extends CustomBinaryHandler<IpAddress>
{
    final BinaryField<IpAddress>
        packed = Field_int(IpAddress::packed);

    public IpAddressHandler()
    {
        super(IpAddress.class);
    }

    @Override
    public IpAddress create(final Binary data, final PersistenceLoadHandler handler)
    {
        return new IpAddress(this.packed.read_int(data));
    }
}
