package furnidata;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import extension.TestLoggerLifecycleExtension;
import furnidata.FurniDataState;
import gearth.misc.Cacher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(TestLoggerLifecycleExtension.class)
class FurniDataToolsTest {

    @TempDir
    static File tempCacheDir;

    private static String testFurniJson;

    @BeforeAll
    public static void setUpStatic() {
        Cacher.setCacheDir(tempCacheDir.getAbsolutePath());
        testFurniJson = createTestFurniJson();
    }

    private static String createTestFurniJson() {
        org.json.JSONArray floorTypes = new org.json.JSONArray();
        JSONObject floorType = new JSONObject();
        floorType.put("classname", "mnstr_seed");
        floorType.put("id", 4284);
        floorType.put("revision", 0);
        floorType.put("offerid", 0);
        floorType.put("rentofferid", 0);
        floorType.put("bc", false);
        floorType.put("rare", false);
        floorType.put("buyout", false);
        floorType.put("rentbuyout", false);
        floorType.put("excludeddynamic", false);
        floorType.put("xdim", 1);
        floorType.put("ydim", 1);
        floorType.put("defaultdir", 0);
        floorType.put("specialtype", 0);
        floorType.put("bcofferid", 0);
        floorType.put("canstandon", false);
        floorType.put("cansiton", false);
        floorType.put("canlayon", false);
        floorType.put("customparams", "");
        floorTypes.put(floorType);

        org.json.JSONArray wallTypes = new org.json.JSONArray();
        JSONObject wallType = new JSONObject();
        wallType.put("classname", "post_it");
        wallType.put("id", 4213);
        wallType.put("revision", 0);
        wallType.put("offerid", 0);
        wallType.put("rentofferid", 0);
        wallType.put("bc", false);
        wallType.put("rare", false);
        wallType.put("buyout", false);
        wallType.put("rentbuyout", false);
        wallType.put("excludeddynamic", false);
        wallType.put("customparams", "");
        wallTypes.put(wallType);

        JSONObject roomItemTypes = new JSONObject();
        roomItemTypes.put("furnitype", floorTypes);

        JSONObject wallItemTypes = new JSONObject();
        wallItemTypes.put("furnitype", wallTypes);

        JSONObject root = new JSONObject();
        root.put("roomitemtypes", roomItemTypes);
        root.put("wallitemtypes", wallItemTypes);

        return root.toString();
    }

    private static String readFileContents(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            fis.read(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String fetchCurrentHash(String countryCode) throws Exception {
        java.util.Map<String, String> domainMap = new java.util.HashMap<>();
        domainMap.put("br", ".com.br");
        domainMap.put("de", ".de");
        domainMap.put("es", ".es");
        domainMap.put("fi", ".fi");
        domainMap.put("fr", ".fr");
        domainMap.put("it", ".it");
        domainMap.put("nl", ".nl");
        domainMap.put("tr", ".com.tr");
        domainMap.put("us", ".com");

        String baseUrl = "https://www.habbo" + domainMap.get(countryCode) + "/gamedata/furnidata_json/1";
        java.net.URL url = new java.net.URL(baseUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        int responseCode = conn.getResponseCode();
        if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
            String location = conn.getHeaderField("Location");
            if (location != null) {
                String[] parts = location.split("/");
                return parts[parts.length - 1];
            }
        }
        return null;
    }

    @Test
    void testLoadFurniDataNormallyWithoutCache(TestInfo testInfo) throws Exception {
        log.info("[TEST] Running: {}", testInfo.getDisplayName());

        CompletableFuture<FurniDataState> resultFuture = new CompletableFuture<>();
        Consumer<FurniDataState> callback = resultFuture::complete;

        FurniDataTools tools = new FurniDataTools("game-us.habbo.com", callback);

        try {
            FurniDataState result = resultFuture.get(10, TimeUnit.SECONDS);

            assertTrue(tools.isReady(), "FurniDataTools should be ready after loading from URL");

            Integer seedTypeId = tools.getFloorTypeId("mnstr_seed");
            assertNotNull(seedTypeId, "mnstr_seed type ID should be resolved from URL data");

            File cacheFile = new File(Cacher.getCacheDir(), "furnidata_us_cache.json");
            assertTrue(cacheFile.exists(), "Cache file should be created after loading from URL");

            Object hash = Cacher.get("plants.furnidataHash_us");
            assertNotNull(hash, "FurniDataTools should store a hash in cache after loading from URL");
            assertNotNull(hash.toString(), "Hash should not be null");
            assertTrue(hash.toString().length() > 0, "Hash should not be empty");

            assertEquals(FurniDataState.LOADED, result, "Load result must be LOADED");
            log.info("[TEST] {} - Load result: {}", testInfo.getDisplayName(), result);
        } finally {
            tools = null;
        }
    }

    @Test
    void testLoadFromCacheReturnsSmallHttpResponse(TestInfo testInfo) throws Exception {
        log.info("[TEST] Running: {}", testInfo.getDisplayName());

        // Set up cache with test data
        Cacher.updateCache(testFurniJson, "furnidata_fr_cache.json");

        // Fetch the current hash from the server's redirect
        String currentHash = fetchCurrentHash("fr");
        assertNotNull(currentHash, "Should be able to fetch hash from server redirect");

        // Store the matching hash in cache
        Cacher.put("plants.furnidataHash_fr", currentHash);

        File cacheFile = new File(Cacher.getCacheDir(), "furnidata_fr_cache.json");
        assertTrue(cacheFile.exists(), "Cache file should exist before load");

        String content = readFileContents(cacheFile);
        JSONObject object = new JSONObject(content);

        assertEquals("mnstr_seed", object.getJSONObject("roomitemtypes").getJSONArray("furnitype").getJSONObject(0).getString("classname"));
        assertEquals(4284, object.getJSONObject("roomitemtypes").getJSONArray("furnitype").getJSONObject(0).getInt("id"));

        assertEquals("post_it", object.getJSONObject("wallitemtypes").getJSONArray("furnitype").getJSONObject(0).getString("classname"));
        assertEquals(4213, object.getJSONObject("wallitemtypes").getJSONArray("furnitype").getJSONObject(0).getInt("id"));

        // Cache data is parsed synchronously in the constructor, so type IDs are immediately available.
        // With a matching hash, fetch() should return CACHED without downloading the full >5MB data.
        // Only a small HTTP request is needed to check the redirect hash.
        CompletableFuture<FurniDataState> resultFuture = new CompletableFuture<>();
        Consumer<FurniDataState> callback = resultFuture::complete;

        FurniDataTools tools = new FurniDataTools("game-fr.habbo.com", callback);

        try {
            Integer seedTypeId = tools.getFloorTypeId("mnstr_seed");
            assertEquals(4284, seedTypeId, "mnstr_seed type ID should be resolved from cache immediately (before URL response)");

            FurniDataState result = resultFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(result, "LoadResult callback should have been called");
            assertEquals(FurniDataState.CACHED, result, "Load result must be CACHED (hash matches)");
            log.info("[TEST] {} - Load result: {}", testInfo.getDisplayName(), result);
        } finally {
            tools = null;
        }
    }

    @Test
    void testCacheFileContainsCorrectData(TestInfo testInfo) throws Exception {
        log.info("[TEST] Running: {}", testInfo.getDisplayName());

        Cacher.updateCache(testFurniJson, "furnidata_de_cache.json");

        File cacheFile = new File(Cacher.getCacheDir(), "furnidata_de_cache.json");
        assertTrue(cacheFile.exists(), "Cache file should exist");

        String content = readFileContents(cacheFile);
        JSONObject object = new JSONObject(content);

        assertEquals("mnstr_seed", object.getJSONObject("roomitemtypes").getJSONArray("furnitype").getJSONObject(0).getString("classname"));
        assertEquals(4284, object.getJSONObject("roomitemtypes").getJSONArray("furnitype").getJSONObject(0).getInt("id"));

        assertEquals("post_it", object.getJSONObject("wallitemtypes").getJSONArray("furnitype").getJSONObject(0).getString("classname"));
        assertEquals(4213, object.getJSONObject("wallitemtypes").getJSONArray("furnitype").getJSONObject(0).getInt("id"));

        log.info("[TEST] {} - PASSED", testInfo.getDisplayName());
    }
}
