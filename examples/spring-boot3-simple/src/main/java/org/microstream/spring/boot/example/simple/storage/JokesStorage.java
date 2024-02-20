package org.microstream.spring.boot.example.simple.storage;

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
 * The {@code JokesStorage} interface provides methods for storing and retrieving jokes.
 * It is used as a part of the joke service in the application.
 *
 * <p>Here's an example of how to use this interface:</p>
 * <pre>
 * <code>
 * JokesStorage jokesStorage = ... // get an instance of JokesStorage
 * jokesStorage.addNewJoke("Why don't scientists trust atoms? Because they make up everything!");
 * List<String> allJokes = jokesStorage.allJokes();
 * </code>
 * </pre>
 *
 * In this example, a new joke is added to the storage and then all jokes are retrieved.
 */
public interface JokesStorage
{
    /**
     * Returns a joke with the specified ID.
     *
     * @param id The ID of the joke.
     * @return The joke with the specified ID.
     */
    String oneJoke(Integer id);

    /**
     * Returns all jokes in the storage.
     *
     * @return A list of all jokes.
     */
    List<String> allJokes();

    /**
     * Adds a new joke to the storage.
     *
     * @param joke The joke to add.
     * @return The ID of the added joke.
     */
    Integer addNewJoke(String joke);

    /**
     * Adds multiple jokes to the storage.
     *
     * @param jokes The jokes to add.
     */
    void addJokes(List<String> jokes);

    /**
     * Saves all jokes to the storage.
     *
     * @param jokes The jokes to save.
     * @return The number of jokes saved.
     */
    Integer saveAllJokes(List<String> jokes);
}
