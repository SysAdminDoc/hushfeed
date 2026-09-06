/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.shared.diagnostics;

/** Removes request addresses, credentials and device identifiers from exported text. */
public final class DiagnosticRedactor {
    private static final String HOST_SUFFIXES =
            "(?:tiktokv?\\.com|tiktokcdn\\.com|byteoversea\\.com|bytedance\\.com|musical\\.ly|ibyteimg\\.com)";
    private static final String CREDENTIAL_NAMES =
            "[a-z0-9_-]*(?:token|session|sessionid|sid|secret|password|passwd|signature|cookie"
                    + "|auth|credential|device_id|deviceid|install_id|installid|iid|openudid"
                    + "|odin|ttwid|uid|sec_user_id|secuid)[a-z0-9_-]*";

    private DiagnosticRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
                .replaceAll("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\"'<>]+", "[url omitted]")
                .replaceAll("(?i)\\b(?:[a-z0-9-]+\\.)+" + HOST_SUFFIXES + "\\b(?:[:/][^\\s\"'<>]*)?",
                        "[host omitted]")
                .replaceAll("(?i)\\b(" + CREDENTIAL_NAMES + ")\\s*[=:]\\s*\"?[^\\s;,&\"'<>]+",
                        "$1=[omitted]");
    }
}
