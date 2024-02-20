package org.microstream.spring.boot.example.advanced.storage;

import java.util.List;

public interface MuppetStorage {
  String oneMuppet(Integer id);
  List<String> allMuppets();
  int addMuppets(List<String> muppets);
}
