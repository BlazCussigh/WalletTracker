package si.uni_lj.fe.tnuv.wallettracker.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;
import si.uni_lj.fe.tnuv.wallettracker.model.StockEntry;

@Dao
public interface StockEntryDao {
    @Insert
    long insert(StockEntry entry);
    @Update
    void update(StockEntry entry);
    @Delete
    void delete(StockEntry entry);
    @Query("SELECT * FROM stock_entries ORDER BY ticker ASC")
    List<StockEntry> getAllStocks();
    @Query("SELECT * FROM stock_entries WHERE id = :id LIMIT 1")
    StockEntry getById(int id);
    @Query("SELECT * FROM stock_entries WHERE ticker = :ticker LIMIT 1")
    StockEntry getByTicker(String ticker);
    @Query("SELECT SUM(valueInEur) FROM stock_entries")
    double getTotalValueInEur();
    @Query("DELETE FROM stock_entries WHERE id = :id")
    void deleteById(int id);
}
