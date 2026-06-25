package com.makeimage.api.service;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

@Service
public class ThumbnailService {
    private static final int MAX_WIDTH = 640;
    private static final int MAX_HEIGHT = 640;

    private final StorageService storageService;

    public ThumbnailService(StorageService storageService) {
        this.storageService = storageService;
    }

    public void ensureThumbnail(String filename) throws IOException {
        Path generated = storageService.generatedPath(filename);
        if (!Files.exists(generated)) {
            return;
        }
        Path thumbnail = storageService.thumbnailPath(filename);
        if (Files.exists(thumbnail) && Files.size(thumbnail) > 0) {
            return;
        }
        createThumbnail(generated, thumbnail);
    }

    public void ensureThumbnailFromThumbnailName(String thumbnailFilename) throws IOException {
        String sourceName = sourceFilenameFromThumbnail(thumbnailFilename);
        if (sourceName == null) {
            return;
        }
        ensureThumbnail(sourceName);
    }

    public int generateMissingThumbnails() throws IOException {
        Path generatedDir = storageService.generatedPath(".").getParent();
        if (generatedDir == null || !Files.exists(generatedDir)) {
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(generatedDir)) {
            for (Path source : stream) {
                if (!Files.isRegularFile(source)) {
                    continue;
                }
                String filename = source.getFileName().toString();
                Path thumbnail = storageService.thumbnailPath(filename);
                if (Files.exists(thumbnail) && Files.size(thumbnail) > 0) {
                    continue;
                }
                createThumbnail(source, thumbnail);
                if (Files.exists(thumbnail) && Files.size(thumbnail) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private void createThumbnail(Path source, Path target) throws IOException {
        BufferedImage original;
        try (InputStream input = Files.newInputStream(source)) {
            original = ImageIO.read(input);
        }
        if (original == null) {
            return;
        }

        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height);
        scale = Math.min(scale, 1.0);
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newWidth, newHeight);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(Files.newOutputStream(target))) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.78f);
            }
            writer.write(null, new IIOImage(scaled, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private String replaceExtension(String filename, String newExt) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) + newExt : filename + newExt;
    }

    private String sourceFilenameFromThumbnail(String thumbnailFilename) throws IOException {
        int dot = thumbnailFilename.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String stem = thumbnailFilename.substring(0, dot);
        Path generatedDir = storageService.generatedPath(".").getParent();
        if (generatedDir == null || !Files.exists(generatedDir)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(generatedDir, stem + ".*")) {
            for (Path source : stream) {
                if (Files.isRegularFile(source)) {
                    return source.getFileName().toString();
                }
            }
        }
        return null;
    }
}
