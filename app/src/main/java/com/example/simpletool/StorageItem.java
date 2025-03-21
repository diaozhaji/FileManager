package com.example.simpletool;

import java.io.File;

public class StorageItem extends File {
    private final String displayName;
    private final boolean isRoot;

    public StorageItem(File path, String name, boolean isRoot) {
        super(path.getAbsolutePath());
        this.displayName = name;
        this.isRoot = isRoot;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRoot() {
        return isRoot;
    }
}