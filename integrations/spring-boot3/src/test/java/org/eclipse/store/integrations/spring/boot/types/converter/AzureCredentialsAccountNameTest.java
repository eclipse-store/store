package org.eclipse.store.integrations.spring.boot.types.converter;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot
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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * The Azure shared-key credentials property historically had a typo in the
 * binding name: the field was called {@code accountMame}, so only
 * {@code ...credentials.account-mame} bound and the documented
 * {@code ...credentials.account-name} was silently ignored (the converter then
 * dropped the null value and the account name never reached the storage
 * configuration).
 *
 * <p>These tests verify both states: the documented {@code account-name}
 * property binds and reaches the converted configuration, and the misspelled
 * {@code account-mame} keeps working as a deprecated alias for anyone who
 * worked around the typo.
 */
class AzureCredentialsAccountNameTest
{
    private static final String PROPERTY_BASE =
            "org.eclipse.store.storage-filesystem.azure.storage.credentials";

    private static final String CONVERTED_KEY =
            "storage-filesystem.azure.storage.credentials.account-name";

    private final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();

    @Test
    void documentedAccountNamePropertyBindsAndReachesConfiguration()
    {
        final EclipseStoreProperties properties = this.bind(Map.of(
                PROPERTY_BASE + ".type", "shared-key",
                PROPERTY_BASE + ".account-name", "my-account"
        ));

        assertEquals("my-account",
                properties.getStorageFilesystem().getAzure().getStorage().getCredentials().getAccountName(),
                "the documented account-name property must bind");

        final Map<String, String> map = this.converter.convertConfigurationToMap(properties);
        assertEquals("my-account", map.get(CONVERTED_KEY),
                "the bound account name must reach the converted storage configuration");
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedAccountMameAliasStillBinds()
    {
        final EclipseStoreProperties properties = this.bind(Map.of(
                PROPERTY_BASE + ".type", "shared-key",
                PROPERTY_BASE + ".account-mame", "legacy-account"
        ));

        assertEquals("legacy-account",
                properties.getStorageFilesystem().getAzure().getStorage().getCredentials().getAccountName(),
                "the historical misspelled property must keep binding as a deprecated alias");
        assertEquals("legacy-account",
                properties.getStorageFilesystem().getAzure().getStorage().getCredentials().getAccountMame(),
                "the deprecated getter must expose the same value");

        final Map<String, String> map = this.converter.convertConfigurationToMap(properties);
        assertEquals("legacy-account", map.get(CONVERTED_KEY),
                "the alias-bound account name must reach the converted storage configuration");
    }

    private EclipseStoreProperties bind(final Map<String, String> propertyValues)
    {
        final Map<String, String> source = new HashMap<>(propertyValues);
        final Binder binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bind("org.eclipse.store", Bindable.of(EclipseStoreProperties.class))
                .orElseThrow(() -> new IllegalStateException("binding produced no result"));
    }
}
