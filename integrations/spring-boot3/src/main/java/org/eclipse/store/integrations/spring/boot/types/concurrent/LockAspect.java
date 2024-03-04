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

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.eclipse.serializer.util.logging.Logging;
import org.slf4j.Logger;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;


/**
 * The {@code LockAspect} class is a Spring component that provides a mechanism for handling concurrent access to shared resources.
 * It uses Aspect Oriented Programming (AOP) to intercept method calls that are annotated with {@code @Read} or {@code @Write} and applies the appropriate lock.
 * The class also supports the {@code @Mutex} annotation which can be used to specify a named lock for a method or a class.
 */
@Aspect
@Component
@Conditional(LockAspect.AspectJCondition.class)
public class LockAspect
{

    private final Logger logger = Logging.getLogger(LockAspect.class);

    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * Intercepts method calls that are annotated with {@code @Read} and applies a read lock.
     *
     * @param joinPoint The join point representing the method call.
     * @return The result of the method call.
     * @throws Throwable If the method call throws an exception.
     */
    @Around("@annotation(Read)")
    public Object readOperation(final ProceedingJoinPoint joinPoint) throws Throwable
    {
        final ReentrantReadWriteLock lock = this.findLock(joinPoint);
        lock.readLock().lock();
        this.logger.trace("lock readLock");
        Object proceed;
        try
        {
            proceed = joinPoint.proceed();
        } finally
        {
            lock.readLock().unlock();
            this.logger.trace("unlock readLock");
        }
        return proceed;
    }

    /**
     * Intercepts method calls that are annotated with {@code @Write} and applies a write lock.
     *
     * @param joinPoint The join point representing the method call.
     * @return The result of the method call.
     * @throws Throwable If the method call throws an exception.
     */
    @Around("@annotation(Write)")
    public Object writeOperation(final ProceedingJoinPoint joinPoint) throws Throwable
    {
        final ReentrantReadWriteLock lock = this.findLock(joinPoint);
        lock.writeLock().lock();
        this.logger.trace("write lock");

        Object proceed;
        try
        {
            proceed = joinPoint.proceed();
        } finally
        {
            lock.writeLock().unlock();
            this.logger.trace("write unlock");
        }

        return proceed;
    }

    /**
     * Finds the appropriate lock for the given join point.
     * If the method or the class of the method is annotated with {@code @Mutex}, the named lock is used.
     * Otherwise, the global lock is used.
     *
     * @param joinPoint The join point representing the method call.
     * @return The lock to be used.
     */
    private ReentrantReadWriteLock findLock(final ProceedingJoinPoint joinPoint)
    {
        final MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        final Method method = methodSignature.getMethod();

        //method annotation first
        ReentrantReadWriteLock finalLock;
        final Mutex annotation = method.getAnnotation(Mutex.class);
        if (annotation != null)
        {
            final String lockName = annotation.value();
            this.logger.trace("Found method lock annotation for lock: {}", lockName);
            finalLock = this.getOrCreateLock(lockName);
        } else
        {
            //class annotation second
            final Class<?> declaringClass = method.getDeclaringClass();
            final Mutex classAnnotation = declaringClass.getAnnotation(Mutex.class);
            if (classAnnotation != null)
            {
                final String classLockName = classAnnotation.value();
                this.logger.trace("Found class lock annotation for lock: {}", classLockName);
                finalLock = this.getOrCreateLock(classLockName);
            } else
            {
                // no annotation, use global lock
                this.logger.trace("Found no @Lockable annotation, use global lock");
                finalLock = this.globalLock;
            }
        }
        return finalLock;
    }

    /**
     * Gets or creates a named lock.
     *
     * @param lockName The name of the lock.
     * @return The named lock.
     */
    private ReentrantReadWriteLock getOrCreateLock(final String lockName)
    {
        return this.locks.computeIfAbsent(lockName, k -> {
            this.logger.trace("Lock for name: {} not found, creating a new one", lockName);
            return new ReentrantReadWriteLock();
        });
    }

    /**
     * A condition that checks if AspectJ is present on the classpath.
     * This condition is used to enable or disable the {@code LockAspect} based on the presence of AspectJ.
     */
    static class AspectJCondition implements Condition
    {
        @Override
        public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata)
        {
            return ClassUtils.isPresent("org.aspectj.lang.ProceedingJoinPoint", context.getClassLoader());
        }
    }
}
