package si.uni_lj.fe.tnuv.wallettracker.model;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "wallet_entries")
public class WalletEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String currency;
    public double amount;
    public String note;
    public long timestamp;
    public double valueInEur;

    public WalletEntry(String currency, double amount, String note, long timestamp, double valueInEur) {
        this.currency = currency;
        this.amount = amount;
        this.note = note;
        this.timestamp = timestamp;
        this.valueInEur = valueInEur;
    }
}
