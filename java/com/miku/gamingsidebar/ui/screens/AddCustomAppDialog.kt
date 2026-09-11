package com.miku.gamingsidebar.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miku.gamingsidebar.R
import com.miku.gamingsidebar.data.InstalledAppItem
import com.miku.gamingsidebar.viewmodel.HubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomAppBottomSheet(
    viewModel: HubViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingInstalledApps.collectAsState()
    var appSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
    }

    val filteredApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                        it.packageName.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141218),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF49454F))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.add_app_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.add_app_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E)
                    )
                }

                FilledIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF2C2834),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = appSearchQuery,
                onValueChange = { appSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.add_app_search_placeholder),
                        color = Color(0xFF757575)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF)
                    )
                },
                trailingIcon = {
                    if (appSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { appSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.add_app_clear),
                                tint = Color(0xFF9E9E9E)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading && installedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.add_app_scanning),
                            color = Color(0xFF9E9E9E),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.add_app_not_found),
                        color = Color(0xFF757575),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        val isEnabled = app.isTurboEnabled

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.toggleCustomApp(app.packageName)
                                },
                            color = if (isEnabled) Color(0xFF1E2430) else Color(0xFF1A1720),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isEnabled) Color(0x6600E5FF) else Color(0x1AFFFFFF)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppDrawableIcon(
                                    drawable = app.icon,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (app.isTurboEnabled && app.isAutoDetectedGame) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x3300E5FF))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.add_app_badge_detected),
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (app.isTurboEnabled && app.isCustomAdded) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x3300E676))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.add_app_badge_added),
                                                    color = Color(0xFF00E676),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (!app.isTurboEnabled && app.isAutoDetectedGame) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x33FF5252))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.add_app_badge_disabled),
                                                    color = Color(0xFFFF5252),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF757575),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = {
                                        viewModel.toggleCustomApp(app.packageName)
                                    },
                                    enabled = true,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00E5FF),
                                        checkedTrackColor = Color(0x4D00E5FF),
                                        uncheckedThumbColor = Color(0xFF757575),
                                        uncheckedTrackColor = Color(0xFF2C2834)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawableIcon(
    drawable: Drawable?,
    modifier: Modifier = Modifier
) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            try {
                val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(160) else 96
                val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(160) else 96
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = modifier
            )
            return
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF2C2834), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SportsEsports,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(22.dp)
        )
    }
}
