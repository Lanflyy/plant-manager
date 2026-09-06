package furnidata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import extension.TestLoggerLifecycleExtension;
import extension.util.PreferenceCacher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(TestLoggerLifecycleExtension.class)
class FurniDataToolsTest {

    private static final File TEST_OUTPUT_DIR = new File("target/test/output");

    @BeforeAll
    public static void setUpStatic() {
        try {
            FileUtils.deleteDirectory(TEST_OUTPUT_DIR);
        } catch (IOException ignored) {}
        TEST_OUTPUT_DIR.mkdirs();
        PreferenceCacher.setCacheDir(TEST_OUTPUT_DIR.getAbsolutePath());
        log.info("[TEST] Cache directory set to: {}", TEST_OUTPUT_DIR.getAbsolutePath());
        log.info("[TEST] Intermediary test artifacts (furnidata cache files) will be stored in: {}", TEST_OUTPUT_DIR.getAbsolutePath());
    }

    private static String readFileContents(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            fis.read(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String createTestFurniJson(String countryCode) {
        JSONArray floorTypes = new JSONArray();
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

        JSONArray wallTypes = new JSONArray();
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

    @Test
    void testLoadFurniDataNormallyWithoutCache(TestInfo testInfo) throws Exception {
        log.info("[TEST] Running: {}", testInfo.getDisplayName());

        CompletableFuture<FurniDataState> resultFuture = new CompletableFuture<>();
        Consumer<FurniDataState> callback = resultFuture::complete;

        final String host = "game-us.habbo.com";
        final String countryCode = FurniDataUtils.hostToCountryCode(host);
        FurniDataTools tools = new FurniDataTools(host, callback);

        try {
            FurniDataState result = resultFuture.get(10, TimeUnit.SECONDS);

            assertTrue(tools.isReady(), "FurniDataTools should be ready after loading from URL");

            Integer seedTypeId = tools.getFloorTypeId("mnstr_seed");
            assertNotNull(seedTypeId, "mnstr_seed type ID should be resolved from URL data");

            File cacheFile = new File(PreferenceCacher.getCacheDir(), FurniDataUtils.getCacheFilename(countryCode));
            assertTrue(cacheFile.exists(), "Cache file should be created after loading from URL");

            Object hash = PreferenceCacher.get(FurniDataUtils.getHashCacheKey(countryCode));
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

        final String countryCode = "fr";
        final String host = "game-" + countryCode + ".habbo.com";

        // Set up cache with test data
        PreferenceCacher.updateCache(createTestFurniJson(countryCode), FurniDataUtils.getCacheFilename(countryCode));

        // Fetch the current hash from the server's redirect
        String currentHash = FurniDataUtils.fetchCurrentHash(host);
        assertNotNull(currentHash, "Should be able to fetch hash from server redirect");

        // Store the matching hash in cache
        PreferenceCacher.put(FurniDataUtils.getHashCacheKey(countryCode), currentHash);

        File cacheFile = new File(PreferenceCacher.getCacheDir(), FurniDataUtils.getCacheFilename(countryCode));
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

        FurniDataTools tools = new FurniDataTools(host, callback);

        try {
            Integer seedTypeId = tools.getFloorTypeId("mnstr_seed");
            assertEquals(4284, seedTypeId, "mnstr_seed type ID should be resolved from cache immediately (before URL response)");

            FurniDataState result = resultFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(result, "LoadResult callback should have been called");
            assertEquals(FurniDataState.CACHED, result, "Load result must be Cache Hit (hash matches)");
            log.info("[TEST] {} - Load result: {}", testInfo.getDisplayName(), result);
            log.info("[TEST] {} - Cache Hit confirmed (cached data used without full download)", testInfo.getDisplayName());
        } finally {
            tools = null;
        }
    }

    @Test
    void testCacheFileContainsCorrectData(TestInfo testInfo) throws Exception {
        log.info("[TEST] Running: {}", testInfo.getDisplayName());

        final String countryCode = "de";

        PreferenceCacher.updateCache(createTestFurniJson(countryCode), FurniDataUtils.getCacheFilename(countryCode));

        File cacheFile = new File(PreferenceCacher.getCacheDir(), FurniDataUtils.getCacheFilename(countryCode));
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
