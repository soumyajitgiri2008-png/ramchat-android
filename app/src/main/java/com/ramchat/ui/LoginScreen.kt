package com.ramchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramchat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onJoinClicked: (displayName: String, roomId: String, encryptionKey: String) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }
    var encryptionKey by remember { mutableStateOf("") }
    
    val isFormValid = displayName.isNotBlank() && roomId.isNotBlank() && encryptionKey.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        // Decorative glowing abstract background circles
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = (-50).dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PrimaryGradientStart.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .blur(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PrimaryGradientEnd.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Header
            Text(
                text = "RAMChat",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(PrimaryGradientStart, PrimaryGradientEnd)
                    )
                ),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Zero Trace • Pure Volatile Messaging",
                fontSize = 14.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 36.dp)
            )

            // Glassmorphic Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = BackgroundCard)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Initialize Secure Room",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        textAlign = TextAlign.Start
                    )

                    // Display Name Input
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Alias", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryGradientStart,
                            unfocusedBorderColor = BorderColor,
                            focusedLabelColor = PrimaryGradientStart,
                            unfocusedLabelColor = TextSecondary,
                            containerColor = BackgroundCardDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    // Room ID Input
                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("Room ID") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Room ID", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryGradientStart,
                            unfocusedBorderColor = BorderColor,
                            focusedLabelColor = PrimaryGradientStart,
                            unfocusedLabelColor = TextSecondary,
                            containerColor = BackgroundCardDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    // Room Password Input (E2E Key)
                    OutlinedTextField(
                        value = encryptionKey,
                        onValueChange = { encryptionKey = it },
                        label = { Text("Secret Encryption Key") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Key", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryGradientStart,
                            unfocusedBorderColor = BorderColor,
                            focusedLabelColor = PrimaryGradientStart,
                            unfocusedLabelColor = TextSecondary,
                            containerColor = BackgroundCardDark
                        ),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )

                    // Join Button with Gradient
                    Button(
                        onClick = {
                            if (isFormValid) {
                                onJoinClicked(displayName.trim(), roomId.trim(), encryptionKey)
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        val buttonBrush = if (isFormValid) {
                            Brush.horizontalGradient(colors = listOf(PrimaryGradientStart, PrimaryGradientEnd))
                        } else {
                            Brush.horizontalGradient(colors = listOf(Color(0xFF2E2E3A), Color(0xFF2E2E3A)))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(buttonBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Enter Private Channel",
                                color = if (isFormValid) Color.White else Color.Gray,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // RAM Disclaimer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "RAM Warning",
                    tint = PrimaryGradientEnd.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "No logs. No files. Messages exist solely in volatile RAM.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
