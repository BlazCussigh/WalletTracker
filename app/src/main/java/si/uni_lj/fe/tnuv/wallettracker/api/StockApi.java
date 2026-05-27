package si.uni_lj.fe.tnuv.wallettracker.api;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StockApi {
    private static final String TAG = "StockApi";
    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    public static class StockQuote {
        public String symbol;
        public String name;
        public double price;
        public String currency;
        public boolean success;
        public String error;

        public static StockQuote error(String msg) {
            StockQuote q = new StockQuote();
            q.success = false;
            q.error = msg;
            return q;
        }
    }

    public static StockQuote getQuote(String ticker) {
        HttpURLConnection conn = null;
        try {
            String urlStr = BASE_URL + ticker.toUpperCase().trim()
                    + "?interval=1d&range=1d&includePrePost=false";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return StockQuote.error("HTTP napaka: " + code);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject chart = root.getJSONObject("chart");

            if (!chart.isNull("error") && !chart.get("error").toString().equals("null")) {
                JSONObject err = chart.getJSONObject("error");
                return StockQuote.error(err.optString("description", "Napaka Yahoo API"));
            }

            JSONArray results = chart.getJSONArray("result");
            if (results.length() == 0) {
                return StockQuote.error("Ticker '" + ticker + "' ni najden");
            }

            JSONObject result = results.getJSONObject(0);
            JSONObject meta = result.getJSONObject("meta");

            StockQuote quote = new StockQuote();
            quote.success = true;
            quote.symbol = meta.optString("symbol", ticker.toUpperCase());
            quote.price = meta.optDouble("regularMarketPrice", -1);
            quote.currency = meta.optString("currency", "USD");

            quote.name = meta.optString("longName", "");
            if (quote.name.isEmpty()) {
                quote.name = meta.optString("shortName", quote.symbol);
            }

            if (quote.price <= 0) {
                return StockQuote.error("Cena ni na voljo za " + ticker);
            }

            Log.d(TAG, "Quote: " + quote.symbol + " = " + quote.price + " " + quote.currency);
            return quote;

        } catch (Exception e) {
            Log.e(TAG, "Napaka: " + e.getMessage());
            return StockQuote.error("Omrežna napaka: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Izračuna vrednost v EUR. Nikoli ne vrne -1 — ExchangeRateApi ima fallback.
     */
    public static double calcValueInEur(double shares, double price, String currency) {
        if (currency == null || currency.equalsIgnoreCase("EUR")) {
            return shares * price;
        }
        // getRate() ima vgrajene fallback tečaje, ne vrne <= 0
        double eurRate = ExchangeRateApi.getRate(currency);
        if (eurRate <= 0) eurRate = 1.08; // skrajni USD fallback
        double priceInEur = price / eurRate;
        return shares * priceInEur;
    }
}
