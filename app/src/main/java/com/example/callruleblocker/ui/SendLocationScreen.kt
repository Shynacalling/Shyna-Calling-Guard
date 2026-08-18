package com.example.callruleblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.data.LocationService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendLocationScreen(onBack: () -> Unit, onSendLocation: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var isLiveMode by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf("1 hour") }
    var comment by remember { mutableStateOf("") }
    val durations = listOf("15 minutes", "30 minutes", "1 hour", "2 hours", "3 hours", "4 hours", "5 hours", "6 hours", "8 hours", "24 hours")

    val darkMapStyle = """
        [
          {
            "elementType": "geometry",
            "stylers": [{ "color": "#242f3e" }]
          },
          {
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#746855" }]
          },
          {
            "elementType": "labels.text.stroke",
            "stylers": [{ "color": "#242f3e" }]
          },
          {
            "featureType": "administrative.locality",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#d59563" }]
          },
          {
            "featureType": "poi",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#d59563" }]
          },
          {
            "featureType": "poi.park",
            "elementType": "geometry",
            "stylers": [{ "color": "#263c3f" }]
          },
          {
            "featureType": "poi.park",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#6b9a76" }]
          },
          {
            "featureType": "road",
            "elementType": "geometry",
            "stylers": [{ "color": "#38414e" }]
          },
          {
            "featureType": "road",
            "elementType": "geometry.stroke",
            "stylers": [{ "color": "#212a37" }]
          },
          {
            "featureType": "road",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#9ca5b3" }]
          },
          {
            "featureType": "road.highway",
            "elementType": "geometry",
            "stylers": [{ "color": "#746855" }]
          },
          {
            "featureType": "road.highway",
            "elementType": "geometry.stroke",
            "stylers": [{ "color": "#1f2835" }]
          },
          {
            "featureType": "road.highway",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#f3d19c" }]
          },
          {
            "featureType": "transit",
            "elementType": "geometry",
            "stylers": [{ "color": "#2f3948" }]
          },
          {
            "featureType": "transit.station",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#d59563" }]
          },
          {
            "featureType": "water",
            "elementType": "geometry",
            "stylers": [{ "color": "#17263c" }]
          },
          {
            "featureType": "water",
            "elementType": "labels.text.fill",
            "stylers": [{ "color": "#515c6d" }]
          },
          {
            "featureType": "water",
            "elementType": "labels.text.stroke",
            "stylers": [{ "color": "#17263c" }]
          }
        ]
    """.trimIndent()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(26.8242, 75.6963), 15f)
    }
    val markerState = rememberMarkerState(position = LatLng(26.8242, 75.6963))

    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    markerState.position = latLng
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Send location", color = Color.White, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White)
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLiveMode) 450.dp else 300.dp)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapStyleOptions = MapStyleOptions(darkMapStyle)
                    ),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                ) {
                    if (isLiveMode) {
                        Marker(
                            state = markerState,
                            title = "You are here",
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }

                // Map overlays from screenshot
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Fullscreen, contentDescription = "Full screen", tint = Color.Black)
                }

                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "My location", tint = Color.Black)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isLiveMode) {
                        item {
                            LocationActionRow(
                                title = "Share live location",
                                icon = Icons.Outlined.WifiTethering,
                                iconBg = Color.White,
                                iconTint = Color.Black,
                                onClick = { isLiveMode = true }
                            )
                        }

                        item {
                            Text(
                                "Nearby places",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        item {
                            LocationActionRow(
                                title = "Send your current location",
                                subtitle = "Accurate to 29 meters",
                                icon = Icons.Outlined.MyLocation,
                                iconBg = Color.Black,
                                iconTint = Color(0xFF00E676),
                                border = true
                            )
                        }

                        val nearbyPlaces = listOf(
                            "Riya Consultant" to "Sanganer, RJ, IN",
                            "A52 Mangalam Grand City" to "Mathur Road, Sanganer, 302026, RJ, IN",
                            "Shreeram tour and travels" to "Galaxy manglam Grand city, Sanganer, 302026...",
                            "Shree Ram Properties" to "Sanganer, RJ, IN"
                        )

                        items(nearbyPlaces) { place ->
                            PlaceRow(place.first, place.second)
                        }
                    } else {
                        item {
                            Text("Share live location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(durations) { duration ->
                                    val isSelected = selectedDuration == duration
                                    Surface(
                                        modifier = Modifier.clickable { selectedDuration = duration },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF242424)
                                    ) {
                                        Text(
                                            duration,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            color = if (isSelected) Color.Black else Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            TextField(
                                value = comment,
                                onValueChange = { comment = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Add comment", color = Color.Gray) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Gray,
                                    unfocusedIndicatorColor = Color.Gray,
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = { 
                                    isLiveMode = false 
                                    context.stopService(android.content.Intent(context, LocationService::class.java))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                            ) {
                                Text("Stop sharing")
                            }
                        }
                    }
                }

                if (isLiveMode) {
                    FloatingActionButton(
                        onClick = { 
                            context.startForegroundService(android.content.Intent(context, LocationService::class.java))
                            onSendLocation("${markerState.position.latitude},${markerState.position.longitude}")
                            onBack() 
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun LocationActionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    border: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = iconBg,
            border = if (border) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E676)) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PlaceRow(name: String, address: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = Color(0xFF242424)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(address, color = Color.Gray, fontSize = 13.sp)
        }
    }
}
