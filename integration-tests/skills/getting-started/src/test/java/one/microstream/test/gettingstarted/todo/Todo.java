package one.microstream.test.gettingstarted.todo;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - getting-started
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

public class Todo
{
    private String  title;
    private boolean done;

    public Todo()
    {
        // for storage
    }

    public Todo(final String title, final boolean done)
    {
        this.title = title;
        this.done  = done;
    }

    public String  title() { return this.title; }
    public boolean done()  { return this.done;  }

    public void setTitle(final String title) { this.title = title; }
    public void setDone(final boolean done)  { this.done  = done;  }
}
