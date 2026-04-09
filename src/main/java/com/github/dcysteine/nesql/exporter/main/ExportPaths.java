package com.github.dcysteine.nesql.exporter.main;

import cpw.mods.fml.relauncher.FMLInjectionData;

import java.io.File;

/**
 * Shared repository path contract for NESQL export profiles.
 */
public final class ExportPaths {
    private static final String REPOSITORY_PATH_FORMAT_STRING = "nesql" + File.separator + "%s";
    private static final String DATABASE_FILE_PATH = "nesql-db";
    private static final String IMAGE_DIRECTORY_PATH = "image";

    public final String repositoryName;
    public final File repositoryDirectory;
    public final File databaseFile;
    public final File imageDirectory;

    private ExportPaths(String repositoryName) {
        this.repositoryName = repositoryName;
        this.repositoryDirectory =
                new File(
                        (File) FMLInjectionData.data()[6],
                        String.format(REPOSITORY_PATH_FORMAT_STRING, repositoryName));
        this.databaseFile = new File(repositoryDirectory, DATABASE_FILE_PATH);
        this.imageDirectory = new File(repositoryDirectory, IMAGE_DIRECTORY_PATH);
    }

    public static ExportPaths forRepository(String repositoryName) {
        return new ExportPaths(repositoryName);
    }
}
