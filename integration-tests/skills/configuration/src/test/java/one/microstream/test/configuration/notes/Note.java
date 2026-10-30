package one.microstream.test.configuration.notes;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - configuration
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

import java.time.Instant;

public class Note
{
    private final String  title;
    private final String  body;
    private final Instant createdAt;

    public Note(String title, String body, Instant createdAt)
    {
        this.title     = title;
        this.body      = body;
        this.createdAt = createdAt;
    }

    public String  title()     { return title; }
    public String  body()      { return body; }
    public Instant createdAt() { return createdAt; }
}
