// kotlin
package com.yeab.esnapp.ui.order

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yeab.esnapp.databinding.ActivityMerchantOrdersBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.model.OrderItem
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resumeWithException

class SearchResultsActivity : BaseActivity() {
    private lateinit var binding: ActivityMerchantOrdersBinding
    private lateinit var dbPhone: DatabaseReference
    private lateinit var dbDate: DatabaseReference
    private lateinit var dbUsers: DatabaseReference
    private val adapter = MerchantOrderAdapter()
    private val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMerchantOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spinnerPageSize.visibility = View.GONE
        binding.btnNextPage.visibility = View.GONE
        binding.btnPrevPage.visibility = View.GONE
        binding.txtPageInfo.visibility = View.GONE

        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter

        val merchantUid = intent.getStringExtra("merchantUid")
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                Toast.makeText(this, "Merchant UID bulunamadı", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        dbPhone = FirebaseDatabase.getInstance()
            .getReference("CompletedOrders")
            .child("MerchantOrders")
            .child("PhoneOrders")
            .child(merchantUid)

        dbDate = FirebaseDatabase.getInstance()
            .getReference("CompletedOrders")
            .child("MerchantOrders")
            .child("DateOrders")
            .child(merchantUid)

        dbUsers = FirebaseDatabase.getInstance()
            .getReference("MerchantsUsers")
            .child(merchantUid)

        val phone = intent.getStringExtra("phone")?.trim().orEmpty()
        val startDate = intent.getStringExtra("startDate")?.trim().orEmpty()
        val endDate = intent.getStringExtra("endDate")?.trim().orEmpty()

        when {
            phone.isNotEmpty() -> loadByPhone(phone)
            startDate.isNotEmpty() && endDate.isNotEmpty() -> loadByDateRange(startDate, endDate)
            else -> {
                Toast.makeText(this, "Geçersiz arama parametresi", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadByPhone(phone: String) {
        binding.progress.visibility = View.VISIBLE
        dbPhone.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lifecycleScope.launch {
                    val items = mutableListOf<OrderItem>()
                    for (orderSnap in snapshot.children) {
                        val orderId = orderSnap.key.orEmpty()
                        val mapped = mapProductToOrderItem(orderId, orderSnap, knownPhone = phone)
                        if (mapped != null) items.add(mapped)
                    }
                    adapter.setData(items)
                    binding.progress.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@SearchResultsActivity, "Veri alınamadı: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadByDateRange(startDate: String, endDate: String) {
        val dates = buildDateList(startDate, endDate)
        if (dates.isEmpty()) {
            Toast.makeText(this, "Tarih aralığı geçersiz", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val items = mutableListOf<OrderItem>()
                for (d in dates) {
                    val snap = readOnce(dbDate.child(d))
                    for (orderSnap in snap.children) {
                        val orderId = orderSnap.key.orEmpty()
                        val mapped = mapProductToOrderItem(orderId, orderSnap, knownPhone = null)
                        if (mapped != null) items.add(mapped)
                    }
                }
                adapter.setData(items)
            } catch (e: Exception) {
                Toast.makeText(this@SearchResultsActivity, "Veri alınamadı", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    // createdDate’i doldurur
    private suspend fun mapProductToOrderItem(
        orderId: String,
        orderSnap: DataSnapshot,
        knownPhone: String? = null
    ): OrderItem? {
        val phone = knownPhone
            ?: orderSnap.child("ownerPhone").getValue(String::class.java)
            ?: orderSnap.child("phone").getValue(String::class.java)
            ?: ""

        val (ownerName, ownerSurname) = if (phone.isNotBlank()) {
            fetchMerchantUserByPhone(phone)
        } else {
            Pair("", "")
        }

        val imageUrl = orderSnap.child("productImageUrl").getValue(String::class.java)
            ?: orderSnap.child("imageUrl").getValue(String::class.java)
            ?: ""
        val productName = orderSnap.child("productName").getValue(String::class.java)
            ?: orderSnap.child("name")?.getValue(String::class.java)
            ?: ""

        val lastStatus = orderSnap.child("productStatus").children.lastOrNull()
            ?.child("status")?.getValue(String::class.java).orEmpty()

        // createdDate: öncelik ürün düğümü, yoksa sipariş düğümü
        val createdDate =
            orderSnap.child("createdDate").getValue(String::class.java)
                ?: orderSnap.child("date").getValue(String::class.java)

        return OrderItem(
            id = "$orderId-${orderSnap.key.orEmpty()}",
            imageUrl = imageUrl,
            ownerName = ownerName,
            ownerSurname = ownerSurname,
            ownerPhone = phone,
            productName = productName,
            lastStatus = lastStatus,
            createdDate = createdDate
        )
    }

    private suspend fun fetchMerchantUserByPhone(phone: String): Pair<String, String> =
        suspendCoroutine { continuation ->
            dbUsers.orderByKey().equalTo(phone)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(ds: DataSnapshot) {
                        val first = ds.children.firstOrNull()
                        val name = first?.child("Name")?.getValue(String::class.java)
                            ?: first?.child("name")?.getValue(String::class.java) ?: ""
                        val surname = first?.child("Surname")?.getValue(String::class.java)
                            ?: first?.child("surname")?.getValue(String::class.java) ?: ""
                        continuation.resume(Pair(name, surname))
                    }
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(Pair("", ""))
                    }
                })
        }

    private suspend fun readOnce(ref: DatabaseReference): DataSnapshot =
        suspendCoroutine { cont ->
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = cont.resume(snapshot)
                override fun onCancelled(error: DatabaseError) = cont.resumeWithException(error.toException())
            })
        }

    private fun buildDateList(startDate: String, endDate: String): List<String> {
        val start = parse(startDate) ?: return emptyList()
        val end = parse(endDate) ?: return emptyList()
        if (start.after(end)) return emptyList()

        val out = mutableListOf<String>()
        val cal = Calendar.getInstance().apply { time = start.time }
        val endCal = Calendar.getInstance().apply { time = end.time }

        while (!cal.after(endCal)) {
            out.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    private fun parse(s: String): Calendar? = try {
        Calendar.getInstance().apply { time = sdf.parse(s)!! }
    } catch (_: ParseException) { null }
}
