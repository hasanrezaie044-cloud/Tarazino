package ir.tarazino.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import ir.tarazino.R
import ir.tarazino.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(auth: AuthStore, activity: FragmentActivity, onUnlocked: () -> Unit, onReset: () -> Unit) {
    val scope = rememberCoroutineScope()
    var hasAccount by remember { mutableStateOf(auth.hasAccount()) }
    var id by remember { mutableStateOf(auth.identifier()) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val bioReady = hasAccount && auth.biometricEnabled && BiometricHelper.available(activity)

    fun askBiometric() = BiometricHelper.prompt(activity, "ترازینو", "رمز عبور", onUnlocked) {}
    LaunchedEffect(Unit) { if (bioReady) askBiometric() }

    fun submit() {
        error = null
        scope.launch {
            busy = true
            if (!hasAccount) {
                error = when {
                    id.isBlank() -> "شناسه الزامی است"
                    pw.length < 6 -> "رمز حداقل ۶ نویسه"
                    pw != pw2 -> "رمزها یکسان نیست"
                    else -> null
                }
                if (error == null) { withContext(Dispatchers.Default) { auth.register(id, pw) }; hasAccount = true; onUnlocked() }
            } else {
                when (val r = withContext(Dispatchers.Default) { auth.verify(pw) }) {
                    VerifyResult.Ok -> onUnlocked()
                    VerifyResult.Wrong -> error = "رمز نادرست است"
                    is VerifyResult.Locked -> error = "قفل موقت: " + ir.tarazino.util.Fa.digits("${r.seconds}") + " ثانیه"
                }
            }
            busy = false
        }
    }

    TarazinoTheme(dark = true) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF010818), Color(0xFF011629), Color(0xFF020F1F))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.login_hero), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                Column(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2EE6A6), unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                        focusedLabelColor = Color(0xFF2EE6A6), unfocusedLabelColor = Color.White.copy(alpha = 0.7f), focusedLeadingIconColor = Color(0xFF2EE6A6),
                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f), cursorColor = Color(0xFF2EE6A6),
                        focusedTrailingIconColor = Color.White, unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f),
                    )
                    OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth(), singleLine = true, readOnly = hasAccount, label = { Text("شماره موبایل یا ایمیل") },
                        leadingIcon = { Icon(Icons.Rounded.Person, null) }, shape = RoundedCornerShape(18.dp), colors = fieldColors)
                    OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("رمز عبور") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        trailingIcon = { IconAction(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "پنهان" else "نمایش", { show = !show }) },
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(18.dp), colors = fieldColors)
                    if (!hasAccount) OutlinedTextField(pw2, { pw2 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("تکرار رمز") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(18.dp), colors = fieldColors)
                    error?.let { Text(it, color = Color(0xFFFF6B6B)) }
                    if (hasAccount) TextButton({ confirmReset = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("رمز عبور خود را فراموش کرده‌اید؟", color = Color(0xFF2EE6A6)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            { submit() }, Modifier.weight(1f).height(54.dp), enabled = !busy, shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EE6A6), contentColor = Color(0xFF00382A)),
                        ) { Text(if (hasAccount) "ورود" else "ثبت نام در ترازینو", style = MaterialTheme.typography.titleMedium) }
                        if (bioReady) FilledTonalIconButton({ askBiometric() }, Modifier.size(54.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.Fingerprint, "ورود با اثر انگشت") }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(ir.tarazino.util.AppVersion.label(activity), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Spacer(Modifier.height(20.dp))
            }
        }
        if (confirmReset) AlertDialog(
            onDismissRequest = { confirmReset = false }, shape = MaterialTheme.shapes.large, title = { Text("حذف همه داده‌ها و رمز؟") },
            confirmButton = { FilledIconButton({ confirmReset = false; onReset(); hasAccount = false; id = ""; pw = ""; pw2 = "" }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Expense)) { Icon(Icons.Rounded.DeleteForever, "حذف") } },
            dismissButton = { IconButton({ confirmReset = false }) { Icon(Icons.Rounded.Close, "بستن") } },
        )
    }
}
