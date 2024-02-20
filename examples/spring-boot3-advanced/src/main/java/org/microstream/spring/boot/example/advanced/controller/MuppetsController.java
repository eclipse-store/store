package org.microstream.spring.boot.example.advanced.controller;

import org.microstream.spring.boot.example.advanced.service.MuppetsInPort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/muppets")
public class MuppetsController {

  private final MuppetsInPort muppets;

  public MuppetsController(MuppetsInPort muppets) {
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
  public void init() {
    muppets.initialize();
  }

}
