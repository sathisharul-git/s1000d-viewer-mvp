package com.s1000Dorg.viewer.storage;

import com.s1000Dorg.viewer.config.StorageProperties;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.IcnEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.PmcEntity;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.IcnRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VaultService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final Path csdbRoot;
    private final Path publishedRoot;
    private final Path cacheRoot;
    private final DmRepository dmRepository;
    private final PmcRepository pmcRepository;
    private final IcnRepository icnRepository;

    public VaultService(
        StorageProperties storageProperties,
        DmRepository dmRepository,
        PmcRepository pmcRepository,
        IcnRepository icnRepository
    ) {
        this.csdbRoot = Path.of(storageProperties.getCsdbRoot()).toAbsolutePath().normalize();
        this.publishedRoot = Path.of(storageProperties.getPublishedRoot()).toAbsolutePath().normalize();
        this.cacheRoot = Path.of(storageProperties.getCacheRoot()).toAbsolutePath().normalize();
        this.dmRepository = dmRepository;
        this.pmcRepository = pmcRepository;
        this.icnRepository = icnRepository;
    }

    public Path resolveDmFile(String dmId) {
        validateId(dmId, "Invalid dmId");
        DmEntity dm = dmRepository.findByDmIdIgnoreCase(dmId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module metadata not found"));
        return resolveVaultPath(csdbRoot, dm.getVaultPath(), "Data module file not found");
    }

    public Path resolvePmcFile(String pmcId) {
        validateId(pmcId, "Invalid pmcId");
        PmcEntity pmc = pmcRepository.findByPmcIdIgnoreCase(pmcId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PMC metadata not found"));
        return resolveVaultPath(csdbRoot, pmc.getVaultPath(), "PMC file not found");
    }

    public Path resolveIcnFile(String icnId) {
        validateId(icnId, "Invalid icnId");
        IcnEntity icn = icnRepository.findByIcnIdIgnoreCase(icnId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Graphic metadata not found"));
        return resolveVaultPath(csdbRoot, icn.getVaultPath(), "Graphic file not found");
    }

    public Optional<Path> resolvePublishedHtml(String dmId) {
        validateId(dmId, "Invalid dmId");
        Path path = publishedRoot.resolve(Path.of("dm", dmId, "index.html")).normalize();
        validatePathSecurity(publishedRoot, path);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public Path ensureCachePath(String icnId) {
        validateId(icnId, "Invalid icnId");
        Path path = cacheRoot.resolve(icnId + ".svg").normalize();
        validatePathSecurity(cacheRoot, path);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create cache directory", ex);
        }
        return path;
    }

    public String computeFileHash(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute file hash for " + file, ex);
        }
    }

    public void validatePathSecurity(Path root, Path candidate) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vault path");
        }
    }

    public Path csdbRoot() {
        return csdbRoot;
    }

    public Path publishedRoot() {
        return publishedRoot;
    }

    private Path resolveVaultPath(Path root, String relativePath, String notFoundMessage) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        Path resolved = root.resolve(relativePath).normalize();
        validatePathSecurity(root, resolved);
        if (!Files.isRegularFile(resolved)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return resolved;
    }

    private void validateId(String value, String message) {
        if (value == null || value.isBlank() || !SAFE_ID.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
