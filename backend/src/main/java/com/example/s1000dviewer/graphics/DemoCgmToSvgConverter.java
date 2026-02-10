package com.example.s1000dviewer.graphics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DemoCgmToSvgConverter implements CgmToSvgConverter {

    private static final Pattern ICN_PATTERN = Pattern.compile("ICN-[A-Z0-9-]+");

    @Override
    public String convert(InputStream cgmStream) throws IOException {
        byte[] payload = cgmStream.readAllBytes();
        String icn = detectIcn(payload);

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1100 460" role="img" aria-label="CGM conversion unavailable">
              <rect width="1100" height="460" fill="#f8fafc"/>
              <rect x="22" y="22" width="1056" height="416" rx="14" fill="#ffffff" stroke="#cbd5e1" stroke-width="2"/>
              <text x="52" y="88" fill="#0f172a" font-size="31" font-family="Segoe UI, sans-serif">CGM conversion is not available in this runtime</text>
              <text x="52" y="132" fill="#334155" font-size="19" font-family="Segoe UI, sans-serif">Install jcgm jars in backend/libs/jcgm and restart backend to enable real CGM rendering.</text>
              <text x="52" y="174" fill="#334155" font-size="19" font-family="Segoe UI, sans-serif">ICN: %s</text>
              <text x="52" y="206" fill="#334155" font-size="19" font-family="Segoe UI, sans-serif">Source size: %d bytes</text>
              <line x1="52" y1="238" x2="1048" y2="238" stroke="#e2e8f0" stroke-width="1"/>
              <text x="52" y="286" fill="#0f766e" font-size="18" font-family="Segoe UI, sans-serif">Fallback behavior:</text>
              <text x="52" y="318" fill="#334155" font-size="17" font-family="Segoe UI, sans-serif">- If .svg/.png/.jpg/.gif exists for the same ICN, viewer serves it directly.</text>
              <text x="52" y="348" fill="#334155" font-size="17" font-family="Segoe UI, sans-serif">- This message is shown only when source is CGM-only and converter jars are missing.</text>
            </svg>
            """.formatted(escape(icn), payload.length);
    }

    private String detectIcn(byte[] payload) {
        String ascii = new String(payload, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        Matcher matcher = ICN_PATTERN.matcher(ascii);
        if (matcher.find()) {
            return matcher.group();
        }
        return "UNKNOWN-ICN";
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
