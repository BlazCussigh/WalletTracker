package si.uni_lj.fe.tnuv.wallettracker.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stock_entries")
public class StockEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String ticker;
    public String name;
    public double shares;
    public double currentPrice;
    public String currency;
    public double valueInEur;
    public long lastUpdated;

    public StockEntry(String ticker, String name, double shares,
                      double currentPrice, String currency, double valueInEur, long lastUpdated) {
        this.ticker = ticker;
        this.name = name;
        this.shares = shares;
        this.currentPrice = currentPrice;
        this.currency = currency;
        this.valueInEur = valueInEur;
        this.lastUpdated = lastUpdated;
    }
}
