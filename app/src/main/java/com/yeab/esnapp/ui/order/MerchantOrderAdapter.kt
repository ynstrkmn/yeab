package com.yeab.esnapp.ui.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yeab.esnapp.databinding.ItemMerchantOrderBinding
import com.yeab.esnapp.ui.model.OrderItem

class MerchantOrderAdapter : RecyclerView.Adapter<MerchantOrderAdapter.VH>() {
    private val items = mutableListOf<OrderItem>()

    fun setData(list: List<OrderItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun getLastKey(): String? = items.lastOrNull()?.id

    // Eksik olan metot: orderId'ye göre sahip bilgilerini günceller
    fun updateOwnerFieldsById(
        orderId: String,
        ownerName: String,
        ownerSurname: String,
        ownerPhone: String
    ) {
        val index = items.indexOfFirst { it.id == orderId }
        if (index != -1) {
            val old = items[index]
            items[index] = old.copy(
                ownerName = ownerName,
                ownerSurname = ownerSurname,
                ownerPhone = ownerPhone
            )
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMerchantOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val b: ItemMerchantOrderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: OrderItem) {
            Glide.with(b.root).load(item.imageUrl).into(b.imgProduct)
            b.txtProduct.text = item.productName
            b.txtStatus.text = item.lastStatus
            b.txtOwner.text = buildString {
                append(item.ownerName)
                if (item.ownerSurname.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(item.ownerSurname)
                }
                if (item.ownerPhone.isNotBlank()) {
                    append(" • ")
                    append(item.ownerPhone)
                }
            }
        }
    }
}
