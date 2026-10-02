package book.example.services;

import book.example.dto.DiagramImage;
import book.example.dto.DiagramSpecification;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class MermaidCliRenderer implements DiagramRenderer {

    private final MermaidRenderProperties properties;

    public MermaidCliRenderer(
            MermaidRenderProperties properties) {

        this.properties = properties;
    }

    @Override
    public DiagramImage render(
            DiagramSpecification specification,
            String mermaidSource) {

        if (specification == null) {
            throw new IllegalArgumentException(
                    "Diagram specification is required"
            );
        }

        if (mermaidSource == null ||
                mermaidSource.isBlank()) {

            throw new IllegalArgumentException(
                    "Mermaid source is required"
            );
        }

        Path tempDirectory = null;

        try {

            // =================================================
            // TEMP DIRECTORY
            // =================================================

            tempDirectory =
                    Files.createTempDirectory(
                            "documentation-mermaid-"
                    );

            Path mermaidFile =
                    tempDirectory.resolve(
                            "diagram.mmd"
                    );

            /*
             * IMPORTANT:
             *
             * We generate PNG, NOT SVG.
             *
             * Apache POI can directly insert PNG
             * into DOCX using XWPFRun.addPicture().
             */
            Path outputFile =
                    tempDirectory.resolve(
                            "diagram.png"
                    );

            Files.writeString(
                    mermaidFile,
                    mermaidSource,
                    StandardCharsets.UTF_8
            );

            // =================================================
            // MERMAID CLI COMMAND
            // =================================================

            String command =
                    properties.getCommand();

            if (command == null ||
                    command.isBlank()) {

                command = "mmdc";
            }

            command = resolveCommand(command);
            List<String> processCommand = new ArrayList<>();
            if (isWindowsCommandScript(command)) {
                String commandInterpreter = System.getenv("ComSpec");
                processCommand.add(commandInterpreter == null || commandInterpreter.isBlank()
                        ? "cmd.exe"
                        : commandInterpreter);
                processCommand.add("/c");
            }
            processCommand.add(command);
            processCommand.add("-i");
            processCommand.add(mermaidFile.toAbsolutePath().toString());
            processCommand.add("-o");
            processCommand.add(outputFile.toAbsolutePath().toString());
            processCommand.add("-b");
            processCommand.add("white");

            ProcessBuilder processBuilder = new ProcessBuilder(processCommand);
            Path processLog = tempDirectory.resolve("mermaid-process.log");
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(processLog.toFile());

            Process process = processBuilder.start();

            /*
             * Wait before reading output. Redirecting the child output to a file
             * prevents a full stdout pipe from deadlocking the timeout mechanism.
             */
            boolean finished =
                    process.waitFor(
                            120,
                            TimeUnit.SECONDS
                    );
            String processOutput = Files.exists(processLog)
                    ? Files.readString(processLog, StandardCharsets.UTF_8)
                    : "";

            if (!finished) {

                process.destroyForcibly();

                throw new IllegalStateException(
                        "Mermaid CLI timed out after 120 seconds"
                );
            }

            int exitCode =
                    process.exitValue();

            if (exitCode != 0) {

                throw new IllegalStateException(
                        "Mermaid CLI failed. "
                                + "Exit code: "
                                + exitCode
                                + ". Output: "
                                + processOutput
                );
            }

            // =================================================
            // VERIFY PNG
            // =================================================

            if (!Files.exists(outputFile)) {

                throw new IllegalStateException(
                        "Mermaid CLI completed successfully "
                                + "but PNG file was not created"
                );
            }

            byte[] imageData =
                    Files.readAllBytes(
                            outputFile
                    );

            if (imageData.length == 0) {

                throw new IllegalStateException(
                        "Generated Mermaid PNG is empty"
                );
            }

            // =================================================
            // CREATE DIAGRAM IMAGE
            // =================================================

            DiagramImage image =
                    new DiagramImage();

            image.setTitle(
                    specification.getTitle()
            );

            /*
             * IMPORTANT:
             * DocumentAssembler will now treat this
             * as XWPFDocument.PICTURE_TYPE_PNG.
             */
            image.setFormat("png");

            image.setData(
                    imageData
            );

            return image;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to render Mermaid diagram",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Mermaid rendering was interrupted",
                    e
            );

        } finally {

            // =================================================
            // CLEAN TEMP FILES
            // =================================================

            deleteTempDirectory(
                    tempDirectory
            );
        }
    }

    private void deleteTempDirectory(
            Path directory) {

        if (directory == null) {
            return;
        }

        try {

            if (!Files.exists(directory)) {
                return;
            }

            Files.walk(directory)
                    .sorted(
                            (a, b) ->
                                    b.compareTo(a)
                    )
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(
                                            path
                                    );
                                } catch (IOException ignored) {
                                    // Best-effort cleanup.
                                }
                            }
                    );

        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

        private String resolveCommand(String command) {
                if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ||
                                command.contains("\\") || command.contains("/")) {
                        return command;
                }

                String lowerCommand = command.toLowerCase(Locale.ROOT);
                if (lowerCommand.endsWith(".cmd") || lowerCommand.endsWith(".bat")) {
                        return command;
                }

                String appData = System.getenv("APPDATA");
                if (appData == null || appData.isBlank()) {
                        return command;
                }

                Path npmWrapper = Path.of(appData, "npm", command + ".cmd");
                return Files.isRegularFile(npmWrapper) ? npmWrapper.toString() : command;
        }

        private boolean isWindowsCommandScript(String command) {
                if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                        return false;
                }

                String lowerCommand = command.toLowerCase(Locale.ROOT);
                return lowerCommand.endsWith(".cmd") || lowerCommand.endsWith(".bat");
        }
}