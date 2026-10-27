package com.example.battlefleet.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.battlefleet.R;
import com.example.battlefleet.models.MatchRecord;

import java.util.ArrayList;
import java.util.List;


public class MatchHistoryAdapter extends RecyclerView.Adapter<MatchHistoryAdapter.ViewHolder> {

    public interface OnMatchClickListener {
        void onMatchClick(MatchRecord record);
    }

    private final List<MatchRecord> items;
    private final OnMatchClickListener listener;

    public MatchHistoryAdapter(OnMatchClickListener listener) {
        this.items = new ArrayList<>();
        this.listener = listener;
    }

    public void setMatches(List<MatchRecord> newMatches) {
        this.items.clear();
        if (newMatches != null) {
            this.items.addAll(newMatches);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_match_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MatchRecord record = items.get(position);
        holder.bind(record, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvOpponent;
        private final TextView tvResultBadge;
        private final TextView tvRoomCode;
        private final TextView tvDuration;
        private final TextView tvAccuracy;
        private final TextView tvCompletedAt;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOpponent = itemView.findViewById(R.id.tv_opponent_name);
            tvResultBadge = itemView.findViewById(R.id.tv_result_badge);
            tvRoomCode = itemView.findViewById(R.id.tv_room_code);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvAccuracy = itemView.findViewById(R.id.tv_accuracy);
            tvCompletedAt = itemView.findViewById(R.id.tv_completed_at);
        }

        public void bind(final MatchRecord record, final OnMatchClickListener listener) {
            tvOpponent.setText("VS " + record.getOpponentName());
            tvRoomCode.setText("Room: #" + (record.getRoomCode() != null ? record.getRoomCode() : "N/A"));
            tvDuration.setText("Duration: " + record.getFormattedDuration());
            tvAccuracy.setText(String.format("Accuracy: %.1f%% (%d/%d)",
                    record.getAccuracy(), record.getHits(), record.getShotsFired()));
            tvCompletedAt.setText(record.getCompletedAt());

            if (record.isVictory()) {
                tvResultBadge.setText("VICTORY");
                tvResultBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ship_deployed));
                tvResultBadge.setBackgroundResource(R.drawable.badge_turn_your);
            } else {
                tvResultBadge.setText("DEFEAT");
                tvResultBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.hit_red));
                tvResultBadge.setBackgroundResource(R.drawable.badge_turn_opponent);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMatchClick(record);
                }
            });
        }
    }
}
