package furnidata;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import furnidata.details.FloorItemDetails;
import furnidata.details.WallItemDetails;
import gearth.misc.Cacher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FurniDataTools {

    private final String countryCode;
    private final String host;
    private final String cacheFilename;
    private volatile boolean isReady = false;

    private final Map<String, Integer> nameToTypeidFloor = new HashMap<>();
    private final Map<String, Integer> nameToTypeidWall = new HashMap<>();
    private final Map<Integer, String> typeIdToNameFloor = new HashMap<>();
    private final Map<Integer, String> typeIdToNameWall = new HashMap<>();

    private final Map<String, FloorItemDetails> nameToFloorItems = new HashMap<>();
    private final Map<String, WallItemDetails> nameToWallItems = new HashMap<>();

    public FurniDataTools(String host, Consumer<FurniDataState> onLoadListener) {
        this(host, onLoadListener, false);
    }

    public FurniDataTools(String host, Consumer<FurniDataState> onLoadListener, boolean forceRefresh) {
        this.host = host;
        countryCode = FurniDataUtils.hostToCountryCode(host);
        cacheFilename = FurniDataUtils.getCacheFilename(countryCode);

        boolean parsedFromCache = tryLoadFromCache();
        String cachedHash = getCachedHash();
        boolean cacheFresh = isCacheFresh();
        Consumer<FurniDataState> listener = onLoadListener;

        new Thread(() -> {
            try {
                fetch(listener, parsedFromCache, cachedHash, cacheFresh, forceRefresh);
            } catch (IOException e) {
                log.error("[FurniData] Failed to fetch furnidata for host: {}", host, e);
                if (parsedFromCache && listener != null) {
                    isReady = true;
                    listener.accept(FurniDataState.CACHED_WITH_REFRESH_ERROR);
                } else if (listener != null) {
                    listener.accept(FurniDataState.FAILED);
                }
            }
        }).start();

    }

    private boolean tryLoadFromCache() {
        if (!Cacher.cacheFileExists(cacheFilename)) {
            return false;
        }

        try {
            File cacheFile = new File(Cacher.getCacheDir(), cacheFilename);
            String contents = FileUtils.readFileToString(cacheFile, StandardCharsets.UTF_8);
            JSONObject object = new JSONObject(contents);
            parseJson(object);
            log.debug("[FurniData] Loaded furni data from cache file: {}", cacheFilename);
            return true;
        } catch (IOException | JSONException e) {
            log.warn("[FurniData] Failed to load cache file {}, will fetch from URL", cacheFilename, e);
            nameToTypeidFloor.clear();
            nameToTypeidWall.clear();
            typeIdToNameFloor.clear();
            typeIdToNameWall.clear();
            nameToFloorItems.clear();
            nameToWallItems.clear();
            return false;
        }
    }

    private void storeCache(String content) {
        try {
            Cacher.updateCache(content, cacheFilename);
            log.debug("[FurniData] Stored furni data cache file: {}", cacheFilename);
        } catch (Exception e) {
            log.warn("[FurniData] Failed to store cache file {}", cacheFilename, e);
        }
    }

    private void parseJson(JSONObject object) {
        JSONArray floorJson = object.getJSONObject("roomitemtypes").getJSONArray("furnitype");
        floorJson.forEach(o -> {
            JSONObject item = (JSONObject)o;
            nameToTypeidFloor.put(item.getString("classname"), item.getInt("id"));
            typeIdToNameFloor.put(item.getInt("id"), item.getString("classname"));
            nameToFloorItems.put(item.getString("classname"), new FloorItemDetails(item));
        });

        JSONArray wallJson = object.getJSONObject("wallitemtypes").getJSONArray("furnitype");
        wallJson.forEach(o -> {
            JSONObject item = (JSONObject)o;
            nameToTypeidWall.put(item.getString("classname"), item.getInt("id"));
            typeIdToNameWall.put(item.getInt("id"), item.getString("classname"));
            nameToWallItems.put(item.getString("classname"), new WallItemDetails(item));
        });
    }

    private String getFurniDataHashKey() {
        return FurniDataUtils.getHashCacheKey(countryCode);
    }

    private String getCachedHash() {
        try {
            Object val = Cacher.get(getFurniDataHashKey());
            if (val != null) {
                return val.toString();
            }
        } catch (Exception e) {
            log.warn("[FurniData] Failed to read hash from cache, will fetch from URL", e);
        }
        return null;
    }

    private void storeCachedHash(String hash) {
        try {
            Cacher.put(getFurniDataHashKey(), hash);
            log.debug("[FurniData] Stored hash {} for countryCode {}", hash, countryCode);
        } catch (Exception e) {
            log.warn("[FurniData] Failed to store hash in cache", e);
        }
    }

    private Long getCachedTimestamp() {
        try {
            Object val = Cacher.get(FurniDataUtils.getTimestampCacheKey(countryCode));
            if (val != null) {
                return Long.valueOf(val.toString());
            }
        } catch (NumberFormatException e) {
            log.warn("[FurniData] Failed to read timestamp from cache", e);
        }
        return null;
    }

    private Integer getCachedMaxAgeSeconds() {
        try {
            Object val = Cacher.get(FurniDataUtils.getMaxAgeCacheKey(countryCode));
            if (val != null) {
                return Integer.valueOf(val.toString());
            }
        } catch (NumberFormatException e) {
            log.warn("[FurniData] Failed to read max-age from cache", e);
        }
        return null;
    }

    private void storeCacheTimestampAndMaxAge(long timestamp, int maxAgeSeconds) {
        try {
            Cacher.put(FurniDataUtils.getTimestampCacheKey(countryCode), String.valueOf(timestamp));
            Cacher.put(FurniDataUtils.getMaxAgeCacheKey(countryCode), String.valueOf(maxAgeSeconds));
        } catch (Exception e) {
            log.warn("[FurniData] Failed to store cache timestamp/max-age", e);
        }
    }

    private void updateFreshness() {
        Integer maxAgeInt = getCachedMaxAgeSeconds();
        int maxAge = maxAgeInt != null ? maxAgeInt : FurniDataUtils.DEFAULT_CACHE_MAX_AGE_SECONDS;
        storeCacheTimestampAndMaxAge(System.currentTimeMillis(), maxAge);
    }

    private boolean isCacheFresh() {
        Long timestamp = getCachedTimestamp();
        if (timestamp == null) return false;
        Integer maxAge = getCachedMaxAgeSeconds();
        if (maxAge == null) return false;
        return System.currentTimeMillis() - timestamp < maxAge * 1000L;
    }

    private String furnidataUrl() {
        return FurniDataUtils.furnidataUrl(host);
    }

    private void fetch(Consumer<FurniDataState> onLoad, boolean wasCached, String cachedHash, boolean cacheFresh, boolean forceRefresh) throws IOException {
        if (!forceRefresh && cacheFresh) {
            log.debug("[FurniData] Cache Hit - cache is fresh (within max-age), using cached data");
            isReady = true;
            onLoad.accept(FurniDataState.CACHED);
            return;
        }

        if (!forceRefresh && wasCached && cachedHash != null) {
            String freshHash = FurniDataUtils.fetchCurrentHash(host);
            if (freshHash != null) {
                storeCachedHash(freshHash);
                if (freshHash.equals(cachedHash)) {
                    updateFreshness();
                    log.debug("[FurniData] Cache Hit - hash matches ({}), using cached data without full download", freshHash);
                    isReady = true;
                    onLoad.accept(FurniDataState.CACHED);
                    return;
                }
                log.debug("[FurniData] Hash differs (cached={}, fresh={}), downloading full data", cachedHash, freshHash);
            } else {
                log.warn("[FurniData] Failed to fetch hash from server, falling back to cache");
                if (wasCached) {
                    isReady = true;
                    onLoad.accept(FurniDataState.CACHED_WITH_REFRESH_ERROR);
                }
                return;
            }
        }

        downloadFullData(onLoad, wasCached);
    }

    private void downloadFullData(Consumer<FurniDataState> onLoad, boolean wasCached) throws IOException {
        String urlStr = furnidataUrl();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            log.debug("[FurniData] Cache Hit - HTTP 304 Not Modified, using cached data");
            updateFreshness();
            isReady = true;
            onLoad.accept(FurniDataState.CACHED);
            return;
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            log.warn("[FurniData] HTTP {} from furnidata URL, falling back to cache", responseCode);
            if (wasCached) {
                isReady = true;
                onLoad.accept(FurniDataState.CACHED_WITH_REFRESH_ERROR);
            } else {
                throw new IOException("HTTP " + responseCode + " and no cache available");
            }
            return;
        }

        String content = IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8);
        JSONObject object = new JSONObject(content);
        parseJson(object);
        storeCache(content);

        String finalUrl = conn.getURL().toString();
        String hash = FurniDataUtils.extractHashFromUrl(finalUrl);
        storeCachedHash(hash);

        String cacheControl = conn.getHeaderField("Cache-Control");
        int maxAgeSeconds = FurniDataUtils.parseMaxAgeSeconds(cacheControl);
        storeCacheTimestampAndMaxAge(System.currentTimeMillis(), maxAgeSeconds);

        isReady = true;
        onLoad.accept(FurniDataState.LOADED);
    }

    public boolean isReady() {
        return isReady;
    }

    public Integer getFloorTypeId(String furniName) {
        return nameToTypeidFloor.get(furniName);
    }
    public Integer getWallTypeId(String furniName) {
        return nameToTypeidWall.get(furniName);
    }
    public String getFloorItemName(int typeId) { return typeIdToNameFloor.get(typeId); }
    public String getWallItemName(int typeId) { return typeIdToNameWall.get(typeId); }

    public FloorItemDetails getFloorItemDetails(String furniName) {
        return nameToFloorItems.get(furniName);
    }
    public WallItemDetails getWallItemDetails(String furniName) {
        return nameToWallItems.get(furniName);
    }

}
