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
import si.uni_lj.fe.tnuv.wallettracker.model.WalletEntry;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(WalletEntry entry);
        void onItemLongClick(WalletEntry entry);
    }

    private List<WalletEntry> entries = new ArrayList<>();
    private final Context context;
    private final OnItemClickListener listener;

    public WalletAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setEntries(List<WalletEntry> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_wallet_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WalletEntry entry = entries.get(position);
        holder.tvCurrency.setText(entry.currency);
        holder.tvAmount.setText(String.format(Locale.getDefault(), "%.2f %s", entry.amount, entry.currency));
        holder.tvEurValue.setText(String.format(Locale.getDefault(), "= %.2f EUR", entry.valueInEur));
        if (entry.note != null && !entry.note.isEmpty()) {
            holder.tvNote.setText(entry.note);
            holder.tvNote.setVisibility(View.VISIBLE);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(entry.timestamp)));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(entry));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onItemLongClick(entry);
            return true;
        });
    }

    @Override
    public int getItemCount() { return entries.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCurrency, tvAmount, tvEurValue, tvNote, tvDate;
        ViewHolder(View view) {
            super(view);
            tvCurrency = view.findViewById(R.id.tvCurrency);
            tvAmount = view.findViewById(R.id.tvAmount);
            tvEurValue = view.findViewById(R.id.tvEurValue);
            tvNote = view.findViewById(R.id.tvNote);
            tvDate = view.findViewById(R.id.tvDate);
        }
    }
}
