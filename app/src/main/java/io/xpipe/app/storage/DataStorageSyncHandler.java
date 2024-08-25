package io.xpipe.app.storage;

import io.xpipe.core.process.ProcessControlProvider;

import java.nio.file.Path;

public interface DataStorageSyncHandler {

    static DataStorageSyncHandler getInstance() {
        return (DataStorageSyncHandler) ProcessControlProvider.get().getGitStorageHandler();
    }

    boolean supportsSync();

    void init();

    void retrieveSyncedData();

    void afterStorageLoad();

    void beforeStorageSave();

    void afterStorageSave();

    void handleEntry(DataStoreEntry entry, boolean exists, boolean dirty);

    void handleCategory(DataStoreCategory category, boolean exists, boolean dirty);

    void handleDeletion(Path target, String name);
}
