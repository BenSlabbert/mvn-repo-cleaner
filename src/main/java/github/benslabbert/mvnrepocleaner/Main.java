/* Licensed under Apache-2.0 2025. */
package github.benslabbert.mvnrepocleaner;

import com.vdurmont.semver4j.Semver;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("must provide exactly one argument");
      System.exit(1);
    }

    Path path = Paths.get(args[0]);
    if (!path.isAbsolute()) {
      System.err.println("must provide absolute path");
      System.exit(1);
    }

    if (!Files.isDirectory(path)) {
      System.err.println("must be a directory");
      System.exit(1);
    }

    Map<Path, Set<Semver>> map = new HashMap<>();
    try (Stream<Path> stream = Files.walk(path)) {
      stream.forEach(p -> handleFile(p, map));
    }

    for (Map.Entry<Path, Set<Semver>> entry : map.entrySet()) {
      Path key = entry.getKey();
      Set<Semver> semvers = entry.getValue();
      List<Semver> sortedVersions = semvers.stream().sorted(Semver::compareTo).toList().reversed();
      System.err.printf("key: %s versions: %s%n", key, sortedVersions);

      if (3 < sortedVersions.size()) {
        System.err.printf(
            "dir %s has 3 or more versions, removing the oldest and keeping the last 2", key);
        List<Semver> versionsToDelete = sortedVersions.stream().skip(2).toList();
        for (Semver semver : versionsToDelete) {
          Path resolve = key.resolve(semver.toString());
          System.err.println("deleting " + resolve);
          deleteAllFilesInDirectory(resolve);
          Files.delete(resolve);
        }
      }
    }
  }

  private static void handleFile(Path file, Map<Path, Set<Semver>> map) {
    System.err.println("file: " + file);

    if (Files.isRegularFile(file)) {
      System.err.println("skipping regular file");
      return;
    }

    String dirName = file.getFileName().toString();
    Path parent = file.getParent();
    System.err.println("parent: " + parent);
    System.err.println("dir: " + dirName);
    try {
      Semver semver = new Semver(dirName, Semver.SemverType.STRICT);
      if (dirName.contains("SNAPSHOT")) {
        System.err.println("delete SNAPSHOT version");
        deleteAllFilesInDirectory(file);
        Files.delete(file);
        return;
      }

      // for each hard version we store the path and version for later post-processing
      map.computeIfAbsent(parent, _ -> new HashSet<>()).add(semver);
    } catch (Exception e) {
      System.err.println("unable to parse version for dir: %s skipping".formatted(dirName));
    }
  }

  private static void deleteAllFilesInDirectory(Path file) throws IOException {
    Files.walkFileTree(
        file,
        new FileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (attrs.isRegularFile()) {
              Files.delete(file);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
