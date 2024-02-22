package org.microstream.spring.boot.example.advanced.service;

/*-
 * #%L
 * spring-boot3-simple
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

import java.util.List;

/**
 * The {@code JokesServices} interface provides methods for managing jokes in the application.
 * It is used as a part of the joke service in the application.
 *
 * <p>Here's an example of how to use this interface:</p>
 * <pre>
 * <code>
 * JokesServices jokesServices = ... // get an instance of JokesServices
 * jokesServices.addNewJoke("Why don't scientists trust atoms? Because they make up everything!");
 * List<String> allJokes = jokesServices.allJokes();
 * </code>
 * </pre>
 * <p>
 * In this example, a new joke is added to the service and then all jokes are retrieved.
 */
public interface JokesServices
{
    /**
     * Returns a joke with the specified ID.
     *
     * @param id The ID of the joke.
     * @return The joke with the specified ID.
     */
    String oneJoke(Integer id);

    /**
     * Returns all jokes.
     *
     * @return A list of all jokes.
     */
    List<String> allJokes();

    /**
     * Adds a new joke.
     *
     * @param joke The joke to add.
     * @return The ID of the added joke.
     */
    Integer addNewJoke(String joke);

    /**
     * Loads predefined jokes.
     */
    void loadPredefinedJokes();

    /**
     * Inserts a new joke.
     *
     * @param joke The joke to insert.
     * @return The ID of the inserted joke.
     */
    Integer insertNewJoke(String joke);
}
