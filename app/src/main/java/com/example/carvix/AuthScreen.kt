package com.example.carvix

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carvix.data.repository.AuthRepository
import com.example.carvix.ui.LocalAppToaster
import kotlinx.coroutines.launch

private fun parseError(msg: String?): String {
    if (msg.isNullOrBlank()) return "Ошибка"
    val m = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(msg)
    return m?.groupValues?.get(1) ?: msg
}

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF5EFE6))) {
        val isCompact = maxWidth < 600.dp
        if (isCompact) {
            CompactAuthScreen(isLogin, { isLogin = !isLogin }, authRepo, onLoginSuccess)
        } else {
            Row(Modifier.fillMaxSize()) {
                LeftPanel(Modifier.fillMaxHeight().weight(1f))
                RightPanel(isLogin, { isLogin = !isLogin }, authRepo, onLoginSuccess, Modifier.fillMaxHeight().weight(1f))
            }
        }
    }
}

@Composable
fun CompactAuthScreen(isLogin: Boolean, onToggle: () -> Unit, authRepo: AuthRepository, onLoginSuccess: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFDFBF7)).border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.brand_logo),
                    contentDescription = "Carvix logo",
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("Carvix", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3E3228))
        }
        Spacer(Modifier.height(32.dp))
        AuthTabs(isLogin, onToggle, true)
        Spacer(Modifier.height(28.dp))
        AnimatedContent(targetState = isLogin, label = "auth") { login ->
            if (login) LoginForm(onToggle, authRepo, onLoginSuccess, true)
            else RegisterForm(onToggle, authRepo, true)
        }
    }
}

@Composable
fun LeftPanel(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(brush = Brush.linearGradient(listOf(Color(0xFFEDE6DA), Color(0xFFDCCFB8)), start = Offset(0f,0f), end = Offset(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY))).padding(40.dp)) {
        Column(verticalArrangement = Arrangement.Top, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFDFBF7)).border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.brand_logo),
                    contentDescription = "Carvix logo",
                    modifier = Modifier.size(68.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Carvix", fontSize = 42.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3E3228))
            Spacer(Modifier.height(8.dp))
            Text("Управление автопарком и ремонтами", fontSize = 15.sp, color = Color(0xFF5A4D42))
            Spacer(Modifier.height(32.dp))
            FeatureItem("Учёт техники и пробега")
            FeatureItem("Заявки на ТО и ремонт")
            FeatureItem("Аналитика для руководства")
            FeatureItem("Контроль запчастей и поставок")
        }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF5A4D42)))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = Color(0xFF5A4D42))
    }
}

@Composable
fun CarIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val c = Color(0xFFC4A882)
        val bodyTop = h * 0.45f; val bodyBottom = h * 0.78f; val r = h * 0.14f; val wy = bodyBottom + r * 0.3f
        drawRoundRect(color = c, topLeft = Offset(w * 0.05f, bodyTop), size = Size(w * 0.9f, bodyBottom - bodyTop), cornerRadius = CornerRadius(w * 0.08f, w * 0.08f))
        drawArc(color = c, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(w * 0.2f, bodyTop - h * 0.25f), size = Size(w * 0.6f, h * 0.45f))
        drawCircle(Color(0xFFFDFBF7), r, Offset(w * 0.28f, wy))
        drawCircle(c, r * 0.5f, Offset(w * 0.28f, wy))
        drawCircle(Color(0xFFFDFBF7), r, Offset(w * 0.72f, wy))
        drawCircle(c, r * 0.5f, Offset(w * 0.72f, wy))
    }
}

@Composable
fun RightPanel(isLogin: Boolean, onToggle: () -> Unit, authRepo: AuthRepository, onLoginSuccess: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AuthTabs(isLogin, onToggle, false)
            Spacer(Modifier.height(40.dp))
            AnimatedContent(targetState = isLogin, label = "auth") { login ->
                if (login) LoginForm(onToggle, authRepo, onLoginSuccess, false)
                else RegisterForm(onToggle, authRepo, false)
            }
        }
    }
}

@Composable
fun AuthTabs(isLogin: Boolean, onToggle: () -> Unit, isCompact: Boolean) {
    val tabHeight = if (isCompact) 44.dp else 48.dp
    val fontSize = if (isCompact) 13.sp else 14.sp
    Box(Modifier.fillMaxWidth().height(tabHeight).clip(RoundedCornerShape(24.dp)).background(Color(0xFFF2F2F2)).padding(4.dp)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(if (isLogin) Color(0xFF1F1F1F) else Color.Transparent).clickable { if (!isLogin) onToggle() }, contentAlignment = Alignment.Center) {
                Text("Вход", color = if (isLogin) Color.White else Color(0xFF5A5A5A), fontSize = fontSize, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(if (!isLogin) Color(0xFF1F1F1F) else Color.Transparent).clickable { if (isLogin) onToggle() }, contentAlignment = Alignment.Center) {
                Text("Регистрация", color = if (!isLogin) Color.White else Color(0xFF5A5A5A), fontSize = fontSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun LoginForm(onToggle: () -> Unit, authRepo: AuthRepository, onLoginSuccess: () -> Unit, isCompact: Boolean) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val toaster = LocalAppToaster.current
    val titleSize = if (isCompact) 24.sp else 28.sp
    val subtitleSize = if (isCompact) 13.sp else 14.sp
    val fieldSpacing = if (isCompact) 10.dp else 12.dp
    val buttonHeight = if (isCompact) 48.dp else 52.dp
    val buttonFontSize = if (isCompact) 14.sp else 15.sp
    val linkFontSize = if (isCompact) 12.sp else 13.sp
    Column(Modifier.fillMaxWidth()) {
        Text("С возвращением", fontSize = titleSize, fontWeight = FontWeight.Medium, color = Color(0xFF3E3228))
        Spacer(Modifier.height(6.dp))
        Text("Войдите в аккаунт, чтобы продолжить", fontSize = subtitleSize, color = Color(0xFF8A8279))
        Spacer(Modifier.height(if (isCompact) 20.dp else 24.dp))
        OutlinedTextField(value = login, onValueChange = { login = it; error = null }, placeholder = { Text("Логин", color = Color(0xFFAAAAAA)) }, leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFFAAAAAA)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
        Spacer(Modifier.height(fieldSpacing))
        OutlinedTextField(value = password, onValueChange = { password = it; error = null }, placeholder = { Text("Пароль", color = Color(0xFFAAAAAA)) }, leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFFAAAAAA)) }, trailingIcon = { IconButton({ passwordVisible = !passwordVisible }, enabled = !isLoading) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color(0xFFAAAAAA)) } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
        if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, fontSize = 13.sp, color = Color(0xFFB00020)) }
        Spacer(Modifier.height(if (isCompact) 20.dp else 24.dp))
        Button({
            if (login.isBlank() || password.isBlank()) {
                error = "Заполните все поля"
                toaster.error("Заполните все поля")
                return@Button
            }
            isLoading = true; error = null
            toaster.action("Вход в аккаунт…")
            scope.launch {
                val r = authRepo.login(login, password)
                isLoading = false
                r.onSuccess {
                    toaster.success("Вы вошли как $login")
                    onLoginSuccess()
                }
                r.onFailure {
                    val msg = parseError(it.message)
                    error = msg
                    toaster.error("Ошибка входа: $msg")
                }
            }
        }, Modifier.fillMaxWidth().height(buttonHeight), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)), enabled = !isLoading) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else { Text("Войти", color = Color.White, fontSize = buttonFontSize, fontWeight = FontWeight.Medium); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Нет аккаунта? ", fontSize = linkFontSize, color = Color(0xFF8A8279))
            Text("Зарегистрироваться", fontSize = linkFontSize, color = Color(0xFF3E3228), fontWeight = FontWeight.Medium, modifier = Modifier.clickable(enabled = !isLoading) { onToggle() })
        }
    }
}

@Composable
fun RegisterForm(onToggle: () -> Unit, authRepo: AuthRepository, isCompact: Boolean) {
    var fullName by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toaster = LocalAppToaster.current
    val titleSize = if (isCompact) 24.sp else 28.sp
    val subtitleSize = if (isCompact) 13.sp else 14.sp
    val fieldSpacing = if (isCompact) 10.dp else 12.dp
    val buttonHeight = if (isCompact) 48.dp else 52.dp
    val buttonFontSize = if (isCompact) 14.sp else 15.sp
    val linkFontSize = if (isCompact) 12.sp else 13.sp
    Column(Modifier.fillMaxWidth()) {
        Text("Создать аккаунт", fontSize = titleSize, fontWeight = FontWeight.Medium, color = Color(0xFF3E3228))
        Spacer(Modifier.height(6.dp))
        Text("Заполните данные сотрудника", fontSize = subtitleSize, color = Color(0xFF8A8279))
        Spacer(Modifier.height(if (isCompact) 20.dp else 24.dp))
        OutlinedTextField(value = fullName, onValueChange = { fullName = it; error = null }, placeholder = { Text("Фамилия Имя Отчество", color = Color(0xFFAAAAAA)) }, leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFFAAAAAA)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
        Spacer(Modifier.height(fieldSpacing))
        if (isCompact) {
            OutlinedTextField(value = login, onValueChange = { login = it; error = null }, placeholder = { Text("Логин", color = Color(0xFFAAAAAA)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
            Spacer(Modifier.height(fieldSpacing))
            OutlinedTextField(value = password, onValueChange = { password = it; error = null }, placeholder = { Text("Пароль", color = Color(0xFFAAAAAA)) }, trailingIcon = { IconButton({ passwordVisible = !passwordVisible }, enabled = !isLoading) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color(0xFFAAAAAA)) } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
        } else {
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = login, onValueChange = { login = it; error = null }, placeholder = { Text("Логин", color = Color(0xFFAAAAAA)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it; error = null }, placeholder = { Text("Пароль", color = Color(0xFFAAAAAA)) }, trailingIcon = { IconButton({ passwordVisible = !passwordVisible }, enabled = !isLoading) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color(0xFFAAAAAA)) } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE0E0E0), unfocusedBorderColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA)), singleLine = true, enabled = !isLoading)
            }
        }
        if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, fontSize = 13.sp, color = Color(0xFFB00020)) }
        if (success) { Spacer(Modifier.height(8.dp)); Text("Аккаунт создан! Теперь войдите.", fontSize = 13.sp, color = Color(0xFF2E7D32)) }
        Spacer(Modifier.height(if (isCompact) 20.dp else 24.dp))
        Button({
            if (fullName.isBlank() || login.isBlank() || password.isBlank()) {
                error = "Заполните все поля"
                toaster.error("Заполните все поля")
                return@Button
            }
            isLoading = true; error = null; success = false
            toaster.action("Регистрация $login…")
            scope.launch {
                val r = authRepo.register(fullName, login, password)
                isLoading = false
                r.onSuccess {
                    success = true
                    toaster.success("Аккаунт $login создан")
                }
                r.onFailure {
                    val msg = parseError(it.message)
                    error = msg
                    toaster.error("Ошибка регистрации: $msg")
                }
            }
        }, Modifier.fillMaxWidth().height(buttonHeight), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)), enabled = !isLoading) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else { Text("Создать аккаунт", color = Color.White, fontSize = buttonFontSize, fontWeight = FontWeight.Medium); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Уже есть аккаунт? ", fontSize = linkFontSize, color = Color(0xFF8A8279))
            Text("Войти", fontSize = linkFontSize, color = Color(0xFF3E3228), fontWeight = FontWeight.Medium, modifier = Modifier.clickable(enabled = !isLoading) { onToggle() })
        }
    }
}
