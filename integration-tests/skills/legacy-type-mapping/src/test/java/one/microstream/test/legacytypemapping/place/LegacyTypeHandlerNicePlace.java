package one.microstream.test.legacytypemapping.place;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - legacy-type-mapping
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
import org.eclipse.serializer.persistence.binary.types.BinaryLegacyTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceReferenceLoader;
import org.eclipse.serializer.util.X;

/**
 * Mirrors {@code examples-expanded.md} Example 3 in the legacy-type-mapping skill.
 *
 * The handler declares the OLD binary layout (two String references) and is what
 * Eclipse Store would invoke when loading a {@code NicePlace} record stored under
 * the previous shape. In this skill-review module no legacy data exists, so the
 * test only verifies registration succeeds.
 */
public class LegacyTypeHandlerNicePlace
    extends BinaryLegacyTypeHandler.AbstractCustom<NicePlace>
{
    private static final long
        OFFSET_name       = 0,
        OFFSET_directions = OFFSET_name + Binary.objectIdByteLength();

    public LegacyTypeHandlerNicePlace()
    {
        super(
            NicePlace.class,
            X.List(
                CustomField(String.class, "name"),
                CustomField(String.class, "directions")
            )
        );
    }

    @Override
    public NicePlace create(final Binary bytes, final PersistenceLoadHandler lh)
    {
        return new NicePlace();
    }

    @Override
    public void updateState(final Binary bytes, final NicePlace inst, final PersistenceLoadHandler lh)
    {
        final String name       = (String) lh.lookupObject(bytes.read_long(OFFSET_name));
        final String directions = (String) lh.lookupObject(bytes.read_long(OFFSET_directions));

        inst.name     = name;
        inst.location = new Location(directions, 0.0, 0.0);
    }

    @Override
    public void iterateLoadableReferences(final Binary bytes, final PersistenceReferenceLoader it)
    {
        it.acceptObjectId(bytes.read_long(OFFSET_name));
        it.acceptObjectId(bytes.read_long(OFFSET_directions));
    }

    @Override
    public boolean hasPersistedReferences()
    {
        return true;
    }

    @Override
    public boolean hasVaryingPersistedLengthInstances()
    {
        return false;
    }
}
