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

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryField;
import org.eclipse.serializer.persistence.binary.types.CustomBinaryHandler;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;

/**
 * Pattern A (two references, final fields, populate via XMemory.setObject)
 * from SKILL.md.
 */
public class HostEntryHandler extends CustomBinaryHandler<HostEntry>
{
    final BinaryField<HostEntry>
        hostname = Field(String.class,    HostEntry::hostname),
        address  = Field(IpAddress.class, HostEntry::address);

    public HostEntryHandler()
    {
        super(HostEntry.class);
    }

    @Override
    public HostEntry create(final Binary data, final PersistenceLoadHandler handler)
    {
        return new HostEntry(null, null);
    }

    @Override
    public void initializeState(final Binary data, final HostEntry inst, final PersistenceLoadHandler handler)
    {
        XMemory.setObject(inst,
            getClassDeclaredFieldOffset(HostEntry.class, "hostname"),
            this.hostname.readReference(data, handler));
        XMemory.setObject(inst,
            getClassDeclaredFieldOffset(HostEntry.class, "address"),
            this.address.readReference(data, handler));
    }
}
