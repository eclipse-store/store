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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation to indicate that the annotated property serves as an identity for an object.
 * This is a marker annotation and does not contain any attributes.
 * <p>
 * This annotation, when applied to a property, signifies that the property is part of the identity
 * for instances of the class. It can be used in scenarios where differentiating between
 * instances based on an identity value is necessary.
 * <p>
 * The annotation is retained at runtime and can be applied to fields or no-argument getter methods.
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface Identity
{
	// marker interface
}
