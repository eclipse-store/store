package org.microstream.spring.boot.example.advanced.service;

import java.util.List;

public interface MuppetsInPort {

  String getMuppet(Integer id);

  List<String> getAllMuppets();

  void initialize();

}
