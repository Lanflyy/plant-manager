package extension.i18n;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class MessageService {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String BUNDLE_PATH = "/i18n/messages_";

    private static volatile String currentLanguage = DEFAULT_LANGUAGE;
    private static final Map<String, Properties> cache = new HashMap<>();

    private MessageService() {
    }

    public static void setLanguage(String language) {
        if (language != null && !language.isEmpty()) {
            currentLanguage = language;
        }
    }

    public static String getLanguage() {
        return currentLanguage;
    }

    public static String getValue(String key) {
        return getValue(key, currentLanguage);
    }

    public static String getValue(String key, String language) {
        String value = lookupKey(key, language);
        if (value == null) {
            value = lookupKey(key, DEFAULT_LANGUAGE);
        }
        if (value == null) {
            return key;
        }
        return value;
    }

    public static String getValue(String key, Object... args) {
        String pattern = getValue(key);
        if (pattern.equals(key)) {
            return key;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    private static String lookupKey(String key, String language) {
        Properties props = loadProperties(language);
        return props != null ? props.getProperty(key) : null;
    }

    private static synchronized Properties loadProperties(String language) {
        Properties props = cache.get(language);
        if (props != null) {
            return props;
        }
        String resourcePath = BUNDLE_PATH + language + ".properties";
        props = new Properties();
        try (InputStream in = MessageService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
        cache.put(language, props);
        return props;
    }

    public static void clearCache() {
        cache.clear();
    }
}
