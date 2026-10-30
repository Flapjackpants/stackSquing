package com.flapjackpants.stacksquing.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Port of src/paths.cpp. */
public final class MaterialListPaths {
    private MaterialListPaths() {
    }

    private record Entry(Path path, FileTime time) {
    }

    public static boolean isMaterialListName(String fileName) {
        return fileName.startsWith("material_list_") && fileName.endsWith(".txt");
    }

    public static List<Path> listNewestFirst(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                try {
                    Path fileName = p.getFileName();
                    if (fileName == null || !Files.isRegularFile(p)
                            || !isMaterialListName(fileName.toString())) {
                        continue;
                    }
                    entries.add(new Entry(p, Files.getLastModifiedTime(p)));
                } catch (IOException | RuntimeException e) {
                    // skip entries we cannot inspect
                }
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        entries.sort(Comparator.comparing(Entry::time).reversed()
                .thenComparing(e -> e.path().getFileName().toString()));
        List<Path> result = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            result.add(e.path());
        }
        return result;
    }

    public static Optional<Path> newestInDir(Path dir) {
        return listNewestFirst(dir).stream().findFirst();
    }

    public static Path resolve(Path pathOrDir) throws IOException {
        if (Files.isDirectory(pathOrDir)) {
            return newestInDir(pathOrDir).orElseThrow(() ->
                    new IOException("No material_list_*.txt found in directory: " + pathOrDir));
        }
        if (Files.isRegularFile(pathOrDir)) {
            return pathOrDir;
        }
        throw new IOException("Path is not a material list file or directory: " + pathOrDir);
    }
}
