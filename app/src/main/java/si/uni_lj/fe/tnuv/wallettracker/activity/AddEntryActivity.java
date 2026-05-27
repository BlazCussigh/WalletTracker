package si.uni_lj.fe.tnuv.wallettracker.activity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import si.uni_lj.fe.tnuv.wallettracker.R;
import si.uni_lj.fe.tnuv.wallettracker.api.ExchangeRateApi;
import si.uni_lj.fe.tnuv.wallettracker.database.WalletDatabase;
import si.uni_lj.fe.tnuv.wallettracker.database.WalletEntryDao;
import si.uni_lj.fe.tnuv.wallettracker.model.WalletEntry;

public class AddEntryActivity extends AppCompatActivity {
    private Spinner spinnerValuta;
    private EditText etZnesek, etOpomba;
    private TextView tvStatus;
    private Button btnShrani;
    private WalletEntryDao dao;
    private WalletEntry obstojeciVnos = null;

    private static final String[] VALUTE = {"USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "CNY", "HRK", "CZK"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_entry);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dao = WalletDatabase.getDatabase(this).walletEntryDao();
        spinnerValuta = findViewById(R.id.spinnerValuta);
        etZnesek = findViewById(R.id.etZnesek);
        etOpomba = findViewById(R.id.etOpomba);
        tvStatus = findViewById(R.id.tvStatus);
        btnShrani = findViewById(R.id.btnShrani);

        ImageButton btnNazaj = findViewById(R.id.btnNazaj);
        btnNazaj.setOnClickListener(v -> finish());

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, VALUTE) {
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
                tv.setBackgroundColor(position == spinnerValuta.getSelectedItemPosition()
                        ? 0xFF6366F1 : 0xFF1C1C3A);
                return tv;
            }
        };
        spinnerValuta.setAdapter(spinnerAdapter);

        int entryId = getIntent().getIntExtra("entry_id", 0);
        if (entryId > 0) loadExistingEntry(entryId);

        btnShrani.setOnClickListener(v -> shraniVnos());
    }

    private void loadExistingEntry(int id) {
        new Thread(() -> {
            final WalletEntry vnos = dao.getEntryById(id);
            runOnUiThread(() -> {
                if (vnos != null) {
                    obstojeciVnos = vnos;
                    etZnesek.setText(String.valueOf(vnos.amount));
                    etOpomba.setText(vnos.note);
                    for (int i = 0; i < VALUTE.length; i++) {
                        if (VALUTE[i].equals(vnos.currency)) { spinnerValuta.setSelection(i); break; }
                    }
                }
            });
        }).start();
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
                        finish();
                    });
                }).start();
            });
        }).start();
    }
}