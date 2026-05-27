package si.uni_lj.fe.tnuv.wallettracker.database;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;
import si.uni_lj.fe.tnuv.wallettracker.model.WalletEntry;

@Dao
public interface WalletEntryDao {
    @Insert
    long insert(WalletEntry entry);
    @Update
    void update(WalletEntry entry);
    @Delete
    void delete(WalletEntry entry);
    @Query("SELECT * FROM wallet_entries ORDER BY timestamp DESC")
    List<WalletEntry> getAllEntries();
    @Query("SELECT * FROM wallet_entries WHERE currency = :currency ORDER BY timestamp ASC")
    List<WalletEntry> getEntriesByCurrency(String currency);
    @Query("SELECT SUM(valueInEur) FROM wallet_entries")
    double getTotalValueInEur();
    @Query("SELECT * FROM wallet_entries WHERE timestamp >= :fromTimestamp ORDER BY timestamp ASC")
    List<WalletEntry> getEntriesFromTimestamp(long fromTimestamp);
    @Query("SELECT * FROM wallet_entries WHERE id = :id LIMIT 1")
    WalletEntry getEntryById(int id);
}
