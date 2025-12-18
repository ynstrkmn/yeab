// Kotlin
package com.yeab.esnapp.ui.order

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
import kotlinx.coroutines.launch
import kotlin.compareTo
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.inc
import kotlin.text.clear
import kotlin.text.compareTo

class MerchantOrdersActivity : BaseActivity() {
    private lateinit var binding: ActivityMerchantOrdersBinding
    private lateinit var database: DatabaseReference
    private lateinit var databaseUser: DatabaseReference
    private val adapter = MerchantOrderAdapter()

    private var pageSize = 5
    private var isLoading = false

    private data class ProductCursor(val orderKey: String?, val productKey: String?)
    private var currentCursor: ProductCursor = ProductCursor(orderKey = null, productKey = null)
    private val cursorHistory = mutableListOf<ProductCursor>()

    private var canGoNext = false
    private var canGoPrev = false

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

        loadInitialPage()
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
                    loadInitialPage()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        binding.btnNextPage.setOnClickListener { loadNextPage() }
        binding.btnPrevPage.setOnClickListener { loadPrevPage() }
    }

    private fun loadInitialPage() {
        isLoading = false
        adapter.clear()
        cursorHistory.clear()
        currentCursor = ProductCursor(orderKey = null, productKey = null)

        // Başlangıçta: Önceki kapalı, Sonraki açık
        canGoPrev = false
        canGoNext = true

        loadPage(direction = PageDirection.INITIAL)
    }

    private fun loadNextPage() {
        if (canGoNext) {
            loadPage(direction = PageDirection.NEXT)
        }
    }

    private fun loadPrevPage() {
        if (!canGoPrev) return

        // Bir önceki cursor'a dön
        if (cursorHistory.size > 1) {
            cursorHistory.removeAt(cursorHistory.lastIndex)
            currentCursor = cursorHistory.last()

            // Artık en baştaki sayfaya döndüysek, ilk sayfayı tam doldur
            if (cursorHistory.size == 1) {
                loadInitialPage()
                return
            }

            loadPage(direction = PageDirection.PREVIOUS)
        }
    }

    private fun loadPage(direction: PageDirection) {
        if (isLoading) return
        isLoading = true
        binding.progress.visibility = View.VISIBLE

        val startOrderKey = if (direction == PageDirection.NEXT || direction == PageDirection.PREVIOUS)
            currentCursor.orderKey else null

        val query: Query = when (direction) {
            PageDirection.INITIAL -> database.orderByKey().limitToFirst(pageSize)
            PageDirection.NEXT -> {
                if (startOrderKey.isNullOrEmpty()) {
                    database.orderByKey().limitToFirst(pageSize)
                } else {
                    database.orderByKey().startAt(startOrderKey).limitToFirst(pageSize + 1)
                }
            }
            PageDirection.PREVIOUS -> {
                if (startOrderKey.isNullOrEmpty()) {
                    database.orderByKey().limitToFirst(pageSize)
                } else {
                    database.orderByKey().endAt(startOrderKey).limitToLast(pageSize + 1)
                }
            }
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var orders = snapshot.children.toList()

                if (direction == PageDirection.NEXT &&
                    !startOrderKey.isNullOrEmpty() &&
                    orders.isNotEmpty() &&
                    orders.first().key == startOrderKey
                ) {
                    orders = orders.drop(1)
                }

                if (direction == PageDirection.PREVIOUS &&
                    !startOrderKey.isNullOrEmpty() &&
                    orders.isNotEmpty() &&
                    orders.last().key == startOrderKey
                ) {
                    orders = orders.dropLast(1).reversed()
                }

                if (orders.isEmpty()) {
                    handleEmptyResult(direction)
                    return
                }

                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val pageItems = mutableListOf<OrderItem>()
                        var collected = 0

                        var newCursorOrderKey: String? = null
                        var newCursorProductKey: String? = null

                        for (orderSnap in orders) {
                            if (collected >= pageSize) break

                            // Gerekirse filtreyi gevşetin: sadece gerçek ürün düğümlerini hedefleyin.
                            val productNodes = orderSnap.children
                                .filter { it.hasChild("productName") || it.hasChild("name") }
                                .toList()

                            val sequence: List<DataSnapshot> = when (direction) {
                                PageDirection.NEXT -> {
                                    val start = if (currentCursor.orderKey == orderSnap.key && currentCursor.productKey != null) {
                                        val idx = productNodes.indexOfFirst { it.key == currentCursor.productKey }
                                        if (idx == -1) 0 else (idx + 1)
                                    } else 0
                                    productNodes.drop(start)
                                }
                                PageDirection.PREVIOUS -> {
                                    // Çapa siparişinde current ürünün öncekilerine kadar al; diğer siparişlerde tüm ürünleri tersten gez
                                    val endExclusive = if (currentCursor.orderKey == orderSnap.key && currentCursor.productKey != null) {
                                        val idx = productNodes.indexOfFirst { it.key == currentCursor.productKey }
                                        if (idx == -1) productNodes.size else idx
                                    } else productNodes.size
                                    productNodes.take(endExclusive).asReversed()
                                }
                                PageDirection.INITIAL -> productNodes
                            }

                            for (productNode in sequence) {
                                if (collected >= pageSize) break

                                val items = mapProductsOfOrder(
                                    orderId = orderSnap.key.orEmpty(),
                                    orderSnap = orderSnap,
                                    onlyThisProduct = productNode
                                )

                                if (items.isNotEmpty()) {
                                    pageItems.add(items.first())
                                    collected++
                                    newCursorOrderKey = orderSnap.key
                                    newCursorProductKey = productNode.key
                                }
                            }
                        }

                        if (pageItems.isEmpty()) {
                            handleEmptyResult(direction)
                            return@repeatOnLifecycle
                        }

                        currentCursor = ProductCursor(newCursorOrderKey, newCursorProductKey)
                        if (direction == PageDirection.INITIAL || direction == PageDirection.NEXT) {
                            if (direction == PageDirection.INITIAL) cursorHistory.clear()
                            cursorHistory.add(currentCursor)
                        }

                        adapter.setData(pageItems)

                        // Kalıcı buton mantığı:
                        // - NEXT'te sayfa eksik dolarsa (collected < pageSize) son sayfadayız => Sonraki kapalı.
                        // - NEXT'ten sonra daima Önceki açık (en başa gelene kadar).
                        // - PREVIOUS'ten sonra daima Sonraki açık.
                        // - Önceki sadece ilk sayfada kapalı (cursorHistory.size <= 1).
                        when (direction) {
                            PageDirection.NEXT -> {
                                canGoPrev = cursorHistory.size > 1
                                canGoNext = collected == pageSize
                            }
                            PageDirection.PREVIOUS -> {
                                // En başa dönüldüyse, ilk sayfayı tekrar doldurmak için buton mantığını koru
                                canGoPrev = cursorHistory.size > 1
                                canGoNext = true
                            }
                            PageDirection.INITIAL -> {
                                canGoPrev = false
                                canGoNext = true
                            }
                        }

                        updatePaginationUI()
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

    private fun handleEmptyResult(direction: PageDirection) {
        isLoading = false
        binding.progress.visibility = View.GONE
        if (direction == PageDirection.INITIAL) adapter.clear()

        when (direction) {
            PageDirection.NEXT -> {
                // Gerçek son sayfa
                canGoNext = false
                // Geri hâlâ mümkün olabilir
                canGoPrev = cursorHistory.size > 1
            }
            PageDirection.PREVIOUS -> {
                // Boş sayfa yakalansa bile, en başta değilsek geri butonunu açık tut
                canGoPrev = cursorHistory.size > 1
                // İleri her zaman denenebilir
                canGoNext = true
            }
            PageDirection.INITIAL -> {
                canGoPrev = false
                canGoNext = false
            }
        }
        updatePaginationUI()
    }

    private fun updatePaginationUI() {
        binding.btnNextPage.isEnabled = canGoNext
        binding.btnPrevPage.isEnabled = canGoPrev
        val currentPage = cursorHistory.size
        binding.txtPageInfo.text = "Sayfa $currentPage"
    }

    private suspend fun mapProductsOfOrder(
        orderId: String,
        orderSnap: DataSnapshot,
        onlyThisProduct: DataSnapshot
    ): List<OrderItem> {
        val phone = orderSnap.key
            ?: ""

        val (ownerName, ownerSurname) = if (phone.isNotBlank()) {
            fetchMerchantUserByPhone(phone)
        } else {
            Pair("", "")
        }

        val imageUrl = onlyThisProduct.child("productImageUrl").getValue(String::class.java)
            ?: onlyThisProduct.child("imageUrl").getValue(String::class.java)
            ?: ""
        val productName = onlyThisProduct.child("productName").getValue(String::class.java)
            ?: onlyThisProduct.child("name")?.getValue(String::class.java)
            ?: ""

        val lastStatus = onlyThisProduct.child("productStatus").children.lastOrNull()
            ?.child("status")?.getValue(String::class.java).orEmpty()

        return listOf(
            OrderItem(
                id = "$orderId-${onlyThisProduct.key.orEmpty()}",
                imageUrl = imageUrl,
                ownerName = ownerName,
                ownerSurname = ownerSurname,
                ownerPhone = phone,
                productName = productName,
                lastStatus = lastStatus
            )
        )
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

    enum class PageDirection { INITIAL, NEXT, PREVIOUS }
}
