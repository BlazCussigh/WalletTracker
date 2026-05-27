package si.uni_lj.fe.tnuv.wallettracker.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import si.uni_lj.fe.tnuv.wallettracker.model.StockEntry;
import si.uni_lj.fe.tnuv.wallettracker.model.WalletEntry;

@Database(entities = {WalletEntry.class, StockEntry.class}, version = 2, exportSchema = false)
public abstract class WalletDatabase extends RoomDatabase {
    public abstract WalletEntryDao walletEntryDao();
    public abstract StockEntryDao stockEntryDao();

    private static volatile WalletDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `stock_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`ticker` TEXT, " +
                    "`name` TEXT, " +
                    "`shares` REAL NOT NULL, " +
                    "`currentPrice` REAL NOT NULL, " +
                    "`currency` TEXT, " +
                    "`valueInEur` REAL NOT NULL, " +
                    "`lastUpdated` INTEGER NOT NULL)");
        }
    };

    public static WalletDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (WalletDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            WalletDatabase.class,
                            "wallet_database"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
