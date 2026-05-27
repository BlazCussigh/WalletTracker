package si.uni_lj.fe.tnuv.wallettracker.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import si.uni_lj.fe.tnuv.wallettracker.R;
import si.uni_lj.fe.tnuv.wallettracker.adapter.StockAdapter;
import si.uni_lj.fe.tnuv.wallettracker.adapter.WalletAdapter;
import si.uni_lj.fe.tnuv.wallettracker.api.ExchangeRateApi;
import si.uni_lj.fe.tnuv.wallettracker.api.StockApi;
import si.uni_lj.fe.tnuv.wallettracker.database.StockEntryDao;
import si.uni_lj.fe.tnuv.wallettracker.database.WalletDatabase;
import si.uni_lj.fe.tnuv.wallettracker.database.WalletEntryDao;
import si.uni_lj.fe.tnuv.wallettracker.model.StockEntry;
import si.uni_lj.fe.tnuv.wallettracker.model.WalletEntry;

public class DashboardActivity extends AppCompatActivity
        implements WalletAdapter.OnItemClickListener {

    private View viewHome, viewDodaj, viewDelnice, viewZgodovina, viewNastavitve;
    private TextView tvTotalEur, tvRefreshCounter, tvWalletSubtotal, tvStocksSubtotal;
    private RecyclerView recyclerView, recyclerViewStocksHome;
    private WalletAdapter adapter;
    private StockAdapter stockAdapterHome;
    private Spinner spinnerValuta;
    private EditText etZnesek, etOpomba;
    private TextView tvStatus;
    private Button btnShrani;
    private WalletEntry obstojeciVnos = null;
    private static final String[] VALUTE = {"USD","EUR","GBP","JPY","CHF","CAD","AUD","CNY","CZK"};
    // Display currencies (more options including base EUR)
    private static final String[] DISPLAY_VALUTE = {"EUR","USD","GBP","JPY","CHF","CAD","AUD","CNY","CZK"};

    private static final String PREFS_NAME = "wallet_prefs";
    private static final String KEY_DISPLAY_CURRENCY = "display_currency";

    private String displayCurrency = "EUR";
    private Spinner spinnerDisplayCurrency;
    private boolean fabExpanded = false;
    private View fabOverlay;
    private View fabValutaRow, fabDelnicaRow;

    private EditText etTicker, etShares;
    private TextView tvStockStatus, tvPortfolioTotal;
    private Button btnDodajDelnico;
    private RecyclerView recyclerViewStocksMain;
    private StockAdapter stockAdapterMain;
    private StockEntry editingStock = null;
    private BarChart barChart;
    private LineChart lineChart;
    private TextView tvTrend;
    private boolean showLineChart = false;
    private long currentChartFilter = 0;
    private WalletEntryDao dao;
    private StockEntryDao stockDao;
    private Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    // Track which section we came from, for dodaj back behaviour
    private View previousSection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Load saved display currency
        displayCurrency = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DISPLAY_CURRENCY, "EUR");

        WalletDatabase db = WalletDatabase.getDatabase(this);
        dao = db.walletEntryDao();
        stockDao = db.stockEntryDao();

        viewHome = findViewById(R.id.sectionHome);
        viewDodaj = findViewById(R.id.sectionDodaj);
        viewDelnice = findViewById(R.id.sectionDelnice);
        viewZgodovina = findViewById(R.id.sectionZgodovina);
        viewNastavitve = findViewById(R.id.sectionNastavitve);

        setupHome();
        setupDodaj();
        setupDelnice();
        setupZgodovina();
        setupNastavitve();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        prikaziSekcijo(viewHome);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                closeFab();
                prikaziSekcijo(viewHome);
                naložiPodatke();
                return true;
            } else if (id == R.id.nav_zgodovina) {
                closeFab();
                prikaziSekcijo(viewZgodovina);
                naložiGraf(currentChartFilter);
                return true;
            } else if (id == R.id.nav_nastavitve) {
                closeFab();
                prikaziSekcijo(viewNastavitve);
                return true;
            }
            return false;
        });

        // Expanding FAB setup
        fabOverlay = findViewById(R.id.fabOverlay);
        fabValutaRow = findViewById(R.id.fabValutaRow);
        fabDelnicaRow = findViewById(R.id.fabDelnicaRow);
        FloatingActionButton fab = findViewById(R.id.fabDodaj);

        fab.setOnClickListener(v -> {
            if (fabExpanded) {
                closeFab();
            } else {
                openFab();
            }
        });

        fabOverlay.setOnClickListener(v -> closeFab());

        findViewById(R.id.fabValuta).setOnClickListener(v -> {
            closeFab();
            obstojeciVnos = null;
            resetForma();
            prikaziSekcijo(viewDodaj);
        });

        findViewById(R.id.fabDelnica).setOnClickListener(v -> {
            closeFab();
            prikaziSekcijo(viewDelnice);
            naložiDelnice();
            if (etTicker != null) etTicker.requestFocus();
        });

        naložiPodatke();
        zaženiRefreshTimer();
    }

    private View getCurrentVisibleSection() {
        if (viewHome.getVisibility() == View.VISIBLE) return viewHome;
        if (viewDelnice.getVisibility() == View.VISIBLE) return viewDelnice;
        if (viewZgodovina.getVisibility() == View.VISIBLE) return viewZgodovina;
        if (viewNastavitve.getVisibility() == View.VISIBLE) return viewNastavitve;
        return viewHome;
    }

    private void openFab() {
        fabExpanded = true;
        fabOverlay.setVisibility(View.VISIBLE);
        fabValutaRow.setVisibility(View.VISIBLE);
        fabDelnicaRow.setVisibility(View.VISIBLE);
        fabValutaRow.setAlpha(0f); fabValutaRow.animate().alpha(1f).translationY(0).setDuration(180).start();
        fabDelnicaRow.setAlpha(0f); fabDelnicaRow.animate().alpha(1f).translationY(0).setDuration(180).start();
        FloatingActionButton fab = findViewById(R.id.fabDodaj);
        fab.animate().rotation(45f).setDuration(200).start();
    }

    private void closeFab() {
        fabExpanded = false;
        fabOverlay.setVisibility(View.GONE);
        fabValutaRow.setVisibility(View.GONE);
        fabDelnicaRow.setVisibility(View.GONE);
        FloatingActionButton fab = findViewById(R.id.fabDodaj);
        fab.animate().rotation(0f).setDuration(200).start();
    }

    @Override
    public void onBackPressed() {
        // Close FAB menu first if open
        if (fabExpanded) {
            closeFab();
            return;
        }
        // If dodaj or delnice section is open, go back to home
        if (viewDodaj.getVisibility() == View.VISIBLE || viewDelnice.getVisibility() == View.VISIBLE) {
            BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
            bottomNav.setSelectedItemId(R.id.nav_home);
            prikaziSekcijo(viewHome);
            naložiPodatke();
        } else {
            // If not on home, go to home instead of exiting
            if (viewHome.getVisibility() != View.VISIBLE) {
                BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
                bottomNav.setSelectedItemId(R.id.nav_home);
                prikaziSekcijo(viewHome);
                naložiPodatke();
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        posodobiVse();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void prikaziSekcijo(View sekcija) {
        viewHome.setVisibility(View.GONE);
        viewDodaj.setVisibility(View.GONE);
        viewDelnice.setVisibility(View.GONE);
        viewZgodovina.setVisibility(View.GONE);
        viewNastavitve.setVisibility(View.GONE);
        sekcija.setVisibility(View.VISIBLE);

        // FAB (+ gumb) je viden le na domači strani
        FloatingActionButton fab = findViewById(R.id.fabDodaj);
        if (fab != null) {
            boolean jeHome = (sekcija == viewHome);
            fab.setVisibility(jeHome ? View.VISIBLE : View.GONE);
            if (!jeHome && fabExpanded) {
                fabExpanded = false;
                if (fabOverlay != null) fabOverlay.setVisibility(View.GONE);
                if (fabValutaRow != null) fabValutaRow.setVisibility(View.GONE);
                if (fabDelnicaRow != null) fabDelnicaRow.setVisibility(View.GONE);
                fab.setRotation(0f);
            }
        }
    }

    private void setupHome() {
        tvTotalEur = findViewById(R.id.tvTotalEur);
        tvRefreshCounter = findViewById(R.id.tvRefreshCounter);
        tvWalletSubtotal = findViewById(R.id.tvWalletSubtotal);
        tvStocksSubtotal = findViewById(R.id.tvStocksSubtotal);

        recyclerView = findViewById(R.id.recyclerViewEntries);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WalletAdapter(this, this);
        recyclerView.setAdapter(adapter);

        recyclerViewStocksHome = findViewById(R.id.recyclerViewStocks);
        recyclerViewStocksHome.setLayoutManager(new LinearLayoutManager(this));
        stockAdapterHome = new StockAdapter(this, new StockAdapter.OnItemClickListener() {
            @Override public void onItemClick(StockEntry entry) { navigateToEditStock(entry); }
            @Override public void onItemLongClick(StockEntry entry) { showStockLongClickMenu(entry); }
        });
        recyclerViewStocksHome.setAdapter(stockAdapterHome);

        findViewById(R.id.btnRefreshNow).setOnClickListener(v -> {
            Toast.makeText(this, "Posodabljam...", Toast.LENGTH_SHORT).show();
            posodobiVse();
        });
    }

    private double convertFromEur(double eurValue) {
        if ("EUR".equals(displayCurrency)) return eurValue;
        double rate = ExchangeRateApi.getRate(displayCurrency);
        return eurValue * rate;
    }

    private void naložiPodatke() {
        new Thread(() -> {
            List<WalletEntry> entries = dao.getAllEntries();
            double walletTotalEur = dao.getTotalValueInEur();
            List<StockEntry> stocks = stockDao.getAllStocks();
            double stocksTotalEur = stockDao.getTotalValueInEur();
            double grandTotalEur = walletTotalEur + stocksTotalEur;

            double walletDisplay = convertFromEur(walletTotalEur);
            double stocksDisplay = convertFromEur(stocksTotalEur);
            double grandDisplay = convertFromEur(grandTotalEur);

            final String cur = displayCurrency;
            runOnUiThread(() -> {
                adapter.setEntries(entries);
                stockAdapterHome.setStocks(stocks);
                tvTotalEur.setText(String.format(Locale.getDefault(), "%.2f %s", grandDisplay, cur));
                tvWalletSubtotal.setText(String.format(Locale.getDefault(), "%.2f %s", walletDisplay, cur));
                tvStocksSubtotal.setText(String.format(Locale.getDefault(), "%.2f %s", stocksDisplay, cur));
            });
        }).start();
    }

    private void zaženiRefreshTimer() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                long last = ExchangeRateApi.getLastRefreshTimestamp(DashboardActivity.this);
                if (tvRefreshCounter != null) {
                    if (last == 0) {
                        tvRefreshCounter.setText("Posodabljam...");
                        tvRefreshCounter.setTextColor(Color.parseColor("#818CF8"));
                    } else {
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        tvRefreshCounter.setText("Posodobljeno ob " + sdf.format(new Date(last)));
                        tvRefreshCounter.setTextColor(Color.parseColor("#34D399"));
                    }
                }
                refreshHandler.postDelayed(this, 30000);
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void posodobiVse() {
        if (tvRefreshCounter != null) {
            tvRefreshCounter.setText("Posodabljam...");
            tvRefreshCounter.setTextColor(Color.parseColor("#818CF8"));
        }
        ExchangeRateApi.setLastRefreshNow(this);

        new Thread(() -> {
            List<WalletEntry> walletEntries = dao.getAllEntries();
            for (WalletEntry entry : walletEntries) {
                if (!entry.currency.equals("EUR")) {
                    double newEur = ExchangeRateApi.convertToEur(entry.amount, entry.currency);
                    if (newEur > 0 && newEur != entry.valueInEur) {
                        entry.valueInEur = newEur;
                        dao.update(entry);
                    }
                }
            }

            List<StockEntry> stocks = stockDao.getAllStocks();
            int posodobljenih = 0;
            for (StockEntry stock : stocks) {
                StockApi.StockQuote quote = StockApi.getQuote(stock.ticker);
                if (quote.success) {
                    double newValueEur = StockApi.calcValueInEur(stock.shares, quote.price, quote.currency);
                    if (newValueEur > 0) {
                        stock.currentPrice = quote.price;
                        stock.currency = quote.currency;
                        stock.valueInEur = newValueEur;
                        stock.lastUpdated = System.currentTimeMillis();
                        if (!stock.name.equals(quote.name) && !quote.name.isEmpty()) {
                            stock.name = quote.name;
                        }
                        stockDao.update(stock);
                        posodobljenih++;
                    }
                }
            }

            final int finalPos = posodobljenih;
            runOnUiThread(() -> {
                naložiPodatke();
                naložiDelnice();
                long last = ExchangeRateApi.getLastRefreshTimestamp(DashboardActivity.this);
                if (tvRefreshCounter != null && last > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvRefreshCounter.setText("Posodobljeno ob " + sdf.format(new Date(last)));
                    tvRefreshCounter.setTextColor(Color.parseColor("#34D399"));
                }
                if (finalPos > 0) {
                    Toast.makeText(this, "Tečaji in " + finalPos + " delnic(e) posodobljenih", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private ArrayAdapter<String> ustvariSpinnerAdapter() {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, VALUTE) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(16f);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(15f);
                int pad = (int)(14 * getResources().getDisplayMetrics().density);
                int padH = (int)(20 * getResources().getDisplayMetrics().density);
                tv.setPadding(padH, pad, padH, pad);
                tv.setBackgroundColor(spinnerValuta != null && position == spinnerValuta.getSelectedItemPosition()
                        ? 0xFF6366F1 : 0xFF1C1C3A);
                return tv;
            }
        };
    }

    private ArrayAdapter<String> ustvariDisplaySpinnerAdapter(Spinner targetSpinner) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, DISPLAY_VALUTE) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(16f);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(15f);
                int pad = (int)(14 * getResources().getDisplayMetrics().density);
                int padH = (int)(20 * getResources().getDisplayMetrics().density);
                tv.setPadding(padH, pad, padH, pad);
                tv.setBackgroundColor(targetSpinner != null && position == targetSpinner.getSelectedItemPosition()
                        ? 0xFF6366F1 : 0xFF1C1C3A);
                return tv;
            }
        };
    }

    private void setupDodaj() {
        spinnerValuta = findViewById(R.id.spinnerValuta);
        etZnesek = findViewById(R.id.etZnesek);
        etOpomba = findViewById(R.id.etOpomba);
        tvStatus = findViewById(R.id.tvStatus);
        btnShrani = findViewById(R.id.btnShrani);
        spinnerValuta.setAdapter(ustvariSpinnerAdapter());
        btnShrani.setOnClickListener(v -> shraniVnos());

        // Back button in dodaj section → go to home
        Button btnBack = findViewById(R.id.btnDodajNazaj);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
                bottomNav.setSelectedItemId(R.id.nav_home);
                prikaziSekcijo(viewHome);
                naložiPodatke();
            });
        }
    }

    private void setupNastavitve() {
        spinnerDisplayCurrency = findViewById(R.id.spinnerDisplayCurrency);
        if (spinnerDisplayCurrency == null) return;

        spinnerDisplayCurrency.setAdapter(ustvariDisplaySpinnerAdapter(spinnerDisplayCurrency));

        // Set current selection
        for (int i = 0; i < DISPLAY_VALUTE.length; i++) {
            if (DISPLAY_VALUTE[i].equals(displayCurrency)) {
                spinnerDisplayCurrency.setSelection(i);
                break;
            }
        }

        spinnerDisplayCurrency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = DISPLAY_VALUTE[position];
                if (!selected.equals(displayCurrency)) {
                    displayCurrency = selected;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putString(KEY_DISPLAY_CURRENCY, displayCurrency).apply();
                    naložiPodatke();
                    Toast.makeText(DashboardActivity.this, "Prikazna valuta: " + displayCurrency, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void resetForma() {
        if (etZnesek != null) etZnesek.setText("");
        if (etOpomba != null) etOpomba.setText("");
        if (spinnerValuta != null) spinnerValuta.setSelection(0);
        if (tvStatus != null) tvStatus.setVisibility(View.GONE);
        if (btnShrani != null) btnShrani.setEnabled(true);
    }

    private void shraniVnos() {
        String valuta = spinnerValuta.getSelectedItem().toString();
        String znesekNiz = etZnesek.getText().toString().trim();
        String opomba = etOpomba.getText().toString().trim();
        if (znesekNiz.isEmpty()) { etZnesek.setError(getString(R.string.vnesi_znesek)); return; }
        double znesek;
        try { znesek = Double.parseDouble(znesekNiz); }
        catch (NumberFormatException e) { etZnesek.setError(getString(R.string.neveljavna_vrednost)); return; }
        if (znesek <= 0) { etZnesek.setError(getString(R.string.znesek_pozitiven)); return; }

        btnShrani.setEnabled(false);
        tvStatus.setText(R.string.pridobivam_tecaj);
        tvStatus.setVisibility(View.VISIBLE);
        final double finalZnesek = znesek;

        new Thread(() -> {
            double eurVrednost = ExchangeRateApi.convertToEur(finalZnesek, valuta);
            runOnUiThread(() -> {
                if (eurVrednost < 0) { tvStatus.setText(R.string.napaka_omrezja); btnShrani.setEnabled(true); return; }
                final double finalEur = eurVrednost;
                new Thread(() -> {
                    if (obstojeciVnos != null) {
                        obstojeciVnos.currency = valuta;
                        obstojeciVnos.amount = finalZnesek;
                        obstojeciVnos.note = opomba;
                        obstojeciVnos.timestamp = System.currentTimeMillis();
                        obstojeciVnos.valueInEur = finalEur;
                        dao.update(obstojeciVnos);
                    } else {
                        dao.insert(new WalletEntry(valuta, finalZnesek, opomba, System.currentTimeMillis(), finalEur));
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, String.format(Locale.getDefault(),
                                getString(R.string.shranjeno_format), finalZnesek, valuta, finalEur), Toast.LENGTH_LONG).show();
                        ExchangeRateApi.setLastRefreshNow(this);
                        obstojeciVnos = null;
                        resetForma();
                        naložiPodatke();
                        ((BottomNavigationView) findViewById(R.id.bottomNav)).setSelectedItemId(R.id.nav_home);
                        prikaziSekcijo(viewHome);
                    });
                }).start();
            });
        }).start();
    }

    @Override
    public void onItemClick(WalletEntry entry) {
        obstojeciVnos = null;
        resetForma();
        new Thread(() -> {
            final WalletEntry vnos = dao.getEntryById(entry.id);
            runOnUiThread(() -> {
                if (vnos != null) {
                    obstojeciVnos = vnos;
                    etZnesek.setText(String.valueOf(vnos.amount));
                    etOpomba.setText(vnos.note != null ? vnos.note : "");
                    for (int i = 0; i < VALUTE.length; i++) {
                        if (VALUTE[i].equals(vnos.currency)) { spinnerValuta.setSelection(i); break; }
                    }
                }
            });
        }).start();
        prikaziSekcijo(viewDodaj);
    }

    @Override
    public void onItemLongClick(WalletEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Upravljaj vnos")
                .setMessage(entry.amount + " " + entry.currency + " ≈ "
                        + String.format(Locale.getDefault(), "%.2f EUR", entry.valueInEur))
                .setPositiveButton("✏️ Uredi", (d, w) -> onItemClick(entry))
                .setNegativeButton("🗑️ Izbriši", (d, w) ->
                        new AlertDialog.Builder(this)
                                .setTitle("Izbriši vnos?")
                                .setPositiveButton("Izbriši", (d2, w2) -> new Thread(() -> {
                                    dao.delete(entry);
                                    runOnUiThread(() -> {
                                        naložiPodatke();
                                        Toast.makeText(this, "Vnos izbrisan", Toast.LENGTH_SHORT).show();
                                    });
                                }).start())
                                .setNegativeButton("Prekliči", null).show())
                .setNeutralButton("Prekliči", null)
                .show();
    }

    private void setupDelnice() {
        etTicker = findViewById(R.id.etTicker);
        etShares = findViewById(R.id.etShares);
        tvStockStatus = findViewById(R.id.tvStockStatus);
        tvPortfolioTotal = findViewById(R.id.tvPortfolioTotal);
        btnDodajDelnico = findViewById(R.id.btnDodajDelnico);

        recyclerViewStocksMain = findViewById(R.id.recyclerViewStocksMain);
        recyclerViewStocksMain.setLayoutManager(new LinearLayoutManager(this));
        stockAdapterMain = new StockAdapter(this, new StockAdapter.OnItemClickListener() {
            @Override public void onItemClick(StockEntry entry) { navigateToEditStock(entry); }
            @Override public void onItemLongClick(StockEntry entry) { showStockLongClickMenu(entry); }
        });
        recyclerViewStocksMain.setAdapter(stockAdapterMain);

        btnDodajDelnico.setOnClickListener(v -> shraniDelnico());

        findViewById(R.id.btnRefreshStocks).setOnClickListener(v -> {
            Toast.makeText(this, "Posodabljam cene delnic...", Toast.LENGTH_SHORT).show();
            posodobiVse();
        });
    }

    private void navigateToEditStock(StockEntry entry) {
        editingStock = entry;
        etTicker.setText(entry.ticker);
        etShares.setText(String.valueOf(entry.shares));
        tvStockStatus.setVisibility(View.GONE);
        btnDodajDelnico.setText("POSODOBI DELNICO");
        prikaziSekcijo(viewDelnice);
    }

    private void showStockLongClickMenu(StockEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(entry.ticker + " — " + entry.name)
                .setMessage(String.format(Locale.getDefault(),
                        "%.4g delnic × %.2f %s\n= %.2f EUR",
                        entry.shares, entry.currentPrice, entry.currency, entry.valueInEur))
                .setPositiveButton("✏️ Uredi", (d, w) -> navigateToEditStock(entry))
                .setNegativeButton("🗑️ Izbriši", (d, w) ->
                        new AlertDialog.Builder(this)
                                .setTitle("Izbriši " + entry.ticker + "?")
                                .setPositiveButton("Izbriši", (d2, w2) -> new Thread(() -> {
                                    stockDao.delete(entry);
                                    runOnUiThread(() -> {
                                        naložiPodatke();
                                        naložiDelnice();
                                        Toast.makeText(this, entry.ticker + " izbrisan", Toast.LENGTH_SHORT).show();
                                    });
                                }).start())
                                .setNegativeButton("Prekliči", null).show())
                .setNeutralButton("Prekliči", null)
                .show();
    }

    private void naložiDelnice() {
        new Thread(() -> {
            List<StockEntry> stocks = stockDao.getAllStocks();
            double totalEur = stockDao.getTotalValueInEur();
            double totalDisplay = convertFromEur(totalEur);
            final String cur = displayCurrency;
            runOnUiThread(() -> {
                stockAdapterMain.setStocks(stocks);
                if (tvPortfolioTotal != null) {
                    tvPortfolioTotal.setText(String.format(Locale.getDefault(), "%.2f %s", totalDisplay, cur));
                }
            });
        }).start();
    }

    private void shraniDelnico() {
        String ticker = etTicker.getText().toString().trim().toUpperCase();
        String sharesStr = etShares.getText().toString().trim();

        if (ticker.isEmpty()) { etTicker.setError(getString(R.string.ticker_prazen)); return; }
        if (sharesStr.isEmpty()) { etShares.setError(getString(R.string.vnesi_znesek)); return; }
        double shares;
        try { shares = Double.parseDouble(sharesStr); }
        catch (NumberFormatException e) { etShares.setError(getString(R.string.neveljavna_vrednost)); return; }
        if (shares <= 0) { etShares.setError(getString(R.string.delnice_pozitivno)); return; }

        btnDodajDelnico.setEnabled(false);
        tvStockStatus.setText(R.string.pridobivam_ceno);
        tvStockStatus.setVisibility(View.VISIBLE);
        tvStockStatus.setTextColor(Color.parseColor("#818CF8"));

        final double finalShares = shares;
        final String finalTicker = ticker;

        new Thread(() -> {
            StockApi.StockQuote quote = StockApi.getQuote(finalTicker);
            runOnUiThread(() -> {
                if (!quote.success) {
                    tvStockStatus.setText("Napaka: " + quote.error);
                    tvStockStatus.setTextColor(Color.parseColor("#F472B6"));
                    btnDodajDelnico.setEnabled(true);
                    return;
                }

                double valueEur = StockApi.calcValueInEur(finalShares, quote.price, quote.currency);
                if (valueEur < 0) {
                    tvStockStatus.setText("Napaka pri konverziji v EUR");
                    tvStockStatus.setTextColor(Color.parseColor("#F472B6"));
                    btnDodajDelnico.setEnabled(true);
                    return;
                }

                final double finalValueEur = valueEur;
                new Thread(() -> {
                    if (editingStock != null) {
                        editingStock.ticker = finalTicker;
                        editingStock.name = quote.name;
                        editingStock.shares = finalShares;
                        editingStock.currentPrice = quote.price;
                        editingStock.currency = quote.currency;
                        editingStock.valueInEur = finalValueEur;
                        editingStock.lastUpdated = System.currentTimeMillis();
                        stockDao.update(editingStock);
                    } else {
                        StockEntry obstoječa = stockDao.getByTicker(finalTicker);
                        if (obstoječa != null) {
                            obstoječa.shares = finalShares;
                            obstoječa.currentPrice = quote.price;
                            obstoječa.currency = quote.currency;
                            obstoječa.valueInEur = finalValueEur;
                            obstoječa.lastUpdated = System.currentTimeMillis();
                            if (!quote.name.isEmpty()) obstoječa.name = quote.name;
                            stockDao.update(obstoječa);
                        } else {
                            stockDao.insert(new StockEntry(
                                    finalTicker, quote.name, finalShares,
                                    quote.price, quote.currency, finalValueEur,
                                    System.currentTimeMillis()
                            ));
                        }
                    }
                    runOnUiThread(() -> {
                        String msg = String.format(Locale.getDefault(),
                                getString(R.string.delnica_shranjena),
                                String.valueOf(finalShares), finalTicker, finalValueEur);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        editingStock = null;
                        etTicker.setText("");
                        etShares.setText("");
                        tvStockStatus.setVisibility(View.GONE);
                        btnDodajDelnico.setText("DODAJ DELNICO");
                        btnDodajDelnico.setEnabled(true);
                        naložiPodatke();
                        naložiDelnice();
                    });
                }).start();
            });
        }).start();
    }

    private void setupZgodovina() {
        barChart = findViewById(R.id.barChart);
        lineChart = findViewById(R.id.lineChart);
        tvTrend = findViewById(R.id.tvTrend);
        styleBarChart();
        styleLineChart();

        long now = System.currentTimeMillis();
        findViewById(R.id.btn1T).setOnClickListener(v -> { currentChartFilter = now - 7L*24*60*60*1000; naložiGraf(currentChartFilter); });
        findViewById(R.id.btn1M).setOnClickListener(v -> { currentChartFilter = now - 30L*24*60*60*1000; naložiGraf(currentChartFilter); });
        findViewById(R.id.btn3M).setOnClickListener(v -> { currentChartFilter = now - 90L*24*60*60*1000; naložiGraf(currentChartFilter); });
        findViewById(R.id.btnVse).setOnClickListener(v -> { currentChartFilter = 0; naložiGraf(0); });

        findViewById(R.id.btnToggleChart).setOnClickListener(v -> {
            showLineChart = !showLineChart;
            barChart.setVisibility(showLineChart ? View.GONE : View.VISIBLE);
            lineChart.setVisibility(showLineChart ? View.VISIBLE : View.GONE);
            ((Button) findViewById(R.id.btnToggleChart)).setText(showLineChart ? "📊 Stolpci" : "📈 Črta");
            naložiGraf(currentChartFilter);
        });
    }

    private void styleBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setBackgroundColor(Color.TRANSPARENT);
        barChart.getXAxis().setTextColor(Color.parseColor("#818CF8"));
        barChart.getAxisLeft().setTextColor(Color.parseColor("#818CF8"));
        barChart.getAxisLeft().setGridColor(Color.parseColor("#1A1C35"));
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setTextColor(Color.WHITE);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBorders(false);
    }

    private void styleLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.getXAxis().setTextColor(Color.parseColor("#818CF8"));
        lineChart.getAxisLeft().setTextColor(Color.parseColor("#818CF8"));
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#1A1C35"));
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setTextColor(Color.WHITE);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.setDrawGridBackground(false);
        lineChart.setDrawBorders(false);
    }

    private void naložiGraf(long fromTimestamp) {
        new Thread(() -> {
            List<WalletEntry> allEntries = dao.getAllEntries();
            if (allEntries != null) java.util.Collections.reverse(allEntries); // zdaj od najstarejšega
            double stocksTotal = stockDao.getTotalValueInEur();

            runOnUiThread(() -> {
                if (allEntries == null || allEntries.isEmpty()) {
                    tvTrend.setText("Trend: ni podatkov");
                    barChart.clear(); lineChart.clear();
                    return;
                }

                // Izračunamo vsoto denarnice PRED izbranim obdobjem (baseline)
                double baselineWallet = 0;
                List<WalletEntry> periodEntries = new ArrayList<>();
                for (WalletEntry entry : allEntries) {
                    if (fromTimestamp > 0 && entry.timestamp < fromTimestamp) {
                        baselineWallet += entry.valueInEur;
                    } else {
                        periodEntries.add(entry);
                    }
                }

                List<Double> displayTotals = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());

                // Dodamo začetno točko za filtrirane poglede
                if (fromTimestamp > 0) {
                    displayTotals.add(baselineWallet + stocksTotal);
                    labels.add(sdf.format(new Date(fromTimestamp)));
                }

                // Dodajamo vnose znotraj izbranega obdobja
                double runningWallet = baselineWallet;
                for (WalletEntry entry : periodEntries) {
                    runningWallet += entry.valueInEur;
                    displayTotals.add(runningWallet + stocksTotal);
                    labels.add(sdf.format(new Date(entry.timestamp)));
                }

                // Če ni nobenih vnosov (sploh), pokaži vsaj trenutno vrednost
                if (displayTotals.isEmpty()) {
                    displayTotals.add(stocksTotal);
                    labels.add("Danes");
                }

                // Zagotovimo vsaj dve točki za lep prikaz trenda
                if (displayTotals.size() == 1) {
                    displayTotals.add(0, displayTotals.get(0));
                    labels.add(0, labels.get(0));
                }

                final String cur = displayCurrency;

                if (!showLineChart) {
                    List<BarEntry> barEntries = new ArrayList<>();
                    for (int i = 0; i < displayTotals.size(); i++)
                        barEntries.add(new BarEntry(i, (float) convertFromEur(displayTotals.get(i))));
                    BarDataSet dataSet = new BarDataSet(barEntries, "Skupna vrednost (" + cur + ")");
                    dataSet.setColor(Color.parseColor("#6366F1"));
                    dataSet.setValueTextColor(Color.WHITE);
                    dataSet.setValueTextSize(9f);
                    BarData data = new BarData(dataSet);
                    data.setBarWidth(0.7f);
                    barChart.setData(data);
                    barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                    barChart.getXAxis().setGranularity(1f);
                    barChart.animateY(600);
                    barChart.invalidate();
                } else {
                    List<Entry> lineEntries = new ArrayList<>();
                    for (int i = 0; i < displayTotals.size(); i++)
                        lineEntries.add(new Entry(i, (float) convertFromEur(displayTotals.get(i))));
                    LineDataSet dataSet = new LineDataSet(lineEntries, "Skupna vrednost (" + cur + ")");
                    dataSet.setColor(Color.parseColor("#818CF8"));
                    dataSet.setCircleColor(Color.parseColor("#A5B4FC"));
                    dataSet.setCircleHoleColor(Color.parseColor("#0D0E1F"));
                    dataSet.setValueTextColor(Color.WHITE);
                    dataSet.setValueTextSize(9f);
                    dataSet.setLineWidth(2.5f);
                    dataSet.setCircleRadius(4f);
                    dataSet.setDrawFilled(true);
                    dataSet.setFillColor(Color.parseColor("#2A2B5E"));
                    dataSet.setFillAlpha(100);
                    dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                    LineData data = new LineData(dataSet);
                    lineChart.setData(data);
                    lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                    lineChart.getXAxis().setGranularity(1f);
                    lineChart.animateY(600);
                    lineChart.invalidate();
                }

                double firstTotal = convertFromEur(displayTotals.get(0));
                double lastTotal = convertFromEur(displayTotals.get(displayTotals.size() - 1));
                if (displayTotals.size() >= 2) {
                    double diff = lastTotal - firstTotal;
                    double pct = firstTotal > 0 ? (diff / firstTotal) * 100 : 0;
                    String sign = diff >= 0 ? "+" : "";
                    tvTrend.setText(String.format(Locale.getDefault(),
                            "Skupaj: %.2f %s  (%s%.1f%%)", lastTotal, cur, sign, pct));
                    tvTrend.setTextColor(diff >= 0 ? Color.parseColor("#34D399") : Color.parseColor("#F472B6"));
                } else {
                    tvTrend.setText(String.format(Locale.getDefault(), "Skupaj: %.2f %s", lastTotal, cur));
                    tvTrend.setTextColor(Color.parseColor("#818CF8"));
                }
            });
        }).start();
    }
}
