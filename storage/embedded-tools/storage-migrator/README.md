# Storage Migrator

With this utility you can easily migrate MicroStream projects to EclipseStore.

It converts the codebase as well as type dictionary files of existing storages.
The only requirement is to copy the type dictionary file into the project for the migration process.
Type dictionary files are named 'PersistenceTypeDictionary.ptd' by default and can be found in the root of your storage folder.

Simply execute this Maven command in your MicroStream project folder. Just make sure to backup your code in case of any occurring errors.

Change or omit the `eclipseStoreVersion` and `typeDictionaryRelativePath` parameters accordingly.

The codebase migration is only done when `eclipseStoreVersion` is set.
The type dictionary migration is only done when `typeDictionaryRelativePath` is set.

### Migration of both, source code and type dictionary:

````
mvn org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.eclipse.store.storage.embedded.tools.storage.migrator.ConvertProject -Drewrite.recipeArtifactCoordinates=org.eclipse.store:storage-embedded-tools-storage-migrator:2.0.0 -DeclipseStoreVersion=2.0.0 -Drewrite.plainTextMasks=**/*.ptd  -DtypeDictionaryRelativeFilePath=src/main/resources/PersistenceTypeDictionary.ptd
````

### Migration of source code only:

````
mvn org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.eclipse.store.storage.embedded.tools.storage.migrator.ConvertProject -Drewrite.recipeArtifactCoordinates=org.eclipse.store:storage-embedded-tools-storage-migrator:2.0.0 -DeclipseStoreVersion=2.0.0
````

### Migration of type dictionary only:

````
mvn org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.eclipse.store.storage.embedded.tools.storage.migrator.ConvertProject -Drewrite.recipeArtifactCoordinates=org.eclipse.store:storage-embedded-tools-storage-migrator:2.0.0 -Drewrite.plainTextMasks=**/*.ptd  -DtypeDictionaryRelativeFilePath=src/main/resources/PersistenceTypeDictionary.ptd
````
