package one.microstream.test.rootandobjectgraph;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - root-and-object-graph
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

public final class AppSettings
{
    private final String locale;
    private final int    pageSize;

    public AppSettings(final String locale, final int pageSize)
    {
        this.locale   = locale;
        this.pageSize = pageSize;
    }

    public static AppSettings defaults()
    {
        return new AppSettings("en-US", 20);
    }

    public String locale()   { return this.locale; }
    public int    pageSize() { return this.pageSize; }
}
