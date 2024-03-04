package org.microstream.spring.boot.example.advanced.controller;

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

import org.microstream.spring.boot.example.advanced.service.JokesServices;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The {@code JokesController} class is a Spring REST controller that provides endpoints for managing jokes.
 * It uses the {@code JokesServices} to perform operations on jokes.
 *
 * <p>This class is annotated with {@code @RestController}, which means it is a Spring MVC controller and its methods return domain objects instead of views.</p>
 *
 * <p>Here's an example of how to use this controller:</p>
 * <pre>
 * <code>
 * GET /jokes/joke?id=1
 * POST /jokes/add
 * {
 *     "joke": "Why don't scientists trust atoms? Because they make up everything!"
 * }
 * </code>
 * </pre>
 *
 * In this example, the first request gets the joke with ID 1 and the second request adds a new joke.
 */
@RestController
@RequestMapping("/jokes")
public class JokesController
{

    private final JokesServices jokesServices;

    /**
     * Constructs a new {@code JokesController} with the provided {@code JokesServices}.
     * JokesServices is a Spring component and is injected by Spring.
     *
     * @param jokesServices The services used to perform operations on jokes.
     */
    public JokesController(JokesServices jokesServices)
    {
        this.jokesServices = jokesServices;
    }

    /**
     * Returns all jokes.
     *
     * @return A list of all jokes.
     */
    @GetMapping
    public List<String> getAll()
    {
        return jokesServices.allJokes();
    }

    /**
     * Returns the joke with the specified ID.
     *
     * @param id The ID of the joke.
     * @return The joke with the specified ID.
     */
    @GetMapping("/joke")
    public String getOneJoke(@RequestParam(name = "id") Integer id)
    {
        return jokesServices.oneJoke(id);
    }

    /**
     * Adds a new joke.
     *
     * @param joke The joke to add.
     * @return The ID of the added joke.
     */
    @PostMapping("/add")
    public Integer putOne(@RequestBody String joke)
    {
        return jokesServices.addNewJoke(joke);
    }

    /**
     * Loads predefined jokes.
     */
    @PostMapping("/init")
    public void init()
    {
        jokesServices.loadPredefinedJokes();
    }

    /**
     * Inserts a new joke.
     * This method functions similarly to the 'putOne' method. However, it differs in its implementation by utilizing a nested locking mechanism.
     * The '@Write Lock' annotation is applied at the service layer, invoking the storage layer within another locking annotation.
     * This implementation is solely intended for demonstration purposes.
     *
     * @param joke The new joke to be added.
     * @return The position of the currently inserted joke in storage.
     */
    @PostMapping("/insert")
    public Integer insert(@RequestBody String joke)
    {
        return jokesServices.insertNewJoke(joke);
    }
}
