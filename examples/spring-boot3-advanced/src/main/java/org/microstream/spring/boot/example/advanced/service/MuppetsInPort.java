package org.microstream.spring.boot.example.advanced.service;

/*-
 * #%L
 * EclipseStore Example Spring Boot3 Advanced
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

import java.util.List;

/**
 * The MuppetsInPort interface provides methods for managing Muppets in a port.
 * This includes retrieving a specific Muppet by its ID, getting all Muppets, and initializing the port.
 */
public interface MuppetsInPort
{
    /**
     * Retrieves a Muppet by its ID.
     *
     * @param id The ID of the Muppet to retrieve.
     * @return The Muppet as a String.
     */
    String getMuppet(Integer id);

    /**
     * Retrieves all Muppets in the port.
     *
     * @return A list of all Muppets as Strings.
     */
    List<String> getAllMuppets();

    /**
     * Initializes the port. This method should be called before any operations are performed on the port.
     * It is responsible for setting up necessary data structures and ensuring the port is ready for operations.
     */
    void initialize();

}
