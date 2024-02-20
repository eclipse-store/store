
package org.eclipse.store.integrations.cdi.types.extension;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.enterprise.inject.spi.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.store.integrations.cdi.Storage;
import org.eclipse.store.storage.types.StorageManager;


/**
 * This extension will look for Objects that are marked with {@link Storage}.
 */
@ApplicationScoped
public class StorageExtension implements Extension
{

    private static final Logger LOGGER = Logger.getLogger(StorageExtension.class.getName());

    private final Set<Class<?>> storageRoot = new HashSet<>();

    private final Map<Class<?>, Set<InjectionPoint>> storageInjectionPoints = new HashMap<>();

    private final Set<String> storageManagerConfigInjectionNames = new HashSet<>();

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd) {
        LOGGER.info("beginning the scanning process");
    }

    <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> pat) {
        LOGGER.info("scanning type: " + pat.getAnnotatedType().getJavaClass().getName());
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery abd) {
        LOGGER.info("finished the scanning process");
    }

    <T> void loadEntity(@Observes @WithAnnotations({Storage.class}) final ProcessAnnotatedType<T> target)
    {
        final AnnotatedType<T> annotatedType = target.getAnnotatedType();
        if (annotatedType.isAnnotationPresent(Storage.class))
        {

            final Class<T> javaClass = target.getAnnotatedType()
                    .getJavaClass();
            this.storageRoot.add(javaClass);
            LOGGER.info("New class found annotated with @Storage is " + javaClass);
        }
    }

    void collectInjectionsFromStorageBean(@Observes final ProcessInjectionPoint<?, ?> pip)
    {
        final InjectionPoint ip = pip.getInjectionPoint();
        if (ip.getBean() != null && ip.getBean()
                .getBeanClass()
                .getAnnotation(Storage.class) != null)
        {
            this.storageInjectionPoints
                    .computeIfAbsent(ip.getBean()
                            .getBeanClass(), k -> new HashSet<>())
                    .add(ip);
        }
        // Is @Inject @ConfigProperty on StorageManager?
        if (this.isStorageManagerFromConfig(ip))
        {
            this.storageManagerConfigInjectionNames.add(this.getConfigPropertyValueOf(ip));

        }
    }

    private String getConfigPropertyValueOf(final InjectionPoint ip)
    {
        return ip.getQualifiers()
                .stream()
                .filter(q -> q.annotationType()
                        .isAssignableFrom(ConfigProperty.class))
                .findAny()
                .map(q -> ((ConfigProperty) q).name())
                .orElse("");
    }

    private boolean isStorageManagerFromConfig(final InjectionPoint ip)
    {
        return ip.getMember() instanceof Field
                && ((Field) ip.getMember()).getType().isAssignableFrom(StorageManager.class)
                && ip.getQualifiers()
                .stream()
                .anyMatch(q -> q.annotationType()
                        .isAssignableFrom(ConfigProperty.class));
    }

    void onAfterBeanDiscovery(@Observes final AfterBeanDiscovery afterBeanDiscovery, final BeanManager beanManager)
    {
        LOGGER.info(String.format("Processing StorageExtension:  %d @Storage found", this.storageRoot.size()));
        if (this.storageRoot.size() > 1)
        {
            throw new IllegalStateException(
                    "In the application must have only one class with the Storage annotation, classes: "
                            + this.storageRoot);
        }
        if (this.storageManagerConfigInjectionNames.size() > 1 && !this.storageRoot.isEmpty())
        {
            throw new IllegalStateException(
                    "It is not supported to define multiple StorageManager's through @ConfigProperty in combination with a @Storage annotated class. Names : "
                            + this.storageManagerConfigInjectionNames);

        }
        this.storageRoot.forEach(entity ->
        {
            Set<InjectionPoint> injectionPoints = this.storageInjectionPoints.get(entity);
            if (injectionPoints == null)
            {
                injectionPoints = Collections.emptySet();
            }
            final StorageBean<?> bean = new StorageBean<>(beanManager, entity, injectionPoints);
            afterBeanDiscovery.addBean(bean);
        });
    }

    public Set<String> getStorageManagerConfigInjectionNames()
    {
        return this.storageManagerConfigInjectionNames;
    }

    public boolean hasStorageRoot()
    {
        return !this.storageRoot.isEmpty();
    }

    @Override
    public String toString()
    {
        return "StorageExtension{"
                +
                "storageRoot="
                + this.storageRoot
                +
                '}';
    }
}
