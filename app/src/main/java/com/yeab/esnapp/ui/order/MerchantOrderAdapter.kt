// kotlin
package com.yeab.esnapp.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yeab.esnapp.databinding.ItemMerchantOrderBinding
import com.yeab.esnapp.ui.model.OrderItem

class MerchantOrderAdapter : RecyclerView.Adapter<MerchantOrderAdapter.VH>() {

    interface OnItemClickListener {
        fun onItemClick(item: OrderItem)
    }

    private val items = mutableListOf<OrderItem>()
    private var clickListener: OnItemClickListener? = null

    fun setData(list: List<OrderItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMerchantOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            clickListener?.onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemMerchantOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OrderItem) {
            binding.txtProduct.text = item.productName
            binding.txtOwner.text = "${item.ownerName} ${item.ownerSurname} • ${item.ownerPhone}"
            binding.txtStatus.text = item.lastStatus
            // \[Opsiyonel\] Görsel yükleme: Glide/Picasso vb.
             Glide.with(binding.imgProduct).load(item.imageUrl).into(binding.imgProduct)
        }
    }
}
