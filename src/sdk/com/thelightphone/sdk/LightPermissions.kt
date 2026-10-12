package com.thelightphone.sdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.thelightphone.sdk.shared.LightResult
import com.thelightphone.sdk.shared.LightServiceMethod

// Runtime permissions. On the phone LightOS answers by its policy and asks the user. The browser
// has its own prompts for what it offers, but none that matches these Android permissions (the
// music folder, the camera for the SDK's scanner), so every permission reads as denied, and a
// request does nothing.

/** As LightOS answers for a permission the tool may not have. */
suspend fun checkPermission(permission: String): LightResult<LightServiceMethod.GetPermission.Response> =
    LightResult.Success(LightServiceMethod.GetPermission.Response(LightServiceMethod.GetPermission.Result.Denied))

/** light-amp's SDK patch: the process's own grant. Null means it can't tell. */
fun hasRuntimePermission(permission: String): Boolean? = false

class PermissionRequestLauncher internal constructor(private val permission: String) {
    fun launch() {}
}

@Composable
fun rememberPermissionRequestLauncher(permission: String): PermissionRequestLauncher? =
    remember(permission) { PermissionRequestLauncher(permission) }
