package com.yeab.esnapp.ui.auth

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.yeab.esnapp.R
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMapRef: GoogleMap? = null
    private var selectedMarker: Marker? = null
    private lateinit var btnConfirm: MaterialButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        private const val MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey"
        const val EXTRA_LAT = "selected_lat"
        const val EXTRA_LNG = "selected_lng"
        const val EXTRA_COUNTRY = "selected_country"
        const val EXTRA_CITY = "selected_city"
        const val EXTRA_DISTRICT = "selected_district"
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableMyLocation()
                moveToLastLocation()
            } else {
                Toast.makeText(this, getString(R.string.error_location_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnConfirm = findViewById(R.id.btnConfirmLocation)

        mapView = MapView(this)
        val container = findViewById<android.view.ViewGroup>(R.id.mapContainer)
        container.addView(
            mapView,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        val bundle = savedInstanceState?.getBundle(MAP_VIEW_BUNDLE_KEY)
        mapView.onCreate(bundle)
        mapView.getMapAsync(this)

        btnConfirm.setOnClickListener {
            val marker = selectedMarker
            if (marker == null) {
                Toast.makeText(this, getString(R.string.mappicker_no_selection), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lat = marker.position.latitude
            val lng = marker.position.longitude

            // Reverse Geocode
            val geocoder = Geocoder(this, Locale.getDefault())
            var country = ""
            var city = ""
            var district = ""

            try {
                val result = geocoder.getFromLocation(lat, lng, 1)
                if (!result.isNullOrEmpty()) {
                    val addr = result[0]
                    country = addr.countryName ?: ""
                    city = addr.adminArea ?: ""
                    district = addr.subAdminArea ?: addr.locality ?: ""
                }
            } catch (e: Exception) {
                // geocoder başarısız olabilir
            }

            val intent = Intent()
            intent.putExtra(EXTRA_LAT, lat)
            intent.putExtra(EXTRA_LNG, lng)
            intent.putExtra(EXTRA_COUNTRY, country)
            intent.putExtra(EXTRA_CITY, city)
            intent.putExtra(EXTRA_DISTRICT, district)

            setResult(RESULT_OK, intent)
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMapRef = map
        map.uiSettings.isZoomControlsEnabled = true

        map.setOnMapClickListener { placeMarker(it) }

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            enableMyLocation()
            moveToLastLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun placeMarker(latLng: LatLng) {
        googleMapRef ?: return

        selectedMarker?.remove()
        selectedMarker = googleMapRef!!.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(getString(R.string.mappicker_marker_title))
        )
        btnConfirm.visibility = View.VISIBLE

        googleMapRef!!.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
    }

    private fun enableMyLocation() {
        try {
            googleMapRef?.isMyLocationEnabled = true
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun moveToLastLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc != null) {
                val latLng = LatLng(loc.latitude, loc.longitude)
                googleMapRef?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            } else {
                googleMapRef?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(41.0, 28.97),
                        12f
                    )
                )
            }
        }
    }

    // MapView lifecycle
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
