package org.eclipse.store.gigamap.annotations;

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

import org.eclipse.store.gigamap.types.GigaMap;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation to indicate that the annotated field must have a unique value across all instances of the class in a {@link GigaMap}.
 * This is a marker annotation and does not contain any attributes.
 * <p>
 * This annotation enforces the uniqueness of the field it is applied to, often in the context of persistence or
 * data modeling systems where ensuring unique values for a field is required.
 * <p>
 * The annotation is retained at runtime and can be applied specifically to fields.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Unique
{
	// marker interface
}
