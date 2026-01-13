package com.yeab.esnapp.ui.order

import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityOrderListBinding
import com.yeab.esnapp.databinding.ItemOrderBinding
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.model.ProductStatus
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys

data class OrderItem(
    val orderId: String,
    val order: Order
)

class OrderListActivity : BaseActivity() {

    private lateinit var binding: ActivityOrderListBinding
    private val dbRef = FirebaseDatabase.getInstance().reference

    private var merchantUid: String? = null
    private lateinit var phone: String

    private val orders = mutableListOf<OrderItem>()
    private lateinit var adapter: OrdersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOrderListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
        phone = intent.getStringExtra(IntentKeys.PHONE) ?: ""

        adapter = OrdersAdapter(orders, phone, { orderItem ->
            openUpdateScreen(orderItem)
        }, { orderItem -> showImageDialog(orderItem.order.productImageUrl)})

        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter

        loadOrders()
    }

    private fun loadOrders() {
        val uid = merchantUid ?: return

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .child(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    orders.clear()
                    if (!snapshot.exists()) {
                        Toast.makeText(
                            this@OrderListActivity,
                            getString(R.string.order_list_empty),
                            Toast.LENGTH_SHORT
                        ).show()
                        adapter.notifyDataSetChanged()
                        return
                    }
                    for (child in snapshot.children) {
                        val order = child.getValue(Order::class.java) ?: continue
                        val id = child.key ?: continue
                        orders.add(OrderItem(id, order))
                    }
                    orders.sortByDescending { it.order.createdDate }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun openUpdateScreen(orderItem: OrderItem) {
        val i = Intent(this, OrderStatusUpdateActivity::class.java)
        i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
        i.putExtra(IntentKeys.PHONE, phone)
        i.putExtra(IntentKeys.ORDER_ID, orderItem.orderId)
        i.putExtra(IntentKeys.PRODUCT_NAME, orderItem.order.productName)
        startActivity(i)
        finish()
    }

    private fun showImageDialog(imageUrl: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_image_preview)
        val imageView = dialog.findViewById<ImageView>(R.id.imgPreview)
        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .into(imageView)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}

class OrdersAdapter(
    private val list: List<OrderItem>,
    private val phone: String,
    private val onClick: (OrderItem) -> Unit,
    private val onPictureClick: (OrderItem) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {

    class OrderViewHolder(val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemOrderBinding.inflate(inflater, parent, false)
        return OrderViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val item = list[position]
        val ctx = holder.itemView.context

        holder.binding.txtProductName.text = item.order.productName

        val statuses = item.order.productStatus
        val lastStatus =
            if (!statuses.isNullOrEmpty()) statuses.last().status
            else ctx.getString(R.string.order_item_last_status_unknown)

        holder.binding.txtLastStatus.text =
            ctx.getString(R.string.order_item_status_label) + " " + lastStatus

        val imageUrl = item.order.productImageUrl
        val progress = holder.binding.imgLoading

        holder.binding.txtDate.text = item.order.createdDate
        holder.binding.txtPhone.text = phone

        progress.visibility = View.VISIBLE

        if (!imageUrl.isNullOrEmpty()) {

            Glide.with(ctx)
                .load(imageUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_menu_report_image)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        progress.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        progress.visibility = View.GONE
                        return false
                    }
                })
                .into(holder.binding.imgProduct)

        } else {
            progress.visibility = View.GONE
            holder.binding.imgProduct.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }

        holder.binding.imgProduct.setOnClickListener {
            onPictureClick(item)
        }
    }


}
