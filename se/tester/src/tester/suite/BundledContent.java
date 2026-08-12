package tester.suite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Unpacks a content set bundled into the tester jar by {@code se/tester/build.gradle.kts} so a suite can compile it. */
final class BundledContent {

    private BundledContent() {
    }

    /** Extract the set rooted at {@code bundleRoot} ({@code <root>/index.txt} lists the files) to a temp dir. */
    static Path extract(String bundleRoot) throws IOException {
        Path root = Files.createTempDirectory("se-bundled-" + bundleRoot).resolve("content");
        ClassLoader loader = BundledContent.class.getClassLoader();
        try (InputStream index = loader.getResourceAsStream(bundleRoot + "/index.txt")) {
            if (index == null) {
                throw new IOException(bundleRoot + "/index.txt is not bundled in the tester jar");
            }
            List<String> paths = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))
                    .lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            for (String relative : paths) {
                Path target = root.resolve(relative);
                Files.createDirectories(target.getParent());
                String resource = bundleRoot.equals("content")
                        ? "content/" + relative                 // the default catalog bundles flat
                        : bundleRoot + "/content/" + relative;  // packs bundle under <root>/content/
                try (InputStream file = loader.getResourceAsStream(resource)) {
                    if (file == null) {
                        throw new IOException("missing bundled resource " + resource);
                    }
                    Files.copy(file, target);
                }
            }
        }
        return root;
    }
}
