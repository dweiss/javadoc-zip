package com.carrotsearch.spikes;

import javax.tools.DocumentationTool;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class JavadocZip {
  private static final int RETVAL_ERR = 1;
  private static final int RETVAL_SUCCESS = 0;

  public static void main(String[] args) {
    // Get the documentation tool with the default options.
    DocumentationTool jdoc = ToolProvider.getSystemDocumentationTool();
    StandardJavaFileManager fileManager = jdoc.getStandardFileManager(null, null, null);

    // This will generate documentation and return status code.
    Function<List<String>, Integer> generateJavadoc =
        (options) -> {
          DocumentationTool.DocumentationTask task =
              jdoc.getTask(null, fileManager, null, null, options, null);
          // Simplified success/ error status code. Javadoc has more return statuses.
          return task.call() ? RETVAL_SUCCESS : RETVAL_ERR;
        };

    // Preflight options and see if there is any '-d' option. This is very
    // crude but will work for the experiment. Unlike the standard doclet, we'll require
    // an explicit destination.
    List<String> filteredOptions = new ArrayList<>(Arrays.asList(args));
    int index = filteredOptions.indexOf("-d");
    if (index < 0 || index + 1 >= filteredOptions.size()) {
      System.err.println("Destination option (-d) missing.");
      System.exit(RETVAL_ERR);
    }

    String destination = filteredOptions.get(index + 1);
    if (destination.matches("(?i)\\.(zip|jar)$")) {
      // Switch to writing to ZIP file. Remove the -d option (will write to zip filesystem's
      // root) and switch filesystem provider.
      filteredOptions.subList(index, index + 2).clear();

      generateJavadoc = wrapWithZipFilesystem(Paths.get(destination), fileManager, generateJavadoc);
    }

    System.exit(generateJavadoc.apply(filteredOptions));
  }

  private static Function<List<String>, Integer> wrapWithZipFilesystem(
      Path destinationZip,
      StandardJavaFileManager fileManager,
      Function<List<String>, Integer> delegate) {
    return (options) -> {
      try (FileSystem fs =
          FileSystems.newFileSystem(
              URI.create("jar:" + destinationZip.toUri()), Map.of("create", "true"))) {
        Path root = fs.getRootDirectories().iterator().next();
        fileManager.setLocationFromPaths(
            DocumentationTool.Location.DOCUMENTATION_OUTPUT, List.of(root));
        return delegate.apply(options);
      } catch (IOException e) {
        System.err.println("I/O exception while generating documentation: " + e.getMessage());
        return RETVAL_ERR;
      }
    };
  }
}
