package com.piceroo.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piceroo.app.ui.CelebrateScreen
import com.piceroo.app.ui.HomeScreen
import com.piceroo.app.ui.PermissionScreen
import com.piceroo.app.ui.PlayScreen
import com.piceroo.app.ui.PicerooTheme

enum class Access { NONE, PARTIAL, FULL }

class MainActivity : ComponentActivity() {
    private var access by mutableStateOf(Access.NONE)
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PicerooTheme {
                val vm: AppViewModel = viewModel()

                // Android shows its own "Allow Piceroo to move N items to trash?" dialog;
                // this receives your answer.
                val trashLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result -> vm.onTrashResult(result.resultCode == RESULT_OK) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { access = mediaAccess() }

                // Reload photos when access changes or you come back to the app on the home screen.
                LaunchedEffect(access, resumeCount) {
                    if (access != Access.NONE && vm.screen == Screen.Home) vm.load()
                }

                if (access == Access.NONE) {
                    PermissionScreen(onGrant = { permissionLauncher.launch(mediaPermissions()) })
                    return@PicerooTheme
                }

                when (val s = vm.screen) {
                    Screen.Home -> HomeScreen(
                        vm = vm,
                        partialAccess = access == Access.PARTIAL,
                        onFixAccess = { permissionLauncher.launch(mediaPermissions()) },
                        onShareList = { shareText(vm.cloudCleanupList()) },
                    )
                    is Screen.Play -> {
                        BackHandler { vm.goHome() }
                        PlayScreen(
                            deck = s.deck,
                            onExit = vm::goHome,
                            onFinish = { trash, keep ->
                                if (vm.finishRound(trash, keep)) {
                                    try {
                                        val request = MediaStore.createTrashRequest(
                                            contentResolver, trash.map { it.uri }, true
                                        )
                                        trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                    } catch (e: Exception) {
                                        // Usually means a photo was already deleted elsewhere.
                                        vm.onTrashResult(false)
                                        Toast.makeText(this, "Couldn't trash those — reloading", Toast.LENGTH_SHORT).show()
                                        vm.goHome()
                                        vm.load()
                                    }
                                }
                            },
                        )
                    }
                    is Screen.Celebrate -> {
                        BackHandler { vm.goHome() }
                        CelebrateScreen(result = s.result, onNext = vm::quickRound, onHome = vm::goHome)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        access = mediaAccess()
        resumeCount++
    }

    private fun shareText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "Nothing trashed yet", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Piceroo cloud cleanup list")
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Save cleanup list"))
    }
}

/** Which permissions to ask for depends on the Android version. */
fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** FULL = every photo; PARTIAL = you picked "Select photos" on Android 14+. */
fun Context.mediaAccess(): Access {
    fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
        Build.VERSION.SDK_INT < 33 && has(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.FULL
        Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
        else -> Access.NONE
    }
}
