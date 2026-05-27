package si.uni_lj.fe.tnuv.wallettracker.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import si.uni_lj.fe.tnuv.wallettracker.R;
import si.uni_lj.fe.tnuv.wallettracker.database.WalletDatabase;
import si.uni_lj.fe.tnuv.wallettracker.database.WalletEntryDao;
import si.uni_lj.fe.tnuv.wallettracker.model.WalletEntry;

public class ChartActivity extends AppCompatActivity {
    private BarChart barChart;
    private TextView tvTrend;
    private WalletEntryDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        // Hide default action bar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dao = WalletDatabase.getDatabase(this).walletEntryDao();
        barChart = findViewById(R.id.barChart);
        tvTrend = findViewById(R.id.tvTrend);

        // Back button
        ImageButton btnNazaj = findViewById(R.id.btnNazaj);
        btnNazaj.setOnClickListener(v -> finish());

        // Chart styling
        barChart.getDescription().setEnabled(false);
        barChart.setBackgroundColor(Color.TRANSPARENT);
        barChart.getXAxis().setTextColor(Color.parseColor("#5C6BC0"));
        barChart.getAxisLeft().setTextColor(Color.parseColor("#5C6BC0"));
        barChart.getAxisLeft().setGridColor(Color.parseColor("#1E1E3F"));
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setTextColor(Color.WHITE);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setGridColor(Color.parseColor("#1E1E3F"));
        barChart.setDrawGridBackground(false);
        barChart.setDrawBorders(false);

        Button btn1T = findViewById(R.id.btn1T);
        Button btn1M = findViewById(R.id.btn1M);
        Button btn3M = findViewById(R.id.btn3M);
        Button btnVse = findViewById(R.id.btnVse);

        long now = System.currentTimeMillis();
        btn1T.setOnClickListener(v -> naložiGraf(now - 7L * 24 * 60 * 60 * 1000));
        btn1M.setOnClickListener(v -> naložiGraf(now - 30L * 24 * 60 * 60 * 1000));
        btn3M.setOnClickListener(v -> naložiGraf(now - 90L * 24 * 60 * 60 * 1000));
        btnVse.setOnClickListener(v -> naložiGraf(0));
        naložiGraf(0);
    }

    private void naložiGraf(long fromTimestamp) {
        new Thread(() -> {
            List<WalletEntry> entries;
            if (fromTimestamp == 0) {
                entries = dao.getAllEntries();
            } else {
                entries = dao.getEntriesFromTimestamp(fromTimestamp);
            }
            runOnUiThread(() -> {
                if (entries == null || entries.isEmpty()) {
                    tvTrend.setText("Trend: ni podatkov");
                    barChart.clear();
                    return;
                }
                List<BarEntry> barEntries = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                for (int i = 0; i < entries.size(); i++) {
                    barEntries.add(new BarEntry(i, (float) entries.get(i).valueInEur));
                    labels.add(sdf.format(new Date(entries.get(i).timestamp)));
                }
                BarDataSet dataSet = new BarDataSet(barEntries, "EUR vrednost");
                dataSet.setColor(Color.parseColor("#E94560"));
                dataSet.setValueTextColor(Color.parseColor("#8ECAE6"));
                dataSet.setValueTextSize(10f);
                BarData data = new BarData(dataSet);
                data.setBarWidth(0.7f);
                barChart.setData(data);
                barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                barChart.animateY(800);
                barChart.invalidate();

                if (entries.size() >= 2) {
                    double first = entries.get(entries.size() - 1).valueInEur;
                    double last = entries.get(0).valueInEur;
                    double diff = last - first;
                    double pct = (diff / first) * 100;
                    String sign = diff >= 0 ? "+" : "";
                    tvTrend.setText(String.format(Locale.getDefault(),
                            "Trend: %s%.2f EUR (%s%.1f%%)", sign, diff, sign, pct));
                    tvTrend.setTextColor(diff >= 0
                            ? Color.parseColor("#4CAF50")
                            : Color.parseColor("#E94560"));
                } else {
                    tvTrend.setText("Trend: premalo podatkov");
                }
            });
        }).start();
    }
}