package org.eclipse.store.gigamap.constraint;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.types.CustomConstraint;

public class NoBadValueConstraint extends CustomConstraint.AbstractSimple<Entity>
{
    final static String BAD = "BAD";

    @Override
    public boolean isViolated(final Entity entity)
    {
        return entity.getWord().contains(BAD);
    }
}
