# Storage Migrator

With this utility you can easily migrate MicroStream projects to EclipseStore.

It converts the codebase as well as type dictionary files of existing storages.
The only requirement is to copy the type dictionary file into the project for the migration process.

Simply execute this Maven command in your MicroStream project folder. Just make sure to backup your code in case of any occurring errors.

Change or omit the `eclipseStoreVersion` and `typeDictionaryRelativePath` parameters accordingly.

The codebase migration is only done when `eclipseStoreVersion` is set.
The type dictionary migration is only done when `typeDictionaryRelativePath` is set.

````
mvn org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.eclipse.store.storage.embedded.tools.storage.migrator.ConvertProject -Drewrite.recipeArtifactCoordinates=org.eclipse.store:storage-embedded-tools-storage-migrator:1.0.0-SNAPSHOT -Drewrite.plainTextMasks=**/*.ptd -DeclipseStoreVersion=1.0.0-SNAPSHOT -DtypeDictionaryRelativeFilePath=src/main/resources/PersistenceTypeDictionary.ptd
````
