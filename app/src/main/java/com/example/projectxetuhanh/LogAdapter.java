package com.example.projectxetuhanh;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<String> logs = new ArrayList<>();
    private static final int MAX_LOG_ENTRIES = 10;

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView logText;

        LogViewHolder(View itemView) {
            super(itemView);
            logText = (TextView) itemView;
        }
    }

    @Override
    public LogViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(LogViewHolder holder, int position) {
        holder.logText.setText(logs.get(position));
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void addLog(String log) {
        if (logs.size() >= MAX_LOG_ENTRIES) {
            logs.remove(0);
            notifyItemRemoved(0);
        }
        logs.add(log);
        notifyItemInserted(logs.size() - 1);
    }
}