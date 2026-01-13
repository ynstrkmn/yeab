// kotlin
package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yeab.esnapp.databinding.ActivityMerchantOrdersBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.model.OrderItem
import com.yeab.esnapp.util.IntentKeys
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MerchantOrdersActivity : BaseActivity() {
    private lateinit var binding: ActivityMerchantOrdersBinding
    private lateinit var database: DatabaseReference
    private lateinit var databaseUser: DatabaseReference
    private val adapter = MerchantOrderAdapter()

    private var pageSize = 5
    private var isLoading = false

    private val allItems = mutableListOf<OrderItem>()
    private var currentPage = 1

    private val isoZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val ymd = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSpinner()
        setupButtons()

        val merchantUid = intent.getStringExtra("merchantUid")
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                Toast.makeText(this, "Merchant UID bulunamadı", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        database = FirebaseDatabase.getInstance()
            .getReference("Orders")
            .child("MerchantOrders")
            .child(merchantUid)
        databaseUser = FirebaseDatabase.getInstance()
            .getReference("MerchantsUsers")
            .child(merchantUid)

        // \[Yeni\] Tıklama dinleyicisi: OrderStatusUpdateActivity’e git
        adapter.setOnItemClickListener(object : MerchantOrderAdapter.OnItemClickListener {
            override fun onItemClick(item: OrderItem) {
                val intent = Intent(this@MerchantOrdersActivity, OrderStatusUpdateActivity::class.java).apply {
                    putExtra(IntentKeys.MERCHANT_UID, merchantUid)
                    putExtra(IntentKeys.PHONE, item.ownerPhone)
                    putExtra(IntentKeys.ORDER_ID, item.id)
                    putExtra(IntentKeys.PRODUCT_NAME, item.productName)
                }
                startActivity(intent)
            }
        })

        fetchAllProductsOnce()
    }

    private fun setupRecyclerView() {
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter
    }

    private fun setupSpinner() {
        val sizes = listOf(5, 10, 20, 50)
        binding.spinnerPageSize.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes)
        binding.spinnerPageSize.setSelection(sizes.indexOf(pageSize))
        binding.spinnerPageSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val newSize = sizes[position]
                if (newSize != pageSize) {
                    pageSize = newSize
                    currentPage = 1
                    applyPaging()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        binding.btnNextPage.setOnClickListener {
            val totalPages = totalPages()
            if (currentPage < totalPages) {
                currentPage++
                applyPaging()
            }
        }
        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                applyPaging()
            }
        }
    }

    private fun fetchAllProductsOnce() {
        if (isLoading) return
        isLoading = true
        binding.progress.visibility = View.VISIBLE
        adapter.clear()
        allItems.clear()
        currentPage = 1

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val temp = mutableListOf<OrderItem>()
                        for (orderSnap in snapshot.children) {
                            val productNodes = orderSnap.children
                                .filter { it.hasChild("productName") || it.hasChild("name") }
                                .toList()
                            for (productNode in productNodes) {
                                val items = mapProductNode(
                                    orderId = productNode.key.orEmpty(),
                                    orderSnap = orderSnap,
                                    productNode = productNode
                                )
                                if (items.isNotEmpty()) temp.add(items.first())
                            }
                        }
                        allItems.addAll(temp)

                        allItems.sortWith { a, b ->
                            val da = parseDate(a.createdDate)
                            val db = parseDate(b.createdDate)
                            when {
                                da == null && db == null -> 0
                                da == null -> 1
                                db == null -> -1
                                else -> db.compareTo(da)
                            }
                        }

                        applyPaging()
                        isLoading = false
                        binding.progress.visibility = View.GONE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                binding.progress.visibility = View.GONE
                Toast.makeText(this@MerchantOrdersActivity, "Veri alınamadı: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun applyPaging() {
        val totalPages = totalPages()
        if (currentPage > totalPages) currentPage = totalPages
        val from = (currentPage - 1) * pageSize
        val toExclusive = minOf(from + pageSize, allItems.size)
        val pageItems = if (from in 0 until toExclusive) allItems.subList(from, toExclusive) else emptyList()

        adapter.setData(pageItems)

        binding.btnPrevPage.isEnabled = currentPage > 1
        binding.btnNextPage.isEnabled = currentPage < totalPages
        binding.txtPageInfo.text = "Sayfa $currentPage / $totalPages"
    }

    private fun totalPages(): Int {
        if (allItems.isEmpty()) return 1
        val pages = (allItems.size + pageSize - 1) / pageSize
        return if (pages < 1) 1 else pages
    }

    private suspend fun mapProductNode(
        orderId: String,
        orderSnap: DataSnapshot,
        productNode: DataSnapshot
    ): List<OrderItem> {
        val phone = orderSnap.key.orEmpty()

        val (ownerName, ownerSurname) = if (phone.isNotBlank()) {
            fetchMerchantUserByPhone(phone)
        } else {
            Pair("", "")
        }

        val imageUrl = productNode.child("productImageUrl").getValue(String::class.java)
            ?: productNode.child("imageUrl").getValue(String::class.java)
            ?: ""
        val productName = productNode.child("productName").getValue(String::class.java)
            ?: productNode.child("name")?.getValue(String::class.java)
            ?: ""

        val lastStatus = productNode.child("productStatus").children.lastOrNull()
            ?.child("status")?.getValue(String::class.java).orEmpty()

        val createdDate = productNode.child("createdDate").getValue(String::class.java)
            ?: orderSnap.child("createdDate").getValue(String::class.java)

        // id: "$orderId-$productKey" formatı kullanıldığı için parçalayıp productKey’i intent’e geçiyoruz
        return listOf(
            OrderItem(
                id = orderId,
                imageUrl = imageUrl,
                ownerName = ownerName,
                ownerSurname = ownerSurname,
                ownerPhone = phone,
                productName = productName,
                lastStatus = lastStatus,
                createdDate = createdDate
            )
        )
    }

    private fun parseDate(s: String?): Date? {
        if (s.isNullOrBlank()) return null
        return try { isoZ.parse(s) } catch (_: Exception) {
            try { iso.parse(s) } catch (_: Exception) {
                try { ymd.parse(s) } catch (_: Exception) { null }
            }
        }
    }

    private fun splitId(id: String): Pair<String, String> {
        val idx = id.indexOf('-')
        return if (idx > 0) {
            id.substring(0, idx) to id.substring(idx + 1)
        } else {
            id to ""
        }
    }

    private suspend fun fetchMerchantUserByPhone(phone: String): Pair<String, String> =
        suspendCoroutine { continuation ->
            databaseUser.orderByKey().equalTo(phone)
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
}
