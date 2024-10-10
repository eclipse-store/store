
# EclipseStore Storage Converter

Main purpose of the EclipseStore Storage converter is providing an option to copy a storage to another storage target.

The converter will not copy redundant older persisted objects. It only copies the objects latest version.
It is possible to define a different channel count for the target than the source storage has.

## Building the storage converter standalone jar

To build the converter standalone jar using Maven you need to active the profile **converter-standalone**.

```console
mvn -Pconverter-standalone clean package
```


## Usage

### standalone jar

To configure the input and output storage an [external configuration](https://docs.eclipsestore.io/manual/storage/configuration/index.html#external-configuration) file for each storage is required.

```console
java -jar storage-embedded-tools-storage-converter-2.0.0.jar sourceCongig.xml targetConfig.xml
```

### StorageConverter.java

Instead of the standalone-jar you may utilize the java class

```
org.eclipse.store.storage.embedded.tools.storage.converter.StorageConverter.StorageConverter(StorageConfiguration, StorageConfiguration)
```

which gives you some more control on the storage's configurations.


