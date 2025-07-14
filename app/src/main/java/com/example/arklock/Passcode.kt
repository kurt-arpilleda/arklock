package com.example.arklock

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun PasscodeScreen(
    onPasscodeVerified: () -> Unit,
    appName: String? = null
) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
    val passwordType = sharedPref.getString("password_type", "PIN") ?: "PIN"
    val savedPassword = sharedPref.getString("password_value", "") ?: ""

    var inputPin by remember { mutableStateOf("") }
    var inputPattern by remember { mutableStateOf(listOf<Int>()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Block recent apps button
    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // Handle back button - prevent dismissing the lock screen
    BackHandler {
        // Do nothing - prevent back button from closing the lock screen
        // User must enter correct PIN/pattern to unlock
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // App Icon/Logo
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = if (appName != null) "Locked: $appName" else "Enter Passcode",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = if (appName != null) {
                "Enter your ${if (passwordType == "PIN") "PIN" else "pattern"} to unlock"
            } else {
                "Enter your ${if (passwordType == "PIN") "PIN" else "pattern"} to continue"
            },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (showError) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // PIN or Pattern Input
        if (passwordType == "PIN") {
            PinVerificationInput(
                pin = inputPin,
                onPinChange = { newPin ->
                    if (newPin.length <= 6) {
                        inputPin = newPin
                        showError = false

                        if (newPin.length == 6) {
                            if (newPin == savedPassword) {
                                onPasscodeVerified()
                            } else {
                                showError = true
                                errorMessage = "Incorrect PIN. Try again."
                                inputPin = ""
                            }
                        }
                    }
                }
            )
        } else {
            PatternVerificationInput(
                selectedPoints = inputPattern,
                onPatternChange = { pattern ->
                    inputPattern = pattern
                    showError = false

                    if (pattern.size >= 4) {
                        val patternString = pattern.joinToString(",")
                        if (patternString == savedPassword) {
                            onPasscodeVerified()
                        } else {
                            showError = true
                            errorMessage = "Incorrect pattern. Try again."
                            inputPattern = listOf()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun PinVerificationInput(pin: String, onPinChange: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // PIN Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .border(
                            2.dp,
                            if (index < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < pin.length) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Number Keypad
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (row in 0..3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    for (col in 0..2) {
                        val number = when {
                            row == 3 && col == 0 -> null
                            row == 3 && col == 1 -> 0
                            row == 3 && col == 2 -> -1
                            else -> row * 3 + col + 1
                        }

                        if (number != null) {
                            if (number == -1) {
                                Button(
                                    onClick = {
                                        if (pin.isNotEmpty()) {
                                            onPinChange(pin.dropLast(1))
                                        }
                                    },
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "⌫",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (pin.length < 6) {
                                            onPinChange(pin + number.toString())
                                        }
                                    },
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = number.toString(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PatternVerificationInput(
    selectedPoints: List<Int>,
    onPatternChange: (List<Int>) -> Unit
) {
    val density = LocalDensity.current
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var currentPattern by remember { mutableStateOf<List<Int>>(emptyList()) }

    val patternSize = 3
    val dotSize = 48.dp
    val spacing = 48.dp
    val totalSize = with(density) { (dotSize * patternSize + spacing * (patternSize - 1)).toPx() }
    val dotSizePx = with(density) { dotSize.toPx() }
    val spacingPx = with(density) { spacing.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier.size(with(density) { totalSize.toDp() })
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset: Offset ->
                                isDragging = true
                                currentPattern = emptyList()
                                currentPath = listOf(offset)

                                val dotIndex = getDotIndex(offset, dotSizePx, spacingPx, patternSize)
                                if (dotIndex != -1) {
                                    currentPattern = listOf(dotIndex + 1)
                                }
                            },
                            onDrag = { change, _ ->
                                if (isDragging) {
                                    val currentPos = change.position
                                    currentPath = currentPath + currentPos

                                    val dotIndex = getDotIndex(currentPos, dotSizePx, spacingPx, patternSize)
                                    if (dotIndex != -1) {
                                        val newDot = dotIndex + 1
                                        if (currentPattern.isEmpty()) {
                                            currentPattern = listOf(newDot)
                                        } else if (!currentPattern.contains(newDot)) {
                                            // Get the last selected dot
                                            val lastDot = currentPattern.last()

                                            // Calculate all intermediate dots between lastDot and newDot
                                            val intermediateDots = getIntermediateDots(lastDot, newDot, patternSize)

                                            // Add all intermediate dots that haven't been selected yet
                                            val newDots = intermediateDots.filter { !currentPattern.contains(it) }

                                            currentPattern = currentPattern + newDots + newDot
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                if (currentPattern.size >= 4) {
                                    onPatternChange(currentPattern)
                                }
                                currentPath = emptyList()
                                currentPattern = emptyList()
                            }
                        )
                    }
            ) {
                if (selectedPoints.size > 1) {
                    drawPatternLines(selectedPoints.map { it - 1 }, dotSizePx, spacingPx, patternSize)
                }

                // Draw current drag lines
                if (isDragging && currentPattern.isNotEmpty() && currentPath.size > 1) {
                    drawPatternLines(currentPattern.map { it - 1 }, dotSizePx, spacingPx, patternSize)

                    // Draw line from last selected dot to current finger position
                    val lastDotIndex = currentPattern.last() - 1
                    val lastDotRow = lastDotIndex / patternSize
                    val lastDotCol = lastDotIndex % patternSize
                    val lastDotCenter = Offset(
                        lastDotCol * (dotSizePx + spacingPx) + dotSizePx / 2,
                        lastDotRow * (dotSizePx + spacingPx) + dotSizePx / 2
                    )

                    drawLine(
                        color = Color.Gray.copy(alpha = 0.7f),
                        start = lastDotCenter,
                        end = currentPath.last(),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Draw the pattern dots
                for (row in 0 until patternSize) {
                    for (col in 0 until patternSize) {
                        val pointIndex = row * patternSize + col + 1 // Convert to 1-9
                        val isSelected = selectedPoints.contains(pointIndex) || currentPattern.contains(pointIndex)

                        val centerX = col * (dotSizePx + spacingPx) + dotSizePx / 2
                        val centerY = row * (dotSizePx + spacingPx) + dotSizePx / 2

                        drawCircle(
                            color = if (isSelected) Color(0xFF4285F4)
                            else Color.Gray.copy(alpha = 0.3f),
                            radius = dotSizePx / 4,
                            center = Offset(centerX, centerY)
                        )
                        if (isSelected) {
                            drawCircle(
                                color = Color(0xFF4285F4),
                                radius = dotSizePx / 8,
                                center = Offset(centerX, centerY)
                            )
                        }
                    }
                }
            }
        }

        if (selectedPoints.isNotEmpty()) {
            TextButton(
                onClick = { onPatternChange(listOf()) }
            ) {
                Text("Clear Pattern")
            }
        }
    }
}

private fun DrawScope.drawPatternLines(
    pattern: List<Int>,
    dotSizePx: Float,
    spacingPx: Float,
    patternSize: Int
) {
    if (pattern.size < 2) return

    val path = Path()
    var isFirst = true

    for (dotIndex in pattern) {
        val row = dotIndex / patternSize
        val col = dotIndex % patternSize
        val centerX = col * (dotSizePx + spacingPx) + dotSizePx / 2
        val centerY = row * (dotSizePx + spacingPx) + dotSizePx / 2
        val point = Offset(centerX, centerY)

        if (isFirst) {
            path.moveTo(point.x, point.y)
            isFirst = false
        } else {
            path.lineTo(point.x, point.y)
        }
    }

    drawPath(
        path = path,
        color = Color.Gray.copy(alpha = 0.7f),
        style = Stroke(
            width = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
    )
}

private fun getDotIndex(
    offset: Offset,
    dotSizePx: Float,
    spacingPx: Float,
    patternSize: Int
): Int {
    for (row in 0 until patternSize) {
        for (col in 0 until patternSize) {
            val centerX = col * (dotSizePx + spacingPx) + dotSizePx / 2
            val centerY = row * (dotSizePx + spacingPx) + dotSizePx / 2

            val distance = sqrt(
                (offset.x - centerX) * (offset.x - centerX) +
                        (offset.y - centerY) * (offset.y - centerY)
            )

            if (distance <= dotSizePx / 2) {
                return row * patternSize + col
            }
        }
    }
    return -1
}

private fun getIntermediateDots(start: Int, end: Int, patternSize: Int): List<Int> {
    if (start == end) return emptyList()

    val startRow = (start - 1) / patternSize
    val startCol = (start - 1) % patternSize
    val endRow = (end - 1) / patternSize
    val endCol = (end - 1) % patternSize

    // If not in same row or column or diagonal, no intermediate dots
    if (startRow != endRow && startCol != endCol &&
        abs(startRow - endRow) != abs(startCol - endCol)) {
        return emptyList()
    }

    val intermediate = mutableListOf<Int>()

    val rowStep = if (endRow > startRow) 1 else if (endRow < startRow) -1 else 0
    val colStep = if (endCol > startCol) 1 else if (endCol < startCol) -1 else 0

    var currentRow = startRow + rowStep
    var currentCol = startCol + colStep

    while (currentRow != endRow || currentCol != endCol) {
        intermediate.add(currentRow * patternSize + currentCol + 1)
        currentRow += rowStep
        currentCol += colStep
    }

    return intermediate
}