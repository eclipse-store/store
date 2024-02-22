package org.eclipse.store.integrations.spring.boot.types.concurrent;

/*-
 * #%L
 * spring-boot3
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code Write} annotation is used to mark methods that should acquire a write lock before execution.
 * This annotation is used in conjunction with the {@code LockAspect} to handle concurrent access to shared resources.
 *
 * <p>Here's an example of how to use this annotation:</p>
 * <pre>
 * <code>
 * public class MyClass {
 *
 *     {@literal @}Write
 *      public void myMethod() {
 *         // method implementation
 *      }
 * }
 * </code>
 * </pre>
 * <p>
 * In this example, the {@code myMethod} method will acquire a write lock before execution.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Write
{
}
