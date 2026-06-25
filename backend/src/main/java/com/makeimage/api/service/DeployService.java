package com.makeimage.api.service;

import com.makeimage.api.dto.AdminDtos;
import org.springframework.stereotype.Service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final List<StepDefinition> STEP_DEFINITIONS = List.of(
            new StepDefinition("validate", "校验参数"),
            new StepDefinition("prepare", "准备目录"),
            new StepDefinition("download", "下载 GitHub 包"),
            new StepDefinition("extract", "解压文件"),
            new StepDefinition("detect", "识别发布目录"),
            new StepDefinition("copy", "覆盖部署文件"),
            new StepDefinition("restart", "重启服务"),
            new StepDefinition("cleanup", "清理临时文件")
    );

    private final Map<String, DeployJob> jobs = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public AdminDtos.DeployJobView startDeploy(AdminDtos.DeployRequest request) {
        DeployJob job = new DeployJob(UUID.randomUUID().toString(), request);
        jobs.put(job.id, job);
        CompletableFuture.runAsync(() -> runDeploy(job));
        return job.toView();
    }

    public AdminDtos.DeployJobView job(String id) {
        DeployJob job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("部署任务不存在");
        }
        return job.toView();
    }

    private void runDeploy(DeployJob job) {
        URI uri = null;
        Path targetRoot = null;
        String normalizedTarget = "";
        Path tempDir = null;
        Path archive = null;
        Path extracted = null;
        long downloadedBytes = 0L;
        ZipInspection inspection = null;
        boolean restarted = false;
        String restartMessage = "";

        job.status = "RUNNING";
        job.message = "部署任务已开始";
        job.touch();

        try {
            job.startStep("validate", "正在校验 GitHub 地址和部署目录");
            uri = URI.create(job.request.sourceUrl().trim());
            validateSourceUrl(uri);
            targetRoot = resolveTargetRoot(job.request.targetDir());
            normalizedTarget = targetRoot.toAbsolutePath().normalize().toString();
            job.completeStep("validate", "参数校验通过");

            job.startStep("prepare", "正在创建目标目录和临时目录");
            Files.createDirectories(targetRoot);
            tempDir = Files.createTempDirectory("makeimage-deploy-");
            archive = tempDir.resolve("release.zip");
            extracted = tempDir.resolve("extracted");
            Files.createDirectories(extracted);
            job.completeStep("prepare", "临时目录已准备: " + tempDir);

            job.startStep("download", "正在从 GitHub 下载发布包");
            downloadedBytes = download(uri, archive);
            job.completeStep("download", "下载完成，大小 " + Math.max(1, downloadedBytes / 1024) + " KB");

            job.startStep("extract", "正在解压发布包");
            inspection = unzip(archive, extracted);
            job.completeStep("extract", "解压完成，共 " + inspection.extractedFiles + " 个文件");

            job.startStep("detect", "正在识别可部署目录");
            Path rootToCopy = inspection.releaseRoot.orElse(extracted);
            if (!Files.isDirectory(rootToCopy)) {
                throw new IllegalArgumentException("压缩包内未找到可部署目录");
            }
            job.completeStep("detect", "已识别目录: " + rootToCopy.getFileName());

            job.startStep("copy", "正在覆盖 backend/frontend/deploy/docs");
            List<String> copied = deployReleaseContent(rootToCopy, targetRoot);
            job.completeStep("copy", "已更新: " + String.join(", ", copied));

            if (job.request.restartCommand() != null && !job.request.restartCommand().isBlank()) {
                job.startStep("restart", "正在执行重启命令");
                if (shouldSkipRestart(job.request.restartCommand())) {
                    job.skipStep("restart", buildRestartSkipMessage(job.request.restartCommand()));
                } else {
                    restartMessage = executeRestart(job.request.restartCommand(), normalizedTarget);
                    restarted = restartMessage.startsWith("restart ok");
                    job.completeStep("restart", restartMessage);
                }
            } else {
                job.skipStep("restart", "未填写重启命令");
            }

            job.startStep("cleanup", "正在清理临时文件");
            cleanup(tempDir);
            tempDir = null;
            job.completeStep("cleanup", "临时文件已清理");

            job.result = new AdminDtos.DeployResultView(
                    uri.toString(),
                    normalizedTarget,
                    downloadedBytes,
                    inspection.extractedFiles,
                    inspection.usedNestedRelease,
                    restarted,
                    restartMessage,
                    LocalDateTime.now()
            );
            job.status = "SUCCESS";
            job.currentStep = "";
            job.message = "部署完成";
            job.touch();
        } catch (Exception e) {
            job.failCurrentStep(e.getMessage());
            job.status = "FAILED";
            job.message = e.getMessage();
            job.touch();
            if (tempDir != null) {
                job.startStep("cleanup", "正在清理临时文件");
                cleanup(tempDir);
                job.completeStep("cleanup", "临时文件已清理");
            }
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
        boolean allowed = ALLOWED_TARGETS.stream()
                .map(prefix -> Path.of(prefix).toAbsolutePath().normalize().toString())
                .anyMatch(prefix -> target.toString().startsWith(prefix));
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

    private List<String> deployReleaseContent(Path releaseRoot, Path targetRoot) throws IOException {
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
        return copied;
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
                Files.createDirectories(target.resolve(relative));
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

    private boolean shouldSkipRestart(String restartCommand) {
        if (!isWindows()) {
            return false;
        }
        return splitCommand(restartCommand).stream()
                .map(token -> token.toLowerCase(Locale.ROOT))
                .anyMatch("systemctl"::equals);
    }

    private String buildRestartSkipMessage(String restartCommand) {
        if (isWindows() && splitCommand(restartCommand).stream().anyMatch("systemctl"::equalsIgnoreCase)) {
            return "当前环境为 Windows，已跳过 systemctl 重启";
        }
        return "未执行重启命令";
    }

    private static boolean isWindows() {
        return Optional.ofNullable(System.getProperty("os.name"))
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .contains("win");
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

    private record StepDefinition(String key, String name) {
    }

    private static final class DeployStep {
        private final String key;
        private final String name;
        private String status = "PENDING";
        private String message = "";
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;

        private DeployStep(String key, String name) {
            this.key = key;
            this.name = name;
        }

        private AdminDtos.DeployStepView toView() {
            return new AdminDtos.DeployStepView(key, name, status, message, startedAt, finishedAt);
        }
    }

    private static final class DeployJob {
        private final String id;
        private final AdminDtos.DeployRequest request;
        private final AdminDtos.DeployRuntimeView runtime;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private final Map<String, DeployStep> steps = new LinkedHashMap<>();
        private String status = "PENDING";
        private String currentStep = "";
        private String message = "等待开始";
        private AdminDtos.DeployResultView result;
        private LocalDateTime updatedAt = createdAt;

        private DeployJob(String id, AdminDtos.DeployRequest request) {
            this.id = id;
            this.request = request;
            this.runtime = detectRuntime(request.restartCommand());
            for (StepDefinition definition : STEP_DEFINITIONS) {
                steps.put(definition.key(), new DeployStep(definition.key(), definition.name()));
            }
        }

        private synchronized void startStep(String key, String message) {
            DeployStep step = steps.get(key);
            if (step == null) {
                return;
            }
            step.status = "RUNNING";
            step.message = message;
            step.startedAt = LocalDateTime.now();
            step.finishedAt = null;
            currentStep = key;
            this.message = message;
            touch();
        }

        private synchronized void completeStep(String key, String message) {
            DeployStep step = steps.get(key);
            if (step == null) {
                return;
            }
            step.status = "SUCCESS";
            step.message = message;
            step.finishedAt = LocalDateTime.now();
            this.message = message;
            touch();
        }

        private synchronized void skipStep(String key, String message) {
            DeployStep step = steps.get(key);
            if (step == null) {
                return;
            }
            step.status = "SKIPPED";
            step.message = message;
            step.finishedAt = LocalDateTime.now();
            this.message = message;
            touch();
        }

        private synchronized void failCurrentStep(String message) {
            DeployStep step = steps.get(currentStep);
            if (step == null) {
                return;
            }
            step.status = "FAILED";
            step.message = message == null || message.isBlank() ? "步骤执行失败" : message;
            step.finishedAt = LocalDateTime.now();
            touch();
        }

        private synchronized void touch() {
            updatedAt = LocalDateTime.now();
        }

        private synchronized AdminDtos.DeployJobView toView() {
            List<AdminDtos.DeployStepView> stepViews = steps.values().stream()
                    .map(DeployStep::toView)
                    .toList();
            long finished = stepViews.stream()
                    .filter(step -> List.of("SUCCESS", "SKIPPED", "FAILED").contains(step.status()))
                    .count();
            int progress = (int) Math.round(finished * 100.0 / Math.max(1, stepViews.size()));
            if ("RUNNING".equals(status)) {
                progress = Math.max(5, progress);
            }
            if ("SUCCESS".equals(status)) {
                progress = 100;
            }
            return new AdminDtos.DeployJobView(id, status, currentStep, progress, message, runtime, stepViews, result, createdAt, updatedAt);
        }
    }

    private static AdminDtos.DeployRuntimeView detectRuntime(String restartCommand) {
        String osName = Optional.ofNullable(System.getProperty("os.name")).orElse("unknown");
        if (isWindows()) {
            if (restartCommand != null && restartCommand.toLowerCase(Locale.ROOT).contains("systemctl")) {
                return new AdminDtos.DeployRuntimeView(
                        osName,
                        "WINDOWS",
                        "PowerShell: Restart-Service <服务名>",
                        "当前是 Windows，默认 systemctl 不可用"
                );
            }
            return new AdminDtos.DeployRuntimeView(
                    osName,
                    "WINDOWS",
                    "PowerShell: Restart-Service <服务名>",
                    "当前是 Windows"
            );
        }
        if (restartCommand != null && restartCommand.toLowerCase(Locale.ROOT).contains("docker")) {
            return new AdminDtos.DeployRuntimeView(
                    osName,
                    "DOCKER",
                    "docker restart <容器名>",
                    "当前看起来是 Docker 部署"
            );
        }
        return new AdminDtos.DeployRuntimeView(
                osName,
                "SYSTEMD",
                "sudo systemctl restart aimakeimage",
                "当前看起来是 Linux/systemd 部署"
        );
    }
}
