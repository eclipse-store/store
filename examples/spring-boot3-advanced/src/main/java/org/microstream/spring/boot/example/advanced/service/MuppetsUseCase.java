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

import org.microstream.spring.boot.example.advanced.storage.MuppetStorage;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MuppetsUseCase implements MuppetsInPort
{
    private final MuppetStorage storage;

    public MuppetsUseCase(MuppetStorage storage)
    {
        this.storage = storage;
    }

    @Override
    public String getMuppet(Integer id)
    {
        return storage.oneMuppet(id);
    }

    @Override
    public List<String> getAllMuppets()
    {
        return storage.allMuppets();
    }

    @Override
    public void initialize()
    {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("muppets.txt"))
        {
            List<String> muppets = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
            if (storage.allMuppets().containsAll(muppets))
            {
                return;
            }
            storage.addMuppets(muppets);
        } catch (IOException e)
        {
            throw new IllegalStateException("Could not read default muppets");
        }
    }
}
