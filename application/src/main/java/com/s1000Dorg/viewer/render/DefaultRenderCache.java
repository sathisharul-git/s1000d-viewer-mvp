package com.s1000Dorg.viewer.render;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.config.RenderProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DefaultRenderCache implements RenderCache {

    private final ConcurrentHashMap<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final Path cacheRoot;
    private final Duration cacheTtl;

    public DefaultRenderCache(FsDataRepository repository, RenderProperties renderProperties) {
        this.cacheRoot = repository.cacheDir();
        this.cacheTtl = Duration.ofSeconds(Math.max(renderProperties.getCacheTtlSeconds(), 0));
        try {
            Files.createDirectories(cacheRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize render cache", ex);
        }
    }

    @Override
    public Optional<RenderedDm> get(String key) {
        CacheEntry entry = memoryCache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!cacheTtl.isZero() && Instant.now().isAfter(entry.createdAt().plus(cacheTtl))) {
            memoryCache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.renderedDm());
    }

    @Override
    public void put(String key, RenderedDm renderedDm) {
        memoryCache.put(key, new CacheEntry(renderedDm, Instant.now()));
        Path cacheFile = cacheRoot.resolve(key + ".html").normalize();
        try {
            Files.writeString(cacheFile, renderedDm.html(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Disk cache is best-effort; memory cache remains authoritative.
        }
    }

    private record CacheEntry(RenderedDm renderedDm, Instant createdAt) {
    }
}

