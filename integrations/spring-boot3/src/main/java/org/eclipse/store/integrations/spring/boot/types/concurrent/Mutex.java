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
 * The {@code Mutex} annotation can be used to specify a named lock for a type or a method.
 * This annotation is used in conjunction with the {@code LockAspect} to handle concurrent access to shared resources.
 *
 * <p>Here's an example of how to use this annotation:</p>
 * <pre>
 * <code>
 * {@literal @}Mutex("myLock")
 *    public class MyClass {
 *
 *      {@literal @}Mutex("myMethodLock")
 *       public void myMethod() {
 *          // method implementation
 *       }
 *  }
 * </code>
 * </pre>
 * <p>
 * In this example, the {@code MyClass} type and the {@code myMethod} method each have their own named lock.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mutex
{
    String value();
}
