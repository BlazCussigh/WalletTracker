package si.uni_lj.fe.tnuv.wallettracker.api;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ExchangeRateApi {
    private static final String TAG = "ExchangeRateApi";
    private static final String BASE_URL = "https://api.frankfurter.app/latest?from=EUR";

    public static final String PREFS_NAME = "wallet_prefs";
    public static final String KEY_REFRESH_INTERVAL = "refresh_interval_minutes";
    public static final String KEY_LAST_REFRESH = "last_refresh_timestamp";
    public static final int DEFAULT_INTERVAL_MINUTES = 30;

    // In-memory cache: rates from EUR (e.g. "USD" -> 1.08 means 1 EUR = 1.08 USD)
    private static final Map<String, Double> rateCache = new HashMap<>();
    private static long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minut

    // Hardcoded fallback rates (EUR base) for when API is unavailable
    private static final Map<String, Double> FALLBACK_RATES = new HashMap<String, Double>() {{
        put("USD", 1.08);
        put("GBP", 0.85);
        put("JPY", 163.0);
        put("CHF", 0.97);
        put("CAD", 1.47);
        put("AUD", 1.65);
        put("CNY", 7.82);
        put("CZK", 25.2);
        put("EUR", 1.0);
    }};

    /**
     * Vrne tečaj valute glede na EUR (koliko enot valute je 1 EUR).
     * Npr. getRate("USD") -> 1.08 pomeni 1 EUR = 1.08 USD.
     * Uporablja cache in fallback.
     */
    public static double getRate(String currency) {
        if (currency == null || currency.equalsIgnoreCase("EUR")) return 1.0;

        // Vrni iz cache-a če je svež
        long now = System.currentTimeMillis();
        if (!rateCache.isEmpty() && (now - cacheTimestamp) < CACHE_TTL_MS) {
            Double cached = rateCache.get(currency.toUpperCase());
            if (cached != null && cached > 0) {
                Log.d(TAG, "Cache hit: " + currency + " = " + cached);
                return cached;
            }
        }

        // Poskusi pridobiti iz API-ja
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "WalletTracker/1.0 Android");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "API napaka: " + responseCode + ", uporabim fallback");
                return getFallbackRate(currency);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            // {"amount":1,"base":"EUR","date":"...","rates":{"USD":1.08,...}}
            JSONObject json = new JSONObject(response.toString());
            JSONObject rates = json.getJSONObject("rates");

            // Posodobi celoten cache
            rateCache.clear();
            for (int i = 0; i < rates.names().length(); i++) {
                String key = rates.names().getString(i);
                rateCache.put(key, rates.getDouble(key));
            }
            rateCache.put("EUR", 1.0);
            cacheTimestamp = System.currentTimeMillis();

            Double result = rateCache.get(currency.toUpperCase());
            if (result != null && result > 0) {
                Log.d(TAG, "API: " + currency + " = " + result);
                return result;
            } else {
                Log.w(TAG, "Valuta " + currency + " ni v odgovoru, fallback");
                return getFallbackRate(currency);
            }

        } catch (Exception e) {
            Log.e(TAG, "Napaka getRate: " + e.getMessage() + ", uporabim fallback");
            return getFallbackRate(currency);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static double getFallbackRate(String currency) {
        Double fallback = FALLBACK_RATES.get(currency.toUpperCase());
        if (fallback != null) {
            Log.w(TAG, "Fallback rate za " + currency + ": " + fallback);
            return fallback;
        }
        Log.e(TAG, "Ni fallback za " + currency + ", vračam 1.0");
        return 1.0; // Skrajni fallback — ne vrni -1 da ne blokiramo shranjevanja
    }

    /**
     * Pretvori znesek iz currency v EUR.
     * Nikoli ne vrne -1 — v najslabšem primeru uporabi fallback.
     */
    public static double convertToEur(double amount, String currency) {
        if (currency == null || currency.equalsIgnoreCase("EUR")) return amount;
        double rate = getRate(currency);
        if (rate <= 0) return amount; // skrajni fallback: brez konverzije
        return amount / rate;
    }

    /** Razveljavi cache (pokliči po ročnem refreshu) */
    public static void invalidateCache() {
        rateCache.clear();
        cacheTimestamp = 0;
    }

    // === REFRESH MANAGEMENT ===

    public static int getRefreshIntervalMinutes(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_REFRESH_INTERVAL, DEFAULT_INTERVAL_MINUTES);
    }

    public static void setRefreshIntervalMinutes(Context ctx, int minutes) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_REFRESH_INTERVAL, minutes).apply();
    }

    public static long getLastRefreshTimestamp(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_REFRESH, 0);
    }

    public static void setLastRefreshNow(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply();
    }

    public static long getSecondsUntilNextRefresh(Context ctx) {
        long last = getLastRefreshTimestamp(ctx);
        if (last == 0) return 0;
        long intervalMs = getRefreshIntervalMinutes(ctx) * 60L * 1000L;
        long nextRefresh = last + intervalMs;
        long remaining = nextRefresh - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public static boolean shouldRefresh(Context ctx) {
        return getSecondsUntilNextRefresh(ctx) == 0;
    }
}
