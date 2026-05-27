package si.uni_lj.fe.tnuv.wallettracker.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import si.uni_lj.fe.tnuv.wallettracker.R;
import si.uni_lj.fe.tnuv.wallettracker.model.StockEntry;

public class StockAdapter extends RecyclerView.Adapter<StockAdapter.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(StockEntry entry);
        void onItemLongClick(StockEntry entry);
    }

    private List<StockEntry> stocks = new ArrayList<>();
    private final Context context;
    private final OnItemClickListener listener;

    public StockAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setStocks(List<StockEntry> stocks) {
        this.stocks = stocks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_stock_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockEntry s = stocks.get(position);
        holder.tvTicker.setText(s.ticker);
        holder.tvName.setText(s.name != null && !s.name.isEmpty() ? s.name : s.ticker);
        holder.tvShares.setText(String.format(Locale.getDefault(), "%.4g delnic", s.shares));
        holder.tvPrice.setText(String.format(Locale.getDefault(),
                "%.2f %s / delnica", s.currentPrice, s.currency));
        holder.tvEurValue.setText(String.format(Locale.getDefault(),
                "= %.2f EUR", s.valueInEur));
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault());
        holder.tvUpdated.setText("Pos.: " + sdf.format(new Date(s.lastUpdated)));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(s));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onItemLongClick(s);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return stocks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTicker, tvName, tvShares, tvPrice, tvEurValue, tvUpdated;
        ViewHolder(View view) {
            super(view);
            tvTicker = view.findViewById(R.id.tvStockTicker);
            tvName = view.findViewById(R.id.tvStockName);
            tvShares = view.findViewById(R.id.tvStockShares);
            tvPrice = view.findViewById(R.id.tvStockPrice);
            tvEurValue = view.findViewById(R.id.tvStockEurValue);
            tvUpdated = view.findViewById(R.id.tvStockUpdated);
        }
    }
}
