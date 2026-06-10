package extension.util;

import extension.entity.AutoBreedTrustedUser;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HEntityType;
import gearth.misc.Cacher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple runtime settings holder for the Plants extension.
 * Settings default values can be changed here; UI updates them at runtime.
 */
public final class PlantSettings {
    private PlantSettings() {}

    private static volatile boolean REQUEST_PET_INFO_BEFORE_TREAT = true;
    private static final String AUTO_BREED_ENABLED_CACHE_KEY = "plants.autoBreedEnabled";
    private static final String AUTO_BREED_ACCEPT_ALL_USERS_CACHE_KEY = "plants.autoBreedAcceptAllUsers";
    private static final String AUTO_BREED_TRUSTED_USERS_CACHE_KEY = "plants.autoBreedTrustedUsers";
    private static final String AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY_CACHE_KEY = "plants.autoBreedRequireSameOrHigherRarity";
    private static volatile boolean AUTO_BREED_ENABLED = false;
    private static volatile boolean AUTO_BREED_ACCEPT_ALL_USERS = false;
    private static volatile boolean AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY = true;
    private static volatile boolean AUTO_BREED_LOADED = false;
    private static final Set<String> AUTO_BREED_TRUSTED_USERS = new LinkedHashSet<>();
    private static final List<AutoBreedTrustedUser> AUTO_BREED_TRUSTED_USER_ROWS = new ArrayList<>();
    private static final List<Runnable> AUTO_BREED_LISTENERS = new CopyOnWriteArrayList<>();

    public static boolean isRequestPetInfoBeforeTreat() {
        return REQUEST_PET_INFO_BEFORE_TREAT;
    }

    public static void setRequestPetInfoBeforeTreat(boolean v) {
        REQUEST_PET_INFO_BEFORE_TREAT = v;
    }

    public static boolean isAutoBreedEnabled() {
        ensureAutoBreedLoaded();
        return AUTO_BREED_ENABLED;
    }

    public static void setAutoBreedEnabled(boolean v) {
        ensureAutoBreedLoaded();
        AUTO_BREED_ENABLED = v;
        Cacher.put(AUTO_BREED_ENABLED_CACHE_KEY, v);
        notifyAutoBreedListeners();
    }

    public static boolean isAutoBreedAcceptAllUsers() {
        ensureAutoBreedLoaded();
        return AUTO_BREED_ACCEPT_ALL_USERS;
    }

    public static void setAutoBreedAcceptAllUsers(boolean v) {
        ensureAutoBreedLoaded();
        AUTO_BREED_ACCEPT_ALL_USERS = v;
        Cacher.put(AUTO_BREED_ACCEPT_ALL_USERS_CACHE_KEY, v);
        notifyAutoBreedListeners();
    }

    public static boolean isAutoBreedRequireSameOrHigherRarity() {
        ensureAutoBreedLoaded();
        return AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY;
    }

    public static void setAutoBreedRequireSameOrHigherRarity(boolean v) {
        ensureAutoBreedLoaded();
        AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY = v;
        Cacher.put(AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY_CACHE_KEY, v);
        notifyAutoBreedListeners();
    }

    public static List<String> getAutoBreedTrustedUsernames() {
        ensureAutoBreedLoaded();
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            return new ArrayList<>(AUTO_BREED_TRUSTED_USERS);
        }
    }

    public static List<AutoBreedTrustedUser> getAutoBreedTrustedUsers() {
        ensureAutoBreedLoaded();
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            return new ArrayList<>(AUTO_BREED_TRUSTED_USER_ROWS);
        }
    }

    public static boolean addAutoBreedTrustedUsername(String username) {
        ensureAutoBreedLoaded();
        String cleaned = cleanUsername(username);
        if (cleaned.isEmpty()) return false;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            if (findAutoBreedTrustedUsername(cleaned) != null) return false;
            AUTO_BREED_TRUSTED_USERS.add(cleaned);
            AUTO_BREED_TRUSTED_USER_ROWS.add(new AutoBreedTrustedUser(cleaned));
            saveAutoBreedTrustedUsernames();
        }
        notifyAutoBreedListeners();
        return true;
    }

    public static boolean removeAutoBreedTrustedUsername(String username) {
        ensureAutoBreedLoaded();
        String cleaned = cleanUsername(username);
        if (cleaned.isEmpty()) return false;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            String existing = findAutoBreedTrustedUsername(cleaned);
            if (existing == null) return false;
            AUTO_BREED_TRUSTED_USERS.remove(existing);
            AutoBreedTrustedUser row = findAutoBreedTrustedUserRow(existing);
            if (row != null) {
                AUTO_BREED_TRUSTED_USER_ROWS.remove(row);
            }
            saveAutoBreedTrustedUsernames();
        }
        notifyAutoBreedListeners();
        return true;
    }

    public static int removeAutoBreedTrustedUsernames(Collection<String> usernames) {
        ensureAutoBreedLoaded();
        if (usernames == null || usernames.isEmpty()) return 0;
        int removed = 0;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            for (String username : usernames) {
                String cleaned = cleanUsername(username);
                if (cleaned.isEmpty()) continue;
                String existing = findAutoBreedTrustedUsername(cleaned);
                if (existing == null) continue;
                AUTO_BREED_TRUSTED_USERS.remove(existing);
                AutoBreedTrustedUser row = findAutoBreedTrustedUserRow(existing);
                if (row != null) {
                    AUTO_BREED_TRUSTED_USER_ROWS.remove(row);
                }
                removed++;
            }
            if (removed > 0) {
                saveAutoBreedTrustedUsernames();
            }
        }
        if (removed > 0) {
            notifyAutoBreedListeners();
        }
        return removed;
    }

    public static boolean renameAutoBreedTrustedUsername(String oldUsername, String newUsername) {
        ensureAutoBreedLoaded();
        String oldCleaned = cleanUsername(oldUsername);
        String newCleaned = cleanUsername(newUsername);
        if (oldCleaned.isEmpty() || newCleaned.isEmpty()) return false;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            String existing = findAutoBreedTrustedUsername(oldCleaned);
            if (existing == null) return false;
            String duplicate = findAutoBreedTrustedUsername(newCleaned);
            if (duplicate != null && !duplicate.equalsIgnoreCase(existing)) return false;
            AutoBreedTrustedUser row = findAutoBreedTrustedUserRow(existing);
            AUTO_BREED_TRUSTED_USERS.remove(existing);
            AUTO_BREED_TRUSTED_USERS.add(newCleaned);
            if (row != null) {
                row.setUsername(newCleaned);
                row.setSelected(false);
                row.setEntity(null);
            }
            saveAutoBreedTrustedUsernames();
        }
        notifyAutoBreedListeners();
        return true;
    }

    public static boolean containsAutoBreedTrustedUsername(String username) {
        ensureAutoBreedLoaded();
        String cleaned = cleanUsername(username);
        if (cleaned.isEmpty()) return false;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            return findAutoBreedTrustedUsername(cleaned) != null;
        }
    }

    public static void addAutoBreedListener(Runnable listener) {
        if (listener != null) {
            AUTO_BREED_LISTENERS.add(listener);
        }
    }

    public static void resolveAutoBreedTrustedUserEntity(HEntity entity) {
        ensureAutoBreedLoaded();
        if (entity == null || entity.getEntityType() != HEntityType.HABBO) return;
        String username = cleanUsername(entity.getName());
        if (username.isEmpty()) return;
        boolean changed = false;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            AutoBreedTrustedUser row = findAutoBreedTrustedUserRow(username);
            if (row == null) return;
            HEntity currentEntity = row.getEntity();
            if (currentEntity == null || currentEntity.getId() != entity.getId()) {
                row.setEntity(entity);
                changed = true;
            }
        }
        if (changed) {
            notifyAutoBreedListeners();
        }
    }

    public static HEntity getHeldAutoBreedUserEntityByUsername(String username) {
        ensureAutoBreedLoaded();
        String cleaned = cleanUsername(username);
        if (cleaned.isEmpty()) return null;
        synchronized (AUTO_BREED_TRUSTED_USERS) {
            AutoBreedTrustedUser row = findAutoBreedTrustedUserRow(cleaned);
            return row == null ? null : row.getEntity();
        }
    }

    private static void ensureAutoBreedLoaded() {
        if (AUTO_BREED_LOADED) return;
        synchronized (PlantSettings.class) {
            if (AUTO_BREED_LOADED) return;
            try {
                Object enabled = Cacher.get(AUTO_BREED_ENABLED_CACHE_KEY);
                if (enabled instanceof Boolean) {
                    AUTO_BREED_ENABLED = (Boolean) enabled;
                }

                Object acceptAllUsers = Cacher.get(AUTO_BREED_ACCEPT_ALL_USERS_CACHE_KEY);
                if (acceptAllUsers instanceof Boolean) {
                    AUTO_BREED_ACCEPT_ALL_USERS = (Boolean) acceptAllUsers;
                }

                Object requireRarity = Cacher.get(AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY_CACHE_KEY);
                if (requireRarity instanceof Boolean) {
                    AUTO_BREED_REQUIRE_SAME_OR_HIGHER_RARITY = (Boolean) requireRarity;
                }

                List<Object> trustedUsers = Cacher.getList(AUTO_BREED_TRUSTED_USERS_CACHE_KEY);
                if (trustedUsers != null) {
                    synchronized (AUTO_BREED_TRUSTED_USERS) {
                        AUTO_BREED_TRUSTED_USERS.clear();
                        AUTO_BREED_TRUSTED_USER_ROWS.clear();
                        for (Object trustedUser : trustedUsers) {
                            if (trustedUser instanceof String) {
                                String cleaned = cleanUsername((String) trustedUser);
                                if (!cleaned.isEmpty() && findAutoBreedTrustedUsername(cleaned) == null) {
                                    AUTO_BREED_TRUSTED_USERS.add(cleaned);
                                    AUTO_BREED_TRUSTED_USER_ROWS.add(new AutoBreedTrustedUser(cleaned));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            AUTO_BREED_LOADED = true;
        }
    }

    private static String cleanUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static String findAutoBreedTrustedUsername(String username) {
        for (String trustedUsername : AUTO_BREED_TRUSTED_USERS) {
            if (trustedUsername.equalsIgnoreCase(username)) {
                return trustedUsername;
            }
        }
        return null;
    }

    private static AutoBreedTrustedUser findAutoBreedTrustedUserRow(String username) {
        for (AutoBreedTrustedUser row : AUTO_BREED_TRUSTED_USER_ROWS) {
            if (row.getUsername().equalsIgnoreCase(username)) {
                return row;
            }
        }
        return null;
    }

    private static void saveAutoBreedTrustedUsernames() {
        Cacher.put(AUTO_BREED_TRUSTED_USERS_CACHE_KEY, new ArrayList<>(AUTO_BREED_TRUSTED_USERS));
    }

    private static void notifyAutoBreedListeners() {
        for (Runnable listener : AUTO_BREED_LISTENERS) {
            try {
                listener.run();
            } catch (Exception ignored) {}
        }
    }
}




