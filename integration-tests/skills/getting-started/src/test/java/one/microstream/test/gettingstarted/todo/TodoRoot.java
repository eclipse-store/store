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

import java.util.ArrayList;
import java.util.List;

public class TodoRoot
{
    private final List<Todo> todos = new ArrayList<>();

    public List<Todo> todos() { return this.todos; }
}
