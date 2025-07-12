package com.example.arklock

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(
    isChangePassword: Boolean = false,
    onPasswordSaved: (String) -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(1) }
    var passwordType by remember { mutableStateOf("PIN") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var pinCode by remember { mutableStateOf("") }
    var verifyPinCode by remember { mutableStateOf("") }
    var patternPoints by remember { mutableStateOf(listOf<Int>()) }
    var verifyPatternPoints by remember { mutableStateOf(listOf<Int>()) }
    var showError by remember { mutableStateOf(false) }
    var showSaveButton by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isChangePassword) {
                    IconButton(
                        onClick = {
                            (context as? ComponentActivity)?.finish()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .offset(x = (-12).dp) // Optional subtle left shift
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp)) // Same width as icon button
                }
            }

            Text(
                text = if (isChangePassword) "Change Passcode" else "Set Passcode",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }


        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step 1
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (step >= 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Connecting line
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(
                        if (step >= 2) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )

            // Step 2
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (step >= 2) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "2",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (step == 1) {
            // Dropdown for password type (Centered & reduced width)
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .wrapContentHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = passwordType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Password Type") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (passwordType == "PIN") Icons.Default.Lock else Icons.Default.Lock,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Text("PIN")
                                }
                            },
                            onClick = {
                                passwordType = "PIN"
                                dropdownExpanded = false
                                pinCode = ""
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Text("PATTERN")
                                }
                            },
                            onClick = {
                                passwordType = "PATTERN"
                                dropdownExpanded = false
                                patternPoints = listOf()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (passwordType == "PIN") {
                PinInput(
                    pin = pinCode,
                    onPinChange = { newPin ->
                        if (newPin.length <= 6) pinCode = newPin
                    },
                    isVerification = false
                )
            } else {
                PatternInput(
                    selectedPoints = patternPoints,
                    onPatternChange = { patternPoints = it },
                    isVerification = false
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Show Create button only when valid input
            val isValidPassword = (passwordType == "PIN" && pinCode.length == 6) ||
                    (passwordType == "PATTERN" && patternPoints.size >= 4)

            if (isValidPassword) {
                Button(
                    onClick = {
                        step = 2
                        verifyPinCode = ""
                        verifyPatternPoints = listOf()
                        showError = false
                        showSaveButton = false
                    },
                    modifier = Modifier
                        .width(220.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Create",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Verification Step
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Verify Your ${if (passwordType == "PIN") "PIN" else "PATTERN"}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (showError) {
                    Text(
                        text = "Passcode doesn't match. Try again.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (passwordType == "PIN") {
                PinInput(
                    pin = verifyPinCode,
                    onPinChange = { newPin ->
                        if (newPin.length <= 6) {
                            verifyPinCode = newPin
                            if (newPin.length == 6) {
                                if (newPin == pinCode) {
                                    showSaveButton = true
                                } else {
                                    showError = true
                                    verifyPinCode = ""
                                    pinCode = ""
                                    step = 1
                                    showSaveButton = false
                                }
                            }
                        }
                    },
                    isVerification = true
                )
            } else {
                PatternInput(
                    selectedPoints = verifyPatternPoints,
                    onPatternChange = { pattern ->
                        verifyPatternPoints = pattern
                        if (pattern.size >= 4) {
                            if (pattern == patternPoints) {
                                showSaveButton = true
                            } else {
                                showError = true
                                verifyPatternPoints = listOf()
                                patternPoints = listOf()
                                step = 1
                                showSaveButton = false
                            }
                        }
                    },
                    isVerification = true
                )
            }

            // Show Save button only when verification is correct
            if (showSaveButton) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (passwordType == "PIN") {
                            savePassword(context, passwordType, pinCode)
                        } else {
                            savePassword(context, passwordType, patternPoints.joinToString(","))
                        }
                        onPasswordSaved(passwordType)
                    },
                    modifier = Modifier
                        .width(220.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Save",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PinInput(pin: String, onPinChange: (String) -> Unit, isVerification: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (!isVerification) {
            Text(
                text = "Create 6-Digit PIN",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
fun PatternInput(
    selectedPoints: List<Int>,
    onPatternChange: (List<Int>) -> Unit,
    isVerification: Boolean = false
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
        if (!isVerification) {
            Text(
                text = "Create Pattern",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

                                // Check if starting on a dot
                                val dotIndex = getDotIndex(offset, dotSizePx, spacingPx, patternSize)
                                if (dotIndex != -1) {
                                    currentPattern = listOf(dotIndex + 1) // Convert to 1-9
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
        if (selectedPoints.isNotEmpty() && !isVerification) {
            TextButton(
                onClick = { onPatternChange(listOf()) }
            ) {
                Text("Clear Pattern")
            }
        }
    }
}
private fun getIntermediateDots(start: Int, end: Int, patternSize: Int): List<Int> {
    if (start == end) return emptyList()

    val startRow = (start - 1) / patternSize
    val startCol = (start - 1) % patternSize
    val endRow = (end - 1) / patternSize
    val endCol = (end - 1) % patternSize

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

private fun savePassword(context: Context, type: String, password: String) {
    val sharedPref = context.getSharedPreferences("arklock_prefs", Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putInt("has_password", 1)
        putString("password_type", if (type == "PIN") "PIN" else "PATTERN")
        putString("password_value", password)
        apply()
    }
}