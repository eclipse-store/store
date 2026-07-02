package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

/**
 * Policy controlling whether and how the storage validates "trusted" reference object ids at store time.
 * <p>
 * A store's data may contain references to object ids whose entities are not part of the store itself:
 * the storer trusted them to already exist in the storage (instances found in the global object registry,
 * or unloaded {@code Lazy} references' cached ids). If that trust is wrong &mdash; e.g. the referenced
 * entity was garbage-collected on disk while its instance survived in memory &mdash; the store would
 * silently persist a dangling reference that only surfaces as a
 * {@code StorageExceptionConsistency: No entity found for objectId} after a later restart.
 * <p>
 * With validation enabled, each channel checks the trusted ids assigned to it against its entity registry
 * before writing the store's data:
 * <ul>
 *   <li>{@link #OFF} &mdash; no validation; the ids are not even collected by the storer (zero overhead).</li>
 *   <li>{@link #LOG} &mdash; missing ids are logged as an error and reported to the
 *       {@link StorageEventLogger}, but the store proceeds.</li>
 *   <li>{@link #FAIL} &mdash; the store is rejected atomically with a
 *       {@link org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference}.</li>
 * </ul>
 */
public enum StorageReferenceValidationPolicy
{
	/**
	 * No validation; trusted object ids are not collected at all.
	 */
	OFF,

	/**
	 * Validate and log detected dangling references, but let the store proceed.
	 */
	LOG,

	/**
	 * Validate and reject a store containing dangling references atomically.
	 */
	FAIL;


	/**
	 * @return whether trusted object ids are collected and validated at all.
	 */
	public boolean isValidating()
	{
		return this != OFF;
	}

	/**
	 * @return whether a detected dangling reference rejects the store.
	 */
	public boolean isFailing()
	{
		return this == FAIL;
	}


	/**
	 * Parses the external configuration value ({@code "off"}, {@code "log"} or {@code "fail"},
	 * case-insensitive).
	 *
	 * @param value the configuration value.
	 *
	 * @return the parsed policy.
	 *
	 * @throws IllegalArgumentException on any other value.
	 */
	public static StorageReferenceValidationPolicy parse(final String value)
	{
		switch(value.trim().toLowerCase())
		{
			case "off" : return OFF ;
			case "log" : return LOG ;
			case "fail": return FAIL;
			default    : throw new IllegalArgumentException(
				"Invalid reference validation policy: \"" + value + "\". Valid values are: off, log, fail."
			);
		}
	}

}
