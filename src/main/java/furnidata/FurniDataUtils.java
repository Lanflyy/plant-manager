package furnidata;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class FurniDataUtils {

    public static final int DEFAULT_CACHE_MAX_AGE_SECONDS = 604800;
    private static final String FURNIDATA_PREFIX = "plants.";
    private static final String SANDBOX_HOST_COUNTRY = "s2";
    private static final String FURNIDATA_PATH = "/gamedata/furnidata_json/1";

    private static final Map<String, String> CODE_TO_DOMAIN_MAP = new HashMap<>();
    static {
        CODE_TO_DOMAIN_MAP.put("br", ".com.br");
        CODE_TO_DOMAIN_MAP.put("de", ".de");
        CODE_TO_DOMAIN_MAP.put("es", ".es");
        CODE_TO_DOMAIN_MAP.put("fi", ".fi");
        CODE_TO_DOMAIN_MAP.put("fr", ".fr");
        CODE_TO_DOMAIN_MAP.put("it", ".it");
        CODE_TO_DOMAIN_MAP.put("nl", ".nl");
        CODE_TO_DOMAIN_MAP.put("tr", ".com.tr");
        CODE_TO_DOMAIN_MAP.put("us", ".com");
    }

    private FurniDataUtils() {}

    public static String hostToCountryCode(String host) {
        return host.substring(5, 7);
    }

    public static String countryCodeToDomain(String countryCode) {
        return CODE_TO_DOMAIN_MAP.get(countryCode);
    }

    public static String furnidataUrl(String host) {
        String countryCode = hostToCountryCode(host);
        if (SANDBOX_HOST_COUNTRY.equals(countryCode)) {
            return "https://sandbox.habbo.com" + FURNIDATA_PATH;
        }
        return "https://www.habbo" + countryCodeToDomain(countryCode) + FURNIDATA_PATH;
    }

    public static String getCacheFilename(String countryCode) {
        return "furnidata_" + countryCode + "_cache.json";
    }

    public static String getHashCacheKey(String countryCode) {
        return FURNIDATA_PREFIX + "furnidataHash_" + countryCode;
    }

    public static String getTimestampCacheKey(String countryCode) {
        return FURNIDATA_PREFIX + "furnidataTimestamp_" + countryCode;
    }

    public static String getMaxAgeCacheKey(String countryCode) {
        return FURNIDATA_PREFIX + "furnidataMaxAge_" + countryCode;
    }

    public static String extractHashFromUrl(String locationUrl) {
        String[] parts = locationUrl.split("/");
        return parts[parts.length - 1];
    }

    public static int parseMaxAgeSeconds(String cacheControlHeader) {
        if (cacheControlHeader == null) {
            return DEFAULT_CACHE_MAX_AGE_SECONDS;
        }
        String[] parts = cacheControlHeader.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("max-age=")) {
                try {
                    return Integer.parseInt(part.substring("max-age=".length()).trim());
                } catch (NumberFormatException e) {
                    log.warn("[FurniData] Failed to parse max-age from Cache-Control header: {}", part);
                }
            }
        }
        return DEFAULT_CACHE_MAX_AGE_SECONDS;
    }

    public static String fetchCurrentHash(String host) {
        String baseUrl = furnidataUrl(host);
        try {
            URL url = new URL(baseUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    return extractHashFromUrl(location);
                }
            }
        } catch (Exception e) {
            log.warn("[FurniData] Failed to fetch current hash for host {}", host, e);
        }
        return null;
    }
}
