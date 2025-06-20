package dev.tonimatas.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public abstract class JsonFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonFile.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static <T extends JsonFile> T load(Class<T> clazz, String path) {
        try (FileReader reader = new FileReader(path)) {
            return gson.fromJson(reader, clazz);
        } catch (IOException e) {
            LOGGER.error("Error loading {} config file.", path);
            throw new RuntimeException(e);
        }
    }

    public static <T extends JsonFile> T loadOrCreate(Class<T> clazz, String path) {
        File file = new File(path);

        if (!file.exists()) {
            if (path.split("/").length > 1) {
                boolean folderCreated = Paths.get(path).getParent().toFile().mkdirs();

                if (!folderCreated) {
                    LOGGER.debug("Error creating {} folder.", path);
                }
            }

            try {
                T instance = clazz.getDeclaredConstructor().newInstance();
                instance.save();
                return instance;
            } catch (Exception e) {
                LOGGER.error("Error creating {} config file.", path);
                throw new RuntimeException(e);
            }
        }

        return load(clazz, path);
    }

    protected abstract String getFilePath();

    public void save() {
        try (FileWriter writer = new FileWriter(getFilePath())) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("Error saving {} config file.", getFilePath());
            throw new RuntimeException(e);
        }
    }
}
