package net.kragnoth.abyssal.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Where the save lives. The desktop build writes JSON next to the user's home; tests use {@link #memory()}. */
public interface SaveStore {
    SaveData load();
    void store(SaveData data);

    static SaveStore memory() {
        return new SaveStore() {
            private SaveData data = new SaveData();
            public SaveData load() { return data; }
            public void store(SaveData d) { data = d; }
        };
    }

    /** JSON file store, written atomically (temp file + move) so a crash never corrupts the save. */
    static SaveStore file(Path path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return new SaveStore() {
            public SaveData load() {
                try {
                    if (Files.exists(path)) {
                        SaveData d = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), SaveData.class);
                        if (d != null) return d.normalised();
                    }
                } catch (Exception e) {
                    System.err.println("[abyssal] could not read save " + path + ": " + e);
                }
                return new SaveData();
            }
            public void store(SaveData d) {
                try {
                    Files.createDirectories(path.getParent());
                    Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
                    Files.writeString(tmp, gson.toJson(d), StandardCharsets.UTF_8);
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    System.err.println("[abyssal] could not write save " + path + ": " + e);
                }
            }
        };
    }
}
