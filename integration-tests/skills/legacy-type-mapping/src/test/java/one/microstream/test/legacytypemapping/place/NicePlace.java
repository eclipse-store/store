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

/**
 * NEW shape of {@code NicePlace} as described in {@code examples-expanded.md}
 * Example 3. The corresponding legacy handler exists only to verify the API
 * surface compiles and registers; no legacy binaries are loaded.
 */
public class NicePlace
{
    String   name;
    Location location;

    public NicePlace()
    {
        // default constructor required by the legacy handler's create(...)
    }

    public String name()
    {
        return this.name;
    }

    public Location location()
    {
        return this.location;
    }
}
