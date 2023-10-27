package org.microstream.spring.boot.example.service;

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

import org.eclipse.store.integrations.spring.boot.types.concurent.Write;
import org.microstream.spring.boot.example.storage.JokesStorage;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class JokesServicesImpl implements JokesServices
{
    private final JokesStorage jokesStorage;


    @Override
    public String oneJoke(Integer id)
    {
        String joke;
        joke = jokesStorage.oneJoke(Objects.requireNonNullElse(id, 0));
        return joke;
    }

    public JokesServicesImpl(JokesStorage jokesStorage)
    {
        this.jokesStorage = jokesStorage;
    }

    @Override
    public List<String> allJokes()
    {
        return jokesStorage.allJokes();
    }

    @Override
    public Integer addNewJoke(String joke)
    {
        return jokesStorage.addNewJoke(joke);
    }

    @Override
    public void loadPredefinedJokes()
    {
        List<String> jokes = null;
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("jokes.txt");
        assert inputStream != null;
        jokes = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.toList());
        List<String> existingJokes = jokesStorage.allJokes();
        if (existingJokes.containsAll(jokes))
        {
            return;
        }
        jokesStorage.addJokes(jokes);
    }

    @Override
    @Write
    public Integer insertNewJoke(String joke)
    {
        List<String> jokes = jokesStorage.allJokes();
        jokes.add(joke);
        return jokesStorage.saveAllJokes(jokes);
    }
}
