package org.eclipse.store.gigamap.restart.update;

import static org.eclipse.store.gigamap.restart.AllTypesPojoIndices.uuidFieldIndex;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UuidUpdateTest
{

	@RepeatedTest(100)
	@Disabled("https://github.com/microstream-one/internal/issues/38")
	void uuidEntityUpdate()
	{
		Path dataDirectory = Path.of("target", "aaa_uuidUpdatetest");
		int size = 10;
		final UuidUpdate uuidUpdate = new UuidUpdate(UUID.randomUUID());
		UUID newUuid = UUID.randomUUID();
		GigaMap<UuidUpdate> gigaMap = GigaMap.New();

		gigaMap.index().bitmap().add(uuidFieldIndex);
		gigaMap.index().bitmap().setIdentityIndices(uuidFieldIndex);

		for (int i = 0; i < size - 1; i++)
		{
			gigaMap.add(new UuidUpdate(UUID.randomUUID()));
		}


		try (EmbeddedStorageManager manager = EmbeddedStorage.start(dataDirectory)) {
			if (manager.root() == null) {
				manager.setRoot(gigaMap);
				manager.storeRoot();
			} else {
				gigaMap = (GigaMap<UuidUpdate>) manager.root();
			}

			//get random entity
			gigaMap.get(ThreadLocalRandom.current().nextInt(0, size));
			UuidUpdate uuidUpdate1 = gigaMap.get(0);
			gigaMap.update(uuidUpdate1, u -> u.setUuid(java.util.UUID.randomUUID()));
			manager.storeAll(gigaMap,uuidUpdate1);
		}
	}

	private static final BinaryIndexerUUID<UuidUpdate> uuidFieldIndex = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(UuidUpdate entity)
		{
			return entity.getUuid();
		}

		public String name()
		{
			return "uuidField";
		}
	};

	private class UuidUpdate
	{
		UUID uuid;

		public UuidUpdate(UUID uuid)
		{
			this.uuid = uuid;
		}

		public UUID getUuid()
		{
			return uuid;
		}

		public void setUuid(UUID uuid)
		{
			this.uuid = uuid;
		}
	}
}
