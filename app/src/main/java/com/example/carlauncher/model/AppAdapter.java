package com.example.carlauncher.model;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carlauncher.databinding.ItemAppBinding;

import java.util.List;

public final class AppAdapter
        extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    private final List<AppInfo> items;
    private final OnAppClickListener listener;

    public AppAdapter(
            List<AppInfo> items,
            OnAppClickListener listener
    ) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater =
                LayoutInflater.from(parent.getContext());

        ItemAppBinding binding =
                ItemAppBinding.inflate(
                        inflater,
                        parent,
                        false
                );

        return new AppViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull AppViewHolder holder,
            int position
    ) {
        AppInfo item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class AppViewHolder
            extends RecyclerView.ViewHolder {

        private final ItemAppBinding binding;

        AppViewHolder(ItemAppBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                AppInfo item,
                OnAppClickListener listener
        ) {
            binding.appName.setText(item.getName());
            binding.packageName.setText(item.getPackageName());
            binding.appIcon.setImageDrawable(item.getIcon());

            binding.getRoot().setOnClickListener(view ->
                    listener.onAppClick(item)
            );
        }
    }
}