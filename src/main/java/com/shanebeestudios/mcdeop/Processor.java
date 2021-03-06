package com.shanebeestudios.mcdeop;

import com.beust.jcommander.JCommander;
import com.shanebeestudios.mcdeop.app.App;
import com.shanebeestudios.mcdeop.util.Logger;
import com.shanebeestudios.mcdeop.util.Util;
import io.github.lxgaming.reconstruct.Reconstruct;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Processor {

    private final Version version;
    private final boolean decompile;
    private final App app;

    private final Reconstruct reconstruct;
    private File JAR_FILE;
    private File MAPPINGS_FILE;
    private File REMAPPED_JAR;

    private final String MINECRAFT_JAR_NAME;
    private final String MAPPINGS_NAME;
    private final String MAPPED_JAR_NAME;

    private Path DATA_FOLDER_PATH = Paths.get(".", "deobf-work");

    public Processor(Version version, boolean decompile, App app) {
        this.version = version;
        this.app = app;
        if (Util.isRunningMacOS()) {
            // If running on macOS, put the decompile folder in the user's home folder
            // This is mainly due to how the Mac APP works
            DATA_FOLDER_PATH = Paths.get(System.getProperty("user.home"), "McDeob");
        }
        if (Files.notExists(DATA_FOLDER_PATH)) {
            try {
                Files.createDirectory(DATA_FOLDER_PATH);
            } catch (IOException ignore) {}
        }

        MINECRAFT_JAR_NAME = "minecraft_" + version.getType().getName() + "_" + version.getVersion() + ".jar";
        MAPPINGS_NAME = "mappings_" + version.getType().getName() + "_" + version.getVersion() + ".txt";
        MAPPED_JAR_NAME = "remapped_" + version.getType().getName() + "_" + version.getVersion() + ".jar";
        this.decompile = decompile;
        this.reconstruct = new Reconstruct(app);
    }

    public void init() {
        try {
            long start = System.currentTimeMillis();
            downloadJar();
            downloadMappings();
            remapJar();
            if (decompile) {
                decompileJar();
            }
            long finish = System.currentTimeMillis() - start;
            Logger.info("Process finished in " + finish + " milliseconds!");
            if (app != null) {
                app.updateStatusBox("Completed in " + finish + " milliseconds!");
                app.updateButton("Start!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void downloadJar() throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Downloading jar file from Mojang.");
        if (app != null) {
            app.updateStatusBox("Downloading jar");
            app.updateButton("Downloading jar", Color.BLUE);
        }
        JAR_FILE = new File(DATA_FOLDER_PATH.toString(), MINECRAFT_JAR_NAME);
        if (JAR_FILE.exists()) {
            JAR_FILE.delete();
        }
        JAR_FILE.createNewFile();
        URL jar_url = new URL(version.getJar());
        OutputStream jar_out = new BufferedOutputStream(new FileOutputStream(JAR_FILE));
        URLConnection connection = jar_url.openConnection();
        InputStream inputStream = connection.getInputStream();
        byte[] buffer = new byte[1024];

        int numRead;
        while ((numRead = inputStream.read(buffer)) != -1) {
            jar_out.write(buffer, 0, numRead);
        }
        inputStream.close();
        jar_out.close();
        long finish = System.currentTimeMillis() - start;
        Logger.info("Successfully downloaded jar file in " + finish + " milliseconds");
    }

    public void downloadMappings() throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Downloading mappings file from Mojang.");
        if (app != null) {
            app.updateStatusBox("Downloading mappings");
            app.updateButton("Downloading mappings", Color.BLUE);
        }
        MAPPINGS_FILE = new File(DATA_FOLDER_PATH.toString(), MAPPINGS_NAME);
        if (MAPPINGS_FILE.exists()) {
            MAPPINGS_FILE.delete();
        }
        MAPPINGS_FILE.createNewFile();

        URL mapping_url = new URL(version.getMappings());
        OutputStream mapping_out = new BufferedOutputStream(new FileOutputStream(MAPPINGS_FILE));
        URLConnection connection = mapping_url.openConnection();
        InputStream inputStream = connection.getInputStream();
        byte[] buffer = new byte[1024];

        int numRead;
        while ((numRead = inputStream.read(buffer)) != -1) {
            mapping_out.write(buffer, 0, numRead);
        }
        inputStream.close();
        mapping_out.close();
        long finish = System.currentTimeMillis() - start;
        Logger.info("Successfully downloaded mappings file in " + finish + " milliseconds");
    }

    public void remapJar() {
        long start = System.currentTimeMillis();
        if (app != null) {
            app.updateStatusBox("Remapping...");
            app.updateButton("Remapping...", Color.BLUE);
        }
        REMAPPED_JAR = new File(DATA_FOLDER_PATH.toString(), MAPPED_JAR_NAME);

        if (!REMAPPED_JAR.exists()) {
            Logger.info("Remapping " + MINECRAFT_JAR_NAME + " file...");
            String[] clientArgs = new String[]{"-jar", JAR_FILE.getAbsolutePath(), "-mapping", MAPPINGS_FILE.getAbsolutePath(), "-output", REMAPPED_JAR.getAbsolutePath(), "-agree"};
            String[] serverArgs = new String[]{"-jar", JAR_FILE.getAbsolutePath(), "-mapping", MAPPINGS_FILE.getAbsolutePath(), "-output", REMAPPED_JAR.getAbsolutePath(),
                    "-exclude", "com.google.,io.netty.,it.unimi.dsi.fastutil.,javax.,joptsimple.,org.apache.", "-agree"};
            try {
                JCommander.newBuilder()
                        .addObject(reconstruct.getArguments())
                        .build()
                        .parse(version.getType() == Version.Type.SERVER ? serverArgs : clientArgs);
            } catch (Exception ex) {
                reconstruct.getLogger().error("Encountered an error while parsing arguments", ex);
                if (app != null) {
                    app.updateStatusBox("fail");
                }
                Runtime.getRuntime().exit(-1);
                return;
            }
            reconstruct.load();
            long finish = System.currentTimeMillis() - start;
            Logger.info("Remapping completed in " + finish + " milliseconds");
        } else {
            Logger.info(MAPPED_JAR_NAME + " already remapped... skipping mapping!");
        }
    }

    public void decompileJar() {
        long start = System.currentTimeMillis();
        Logger.info("Decompiling final jar file.");
        if (app != null) {
            app.updateStatusBox("Decompiling... This will take a while!");
            app.updateButton("Decompiling...", Color.BLUE);
        }
        File DIR = new File(DATA_FOLDER_PATH.toString(), "final-decompile");
        if (!DIR.exists()) {
            DIR.mkdirs();
        }
        // Setup FernFlower to properly decompile the jar file
        String[] args = new String[] {"-dgs=1", "-hdc=0", "-rbr=0", "-asc=1", "-udv=0", REMAPPED_JAR.getAbsolutePath(), DIR.getAbsolutePath()};
        ConsoleDecompiler.main(args);
        long finish = System.currentTimeMillis() - start;
        Logger.info("Decompiling completed in " + finish + " milliseconds");
    }

}
