import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniBankChecks {
    private record Run(String id, int number, String requirements, String input, String expected) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Arguments: class-directory result-directory scenario-file source-file");
        }
        Path classes = Path.of(args[0]).toAbsolutePath();
        Path results = Path.of(args[1]);
        Path scenarioFile = Path.of(args[2]);
        Path sourceFile = Path.of(args[3]);
        if (Files.exists(results)) {
            throw new IllegalArgumentException("Result directory already exists: " + results);
        }
        String suite = Files.readString(scenarioFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String sourceHash = sha256(sourceFile);
        String suiteHash = sha256(scenarioFile);
        List<Run> runs = readRuns(suite);
        Files.createDirectories(results);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        List<String> command = List.of(java.toString(), "-Dfile.encoding=UTF-8", "-cp", classes.toString(), "MiniBank");
        StringBuilder summary = new StringBuilder("# Фактические результаты проверок\n\n");
        summary.append("Дата ").append(OffsetDateTime.now()).append("\n\n");
        summary.append("Java ").append(System.getProperty("java.version")).append("\n\n");
        summary.append("Исходник ").append(sourceFile).append("\n\nSHA256 ").append(sourceHash).append("\n\n");
        summary.append("Набор ").append(scenarioFile).append("\n\nSHA256 ").append(suiteHash).append("\n\n");
        summary.append("| Сценарий | Запуск | Требования | Статус | Код процесса | Протокол |\n");
        summary.append("|---|---|---|---|---|---|\n");
        int failures = 0;
        for (Run run : runs) {
            Path folder = results.resolve(run.id() + "-" + run.number());
            Files.createDirectory(folder);
            Path input = folder.resolve("stdin.txt");
            Path expected = folder.resolve("expected.txt");
            Path stdout = folder.resolve("stdout.txt");
            Path stderr = folder.resolve("stderr.txt");
            Files.writeString(input, run.input(), StandardCharsets.UTF_8);
            Files.writeString(expected, run.expected(), StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("command.txt"), String.join("\n", command) + "\n", StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(command)
                    .redirectInput(input.toFile())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly().waitFor();
            }
            String actual = Files.readString(stdout, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String error = Files.readString(stderr, StandardCharsets.UTF_8);
            boolean passed = completed && actual.equals(run.expected()) && error.isEmpty();
            String status = passed ? "пройдено" : "не пройдено";
            int exitCode = process.exitValue();
            if (!passed) {
                failures++;
            }
            String record = "# " + run.id() + " запуск " + run.number() + "\n\n"
                    + "Дата " + OffsetDateTime.now() + "\n\n"
                    + "Требования " + run.requirements() + "\n\n"
                    + "Исходник " + sourceFile + "\n\nSHA256 " + sourceHash + "\n\n"
                    + "Набор " + scenarioFile + "\n\nSHA256 " + suiteHash + "\n\n"
                    + "Действие — отдельный процесс Java 17. Аргументы команды — command.txt.\n\n"
                    + "Точный вход — stdin.txt. Ожидаемый stdout — expected.txt. Ожидаемый stderr пуст.\n\n"
                    + "Фактические результаты — stdout.txt и stderr.txt.\n\n"
                    + "Фактический код процесса " + exitCode + " (не нормируется требованиями).\n\n"
                    + "Завершён в пределах 10 секунд — " + completed + ".\n\n"
                    + "Статус — " + status + ".\n";
            Files.writeString(folder.resolve("record.md"), record, StandardCharsets.UTF_8);
            summary.append("| ").append(run.id()).append(" | ").append(run.number()).append(" | ")
                    .append(run.requirements()).append(" | ").append(status).append(" | ")
                    .append(exitCode).append(" | ").append(folder.toString().replace('\\', '/'))
                    .append("/record.md |\n");
            System.out.println(run.id() + "." + run.number() + " " + (passed ? "PASS" : "FAIL"));
        }
        summary.append("\nВсего запусков ").append(runs.size()).append(". Не пройдено ").append(failures).append(".\n");
        summary.append("\nСравнение учитывает все символы и завершающие переводы строк. CRLF приводится к LF.\n");
        Files.writeString(results.resolve("results.md"), summary, StandardCharsets.UTF_8);
        System.out.println("Runs: " + runs.size() + ", failures: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static List<Run> readRuns(String suite) {
        Pattern cases = Pattern.compile("^### (T\\d{2}) ([^\\n]+)\\n(.*?)(?=^### T\\d{2} |^## |\\z)", Pattern.MULTILINE | Pattern.DOTALL);
        Pattern blocks = Pattern.compile("^```text\\n(.*?)^```[ \\t]*$", Pattern.MULTILINE | Pattern.DOTALL);
        Pattern requirements = Pattern.compile("^- Требования: (.+)$", Pattern.MULTILINE);
        Matcher caseMatcher = cases.matcher(suite);
        List<Run> runs = new ArrayList<>();
        while (caseMatcher.find()) {
            String body = caseMatcher.group(3);
            Matcher references = requirements.matcher(body);
            if (!references.find()) {
                throw new IllegalArgumentException("Requirements absent: " + caseMatcher.group(1));
            }
            Matcher blockMatcher = blocks.matcher(body);
            List<String> contents = new ArrayList<>();
            while (blockMatcher.find()) {
                contents.add(blockMatcher.group(1));
            }
            if (contents.isEmpty() || contents.size() % 2 != 0) {
                throw new IllegalArgumentException("Invalid input/output pairs: " + caseMatcher.group(1));
            }
            for (int i = 0; i < contents.size(); i += 2) {
                runs.add(new Run(caseMatcher.group(1), i / 2 + 1, references.group(1), contents.get(i), contents.get(i + 1)));
            }
        }
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("No scenario runs found");
        }
        return runs;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
