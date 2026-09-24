package com.example.carlauncher.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carlauncher.databinding.ItemAppBinding

class AppAdapter(
    private val items: List<AppInfo?>?,
    private val listener: OnAppClickListener?,
) : RecyclerView.Adapter<AppAdapter.AppViewHolder?>() {

    fun interface OnAppClickListener {
        fun onAppClick(app: AppInfo?)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AppViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())

        val binding =
            ItemAppBinding.inflate(
                inflater!!,
                parent,
                false,
            )

        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AppViewHolder,
        position: Int,
    ) {
        val item = items!!.get(position)
        holder.bind(item, listener)
    }

    override fun getItemCount(): Int = items!!.size

    class AppViewHolder(private val binding: ItemAppBinding?) :
        RecyclerView.ViewHolder(binding!!.getRoot()) {

        fun bind(
            item: AppInfo?,
            listener: OnAppClickListener?,
        ) {
            binding!!.appName.setText(item!!.name)
            binding!!.packageName.setText(item!!.packageName)
            binding!!.appIcon.setImageDrawable(item!!.icon)

            binding!!.getRoot().setOnClickListener { listener!!.onAppClick(item) }
        }
    }
}
