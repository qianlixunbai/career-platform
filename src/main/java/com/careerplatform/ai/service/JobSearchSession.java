package com.careerplatform.ai.service;

import com.careerplatform.ai.client.JobSearchGateway.ProviderSearchResult;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Request-local map from opaque result keys to provider facts.
 *
 * <p>A new instance must be created for every discovery request.  The class
 * does not fetch source URLs; it only validates and stores provider output so
 * later Java code can reconstruct facts without trusting model supplied URLs.</p>
 */
public final class JobSearchSession {

    private static final int MAX_SOURCE_URL_LENGTH = 500;
    private static final int MAX_SOURCE_HOST_LENGTH = 150;
    private static final int MAX_SOURCE_TITLE_LENGTH = 500;
    private static final int MAX_SOURCE_SNIPPET_LENGTH = 2_000;

    private final Map<String, ProviderSearchResult> resultsByKey = new LinkedHashMap<>();
    private final Set<String> canonicalUrls = new HashSet<>();

    /**
     * Register provider facts and return the safe opaque references exposed to
     * the model. Invalid URLs and duplicate canonical URLs are dropped.
     */
    public synchronized List<SearchHit> register(List<ProviderSearchResult> providerResults) {
        if (providerResults == null || providerResults.isEmpty()) {
            return List.of();
        }

        List<SearchHit> hits = new ArrayList<>();
        for (ProviderSearchResult providerResult : providerResults) {
            SanitizedResult sanitized = sanitize(providerResult);
            if (sanitized == null || !canonicalUrls.add(sanitized.canonicalUrl())) {
                continue;
            }

            String resultKey = newResultKey();
            resultsByKey.put(resultKey, sanitized.result());
            hits.add(new SearchHit(
                    resultKey,
                    sanitized.result().sourceTitle(),
                    sanitized.result().sourceSnippet()));
        }
        return List.copyOf(hits);
    }

    /** Resolve an opaque key from this session only. */
    public synchronized ProviderSearchResult resolve(String resultKey) {
        if (resultKey == null || resultKey.isBlank()) {
            return null;
        }
        return resultsByKey.get(resultKey);
    }

    private String newResultKey() {
        String resultKey;
        do {
            resultKey = "result-" + UUID.randomUUID();
        }
        while (resultsByKey.containsKey(resultKey));
        return resultKey;
    }

    private SanitizedResult sanitize(ProviderSearchResult providerResult) {
        if (providerResult == null || providerResult.sourceUrl() == null) {
            return null;
        }

        String sourceUrl = providerResult.sourceUrl();
        if (sourceUrl.isBlank() || sourceUrl.length() > MAX_SOURCE_URL_LENGTH) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(sourceUrl);
        }
        catch (URISyntaxException exception) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = stripTrailingDots(uri.getHost());
        if (scheme == null || host == null || host.isBlank()
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || uri.getUserInfo() != null
                || uri.getPort() > 65535
                || uri.getPort() > 65_535
                || (host.indexOf('.') < 0 && host.indexOf(':') < 0)
                || isBlockedHost(host)
                || host.length() > MAX_SOURCE_HOST_LENGTH) {
            return null;
        }

        String canonicalUrl = canonicalize(uri, scheme, host);
        if (canonicalUrl == null) {
            return null;
        }

        String sourceTitle = bound(providerResult.sourceTitle(), MAX_SOURCE_TITLE_LENGTH);
        String sourceSnippet = bound(providerResult.sourceSnippet(), MAX_SOURCE_SNIPPET_LENGTH);
        String sourceHost = host;
        if (sourceHost.length() > MAX_SOURCE_HOST_LENGTH) {
            return null;
        }

        ProviderSearchResult safeResult = new ProviderSearchResult(
                sourceUrl,
                sourceTitle,
                sourceHost,
                sourceSnippet,
                providerResult.publishedAt());
        return new SanitizedResult(canonicalUrl, safeResult);
    }

    private static String canonicalize(URI uri, String scheme, String host) {
        String normalizedPath;
        try {
            normalizedPath = uri.normalize().getRawPath();
        }
        catch (RuntimeException exception) {
            return null;
        }
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            normalizedPath = "/";
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.indexOf(':') >= 0 && !normalizedHost.startsWith("[")) {
            normalizedHost = "[" + normalizedHost + "]";
        }

        int port = uri.getPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        StringBuilder value = new StringBuilder()
                .append(scheme.toLowerCase(Locale.ROOT))
                .append("://")
                .append(normalizedHost);
        if (port >= 0 && !defaultPort) {
            value.append(':').append(port);
        }
        value.append(normalizedPath);
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            value.append('?').append(uri.getRawQuery());
        }
        return value.toString();
    }

    private static String bound(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static boolean isBlockedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        while (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")) {
            return true;
        }

        if (normalizedHost.indexOf('%') >= 0) {
            return true;
        }

        if (!normalizedHost.contains(".") && !normalizedHost.contains(":")) {
            return true;
        }

        if (looksLikeIpv4Literal(normalizedHost)) {
            return isBlockedIpv4(normalizedHost);
        }

        if (normalizedHost.indexOf(':') >= 0) {
            try {
                InetAddress address = InetAddress.getByName(normalizedHost);
                return isBlockedInetAddress(address);
            }
            catch (Exception exception) {
                return true;
            }
        }
        return false;
    }

    private static String stripTrailingDots(String host) {
        if (host == null) {
            return null;
        }
        int end = host.length();
        while (end > 0 && host.charAt(end - 1) == '.') {
            end--;
        }
        return end == host.length() ? host : host.substring(0, end);
    }

    private static boolean looksLikeIpv4Literal(String host) {
        return host.matches("[0-9.]+");
    }

    private static boolean isBlockedIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return true;
        }
        int[] octets = new int[4];
        try {
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty() || (parts[index].length() > 1 && parts[index].startsWith("0"))) {
                    return true;
                }
                octets[index] = Integer.parseInt(parts[index]);
                if (octets[index] < 0 || octets[index] > 255) {
                    return true;
                }
            }
        }
        catch (NumberFormatException exception) {
            return true;
        }

        int first = octets[0];
        int second = octets[1];
        return first == 0 || first == 10 || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 192 && second == 0 && octets[2] == 0)
                || (first == 198 && (second == 18 || second == 19))
                || first >= 224;
    }

    private static boolean isBlockedInetAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address inet6Address) {
            byte[] bytes = inet6Address.getAddress();
            // fc00::/7 is IPv6 unique-local space and must not be accepted.
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return address instanceof Inet4Address && isBlockedIpv4(address.getHostAddress());
    }

    private record SanitizedResult(String canonicalUrl, ProviderSearchResult result) {
    }

    /** Safe model-facing reference; it deliberately omits source URL and host. */
    public record SearchHit(String resultKey, String sourceTitle, String sourceSnippet) {
    }
}
