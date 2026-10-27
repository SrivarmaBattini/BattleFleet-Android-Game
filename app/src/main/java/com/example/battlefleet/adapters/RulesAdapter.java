package com.example.battlefleet.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.battlefleet.R;

import java.util.List;


public class RulesAdapter extends ArrayAdapter<RulesAdapter.RuleItem> {

    public static class RuleItem {
        public final String title;
        public final String description;

        public RuleItem(String title, String description) {
            this.title = title;
            this.description = description;
        }

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }

    public RulesAdapter(@NonNull Context context, @NonNull List<RuleItem> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_rule_list, parent, false);
        }

        RuleItem item = getItem(position);
        if (item != null) {
            TextView tvTitle = convertView.findViewById(R.id.tv_rule_title);
            TextView tvBody = convertView.findViewById(R.id.tv_rule_body);
            tvTitle.setText(item.title);
            tvBody.setText(item.description);
        }

        return convertView;
    }
}
