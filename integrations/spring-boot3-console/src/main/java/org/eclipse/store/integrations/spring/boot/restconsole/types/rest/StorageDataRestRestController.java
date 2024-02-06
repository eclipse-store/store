package org.eclipse.store.integrations.spring.boot.restconsole.types.rest;

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.eclipse.store.storage.restadapter.types.ViewerRootDescription;
import org.eclipse.store.storage.restadapter.types.ViewerStorageFileStatistics;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Spring REST controller exposing storage data for the restclient.
 */
@RestController
@RequestMapping(value = "/status/store-data", produces = {MediaType.APPLICATION_JSON_VALUE})
public class StorageDataRestRestController {
  private final StorageRestAdapter adapter;

  public StorageDataRestRestController(
      StorageRestAdapter adapter
  ) {
    this.adapter = adapter;
  }

  @GetMapping(value = "/")
  public ResponseEntity<List<RouteWithMethodsDto>> getAvailableRoutes() {
    var routes = Stream.of(
        new RouteWithMethodsDto("/", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto("/root", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto("/dictionary", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto("/object/:oid", HttpMethod.GET.name().toLowerCase()),
        new RouteWithMethodsDto("/maintenance/filesStatistics", HttpMethod.GET.name().toLowerCase())
        ).toList();
    return ok(routes);
  }

  @GetMapping(value = "/root")
  public ResponseEntity<ViewerRootDescription> getRoot() {
    var root = adapter.getUserRoot();
    return ok(root);
  }

  @GetMapping(value = "/dictionary")
  public ResponseEntity<String> getDictionary() {
    var dictionary = adapter.getTypeDictionary();
    return ok(dictionary);
  }

  @GetMapping("/object/{oid}")
  public ResponseEntity<ViewerObjectDescription> getObject(
      @PathVariable(value = "oid") Long objectId,
      @RequestParam(value = "valueLength", required = false) Long valueLength,
      @RequestParam(value = "fixedOffset", required = false) Long fixedOffset,
      @RequestParam(value = "fixedLength", required = false) Long fixedLength,
      @RequestParam(value = "variableOffset", required = false) Long variableOffset,
      @RequestParam(value = "variableLength", required = false) Long variableLength,
      @RequestParam(value = "references", required = false) Boolean resolveReferences
  ) {
    var storageObject = adapter.getObject(
        objectId,
        fixedOffset != null ? fixedOffset : 0L,
        fixedLength  != null ? fixedLength : Long.MAX_VALUE,
        variableOffset != null ? variableOffset : 0L,
        variableLength != null ? variableLength : Long.MAX_VALUE,
        valueLength != null ? valueLength : Long.MAX_VALUE,
        resolveReferences != null ? resolveReferences : false
    );
    return ok(storageObject);
  }

  @GetMapping("/maintenance/filesStatistics")
  public ResponseEntity<ViewerStorageFileStatistics> getFileStatistics() {
    var stats = adapter.getStorageFilesStatistics();
    return ok(stats);
  }
}
