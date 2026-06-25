package test.eclipse.store.collections.lazy.arraylist;

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

import java.util.Objects;

public class ListEntry {

    String id;

    public ListEntry(final String id) {
        super();
        this.id = id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        final ListEntry other = (ListEntry) obj;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public String toString() {
        return "ListEntry{" +
                "id='" + id + '\'' +
                '}';
    }
}
