package org.microstream.spring.boot.example.advanced.controller;

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

import org.microstream.spring.boot.example.advanced.service.MuppetsInPort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/muppets")
public class MuppetsController
{

    private final MuppetsInPort muppets;

    public MuppetsController(MuppetsInPort muppets)
    {
        this.muppets = muppets;
    }

    @GetMapping
    public List<String> getAll()
    {
        return muppets.getAllMuppets();
    }

    @GetMapping("/muppet")
    public String getOneJoke(@RequestParam(name = "id") Integer id)
    {
        return muppets.getMuppet(id);
    }

    @PostMapping("/init")
    public void init()
    {
        muppets.initialize();
    }

}
