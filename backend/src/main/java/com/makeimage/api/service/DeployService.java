package com.makeimage.api.service;

import com.makeimage.api.dto.AdminDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DeployService {
    private static final List<String> ALLOWED_TARGETS = List.of(
            "/opt/aimakeimage",
            "/www/aimakeimage"
    );
    private static final List<String> DEPLOY_PATHS = List.of(
            "backend",
            "frontend",
            "deploy",
            "docs"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DeployService() {
    }

    @Transactional
    public AdminDtos.DeployResultView deployFromGitHub(AdminDtos.DeployRequest request) throws IOException, InterruptedException {
        URI uri = URI.create(request.sourceUrl().trim());
        validateSourceUrl(uri);

        Path targetRoot = resolveTargetRoot(request.targetDir());
        Files.createDirectories(targetRoot);

        String normalizedTarget = targetRoot.toAbsolutePath().normalize().toString();
        Path tempDir = Files.createTempDirectory("makeimage-deploy-");
        Path archive = tempDir.resolve("release.zip");
        Path extracted = tempDir.resolve("extracted");
        Files.createDirectories(extracted);

        try {
            long downloadedBytes = download(uri, archive);
            ZipInspection inspection = unzip(archive, extracted);
            Path rootToCopy = inspection.releaseRoot.orElse(extracted);

            if (!Files.isDirectory(rootToCopy)) {
                throw new IllegalArgumentException("压缩包内未找到可部署目录");
            }

            deployReleaseContent(rootToCopy, targetRoot);
            boolean restarted = false;
            String restartMessage = "";
            if (request.restartCommand() != null && !request.restartCommand().isBlank()) {
                restartMessage = executeRestart(request.restartCommand(), normalizedTarget);
                restarted = true;
            }

            return new AdminDtos.DeployResultView(
                    uri.toString(),
                    normalizedTarget,
                    downloadedBytes,
                    inspection.extractedFiles,
                    inspection.usedNestedRelease,
                    restarted,
                    restartMessage,
                    LocalDateTime.now()
            );
        } finally {
            cleanup(tempDir);
        }
    }

    private void validateSourceUrl(URI uri) {
        String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme))) {
            throw new IllegalArgumentException("只支持 http/https 下载地址");
        }
        String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
        if (!"github.com".equals(host) && !"raw.githubusercontent.com".equals(host) && !host.endsWith(".githubusercontent.com")) {
            throw new IllegalArgumentException("只允许从 GitHub 下载资源");
        }
    }

    private Path resolveTargetRoot(String targetDir) {
        String candidate = Optional.ofNullable(targetDir).orElse("").trim();
        if (candidate.isBlank()) {
            candidate = "/opt/aimakeimage";
        }
        Path target = Path.of(candidate).toAbsolutePath().normalize();
        boolean allowed = ALLOWED_TARGETS.stream().anyMatch(prefix -> target.toString().startsWith(Path.of(prefix).toAbsolutePath().normalize().toString()));
        if (!allowed) {
            throw new IllegalArgumentException("目标目录不在允许范围内");
        }
        return target;
    }

    private long download(URI uri, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("下载失败: HTTP " + response.statusCode());
        }
        try (InputStream inputStream = new BufferedInputStream(response.body())) {
            return Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ZipInspection unzip(Path zipFile, Path destDir) throws IOException {
        int extractedFiles = 0;
        List<Path> topLevelDirs = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path resolved = destDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(destDir)) {
                    throw new IllegalArgumentException("压缩包包含非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zipInputStream, resolved, StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles++;
                    if (resolved.getNameCount() >= 2) {
                        topLevelDirs.add(destDir.resolve(resolved.getName(0)));
                    }
                }
            }
        }
        Optional<Path> releaseRoot = findReleaseRoot(destDir);
        return new ZipInspection(extractedFiles, releaseRoot, releaseRoot.isPresent());
    }

    private Optional<Path> findReleaseRoot(Path destDir) throws IOException {
        if (hasDeployMarkers(destDir)) {
            return Optional.of(destDir);
        }
        try (var stream = Files.walk(destDir, 4)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(this::hasDeployMarkersQuietly)
                    .min(Comparator.comparingInt(Path::getNameCount));
        }
    }

    private void deployReleaseContent(Path releaseRoot, Path targetRoot) throws IOException {
        List<String> copied = new ArrayList<>();
        for (String relative : DEPLOY_PATHS) {
            Path source = releaseRoot.resolve(relative).normalize();
            if (!Files.exists(source)) {
                continue;
            }
            Path target = targetRoot.resolve(relative).normalize();
            if (!target.startsWith(targetRoot)) {
                throw new IllegalArgumentException("目标路径非法: " + relative);
            }
            deletePath(target);
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            copied.add(relative);
        }
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("压缩包内未找到 backend/frontend/deploy/docs 目录");
        }
    }

    private boolean hasDeployMarkersQuietly(Path dir) {
        try {
            return hasDeployMarkers(dir);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasDeployMarkers(Path dir) {
        return DEPLOY_PATHS.stream().anyMatch(name -> Files.exists(dir.resolve(name)));
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path currentTarget = target.resolve(relative);
                Files.createDirectories(currentTarget);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deletePath(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(path)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path item : paths) {
            Files.deleteIfExists(item);
        }
    }

    private String executeRestart(String restartCommand, String workingDir) {
        List<String> command = splitCommand(restartCommand);
        if (command.isEmpty()) {
            return "";
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(Path.of(workingDir).toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int code = process.waitFor();
            return (code == 0 ? "restart ok" : "restart failed(" + code + ")") + (output.isBlank() ? "" : ": " + trimOutput(output));
        } catch (Exception e) {
            return "restart failed: " + e.getMessage();
        }
    }

    private List<String> splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char ch : command.toCharArray()) {
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private String trimOutput(String output) {
        String normalized = output.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }

    private void cleanup(Path tempDir) {
        try {
            deletePath(tempDir);
        } catch (IOException ignored) {
        }
    }

    private record ZipInspection(int extractedFiles, Optional<Path> releaseRoot, boolean usedNestedRelease) {
    }
}
