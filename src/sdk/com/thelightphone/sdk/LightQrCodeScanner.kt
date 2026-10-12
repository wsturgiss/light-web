package com.thelightphone.sdk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

/**
 * The SDK's QR code scanner. On the phone it's the camera with ML Kit; light-web has neither
 * (the SDK's version is excluded from the build), so this says so and offers the way back.
 * [onScanned] is never called.
 */
@Composable
fun LightQrCodeScanner(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Scan QR Code",
) {
    Column(modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(title),
        )
        LightText(
            "Scanning a QR code needs the phone's camera, which isn't available in the browser.",
            LightTextVariant.Paragraph,
            modifier = Modifier.padding(16.dp),
        )
    }
}
