package com.example.s1000dviewer.render;

import com.example.s1000dviewer.adapters.fs.FsDataRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DefaultRenderCache implements RenderCache {

    private final ConcurrentHashMap<String, RenderedDm> memoryCache = new ConcurrentHashMap<>();
    private final Path cacheRoot;

    public DefaultRenderCache(FsDataRepository repository) {
        this.cacheRoot = repository.cacheDir();
        try {
            Files.createDirectories(cacheRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize render cache", ex);
        }
    }

    @Override
    public Optional<RenderedDm> get(String key) {
        return Optional.ofNullable(memoryCache.get(key));
    }

    @Override
    public void put(String key, RenderedDm renderedDm) {
        memoryCache.put(key, renderedDm);
        Path cacheFile = cacheRoot.resolve(key + ".html").normalize();
        try {
            Files.writeString(cacheFile, renderedDm.html(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Disk cache is best-effort; memory cache remains authoritative.
        }
    }
}
