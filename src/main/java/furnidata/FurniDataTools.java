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
import org.json.JSONObject;

import furnidata.details.FloorItemDetails;
import furnidata.details.WallItemDetails;
import furnidata.FurniDataState;
import gearth.misc.Cacher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FurniDataTools {

    private static final Map<String, String> codeToDomainMap = new HashMap<>();
    static {
        codeToDomainMap.put("br", ".com.br");
        codeToDomainMap.put("de", ".de");
        codeToDomainMap.put("es", ".es");
        codeToDomainMap.put("fi", ".fi");
        codeToDomainMap.put("fr", ".fr");
        codeToDomainMap.put("it", ".it");
        codeToDomainMap.put("nl", ".nl");
        codeToDomainMap.put("tr", ".com.tr");
        codeToDomainMap.put("us", ".com");
    }

    private static final String FURNIDATA_PREFIX = "plants.";

    private final String countryCode;
    private final String cacheFilename;
    private volatile boolean isReady = false;

    private final Map<String, Integer> nameToTypeidFloor = new HashMap<>();
    private final Map<String, Integer> nameToTypeidWall = new HashMap<>();
    private final Map<Integer, String> typeIdToNameFloor = new HashMap<>();
    private final Map<Integer, String> typeIdToNameWall = new HashMap<>();

    private final Map<String, FloorItemDetails> nameToFloorItems = new HashMap<>();
    private final Map<String, WallItemDetails> nameToWallItems = new HashMap<>();

    public FurniDataTools(String host, Consumer<FurniDataState> onLoadListener) {
        countryCode = host.substring(5, 7);
        cacheFilename = "furnidata_" + countryCode + "_cache.json";

        boolean parsedFromCache = tryLoadFromCache();
        String cachedHash = getCachedHash();
        Consumer<FurniDataState> listener = onLoadListener;

        new Thread(() -> {
            try {
                fetch(listener, parsedFromCache, cachedHash);
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
        } catch (Exception e) {
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
        return FURNIDATA_PREFIX + "furnidataHash_" + countryCode;
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

    private String extractHashFromUrl(String locationUrl) {
        String[] parts = locationUrl.split("/");
        return parts[parts.length - 1];
    }

    private void fetch(Consumer<FurniDataState> onLoad, boolean wasCached, String cachedHash) throws IOException {
        if (wasCached && cachedHash != null) {
            log.debug("[FurniData] Checking if hash {} matches cached hash for {}", cachedHash, countryCode);
            String baseUrl = furnidataUrl();
            URL url = new URL(baseUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();

            if (responseCode >= 300 && responseCode < 400) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    String freshHash = extractHashFromUrl(location);
                    if (freshHash.equals(cachedHash)) {
                        log.debug("[FurniData] Hash matches ({}), using cached data without full download", freshHash);
                        isReady = true;
                        onLoad.accept(FurniDataState.CACHED);
                        return;
                    } else {
                        log.debug("[FurniData] Hash differs (cached={}, fresh={}), fetching full data", cachedHash, freshHash);
                    }
                }
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " when checking furnidata redirect for " + countryCode);
            }
        }

        String urlStr = furnidataUrl();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            log.debug("[FurniData] HTTP 304 Not Modified, using cached data");
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
        String hash = extractHashFromUrl(finalUrl);
        storeCachedHash(hash);

        isReady = true;
        onLoad.accept(FurniDataState.LOADED);
    }

    private String furnidataUrl() {
        if (countryCode.equals("s2")) {
            return "https://sandbox.habbo.com/gamedata/furnidata_json/1";
        }

        return String.format("https://www.habbo%s/gamedata/furnidata_json/1", codeToDomainMap.get(countryCode));
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
