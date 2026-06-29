package com.rapidraw.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Theme Colors ──
private val HasselbladOrange = Color(0xFFE85D04)
private val EditorBackground = Color(0xFF0D0D0D)
private val EditorSurface = Color(0xFF1A1A1A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF707070)

private val feedbackTypes = listOf("Bug Report", "Feature Request", "Improvement", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var feedbackType by remember { mutableStateOf(feedbackTypes[0]) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var hasAttachment by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Validation
    val titleError = title.isNotEmpty() && title.length < 3
    val descriptionError = description.isNotEmpty() && description.length < 10
    val isFormValid = title.length >= 3 && description.length >= 10

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = EditorSurface,
        unfocusedContainerColor = EditorSurface,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = HasselbladOrange,
        focusedBorderColor = HasselbladOrange,
        unfocusedBorderColor = TextTertiary.copy(alpha = 0.3f),
        focusedLabelColor = HasselbladOrange,
        unfocusedLabelColor = TextTertiary,
        errorBorderColor = Color(0xFFCF6679),
        errorLabelColor = Color(0xFFCF6679)
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusManager.clearFocus() }
            ),
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Send Feedback",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Text(
                "We'd love to hear from you!",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your feedback helps us make RapidRAW better. Tell us what's on your mind.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            // Feedback Type Dropdown
            Text(
                "Feedback Type",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = feedbackType,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = textFieldColors,
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(EditorSurface)
                ) {
                    feedbackTypes.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    type,
                                    color = if (type == feedbackType) HasselbladOrange else TextPrimary,
                                    fontWeight = if (type == feedbackType) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                feedbackType = type
                                dropdownExpanded = false
                            },
                            modifier = Modifier.background(EditorSurface)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("Brief summary of your feedback", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Title must be at least 3 characters", color = Color(0xFFCF6679)) }
                } else null,
                colors = textFieldColors,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Tell us in detail...", color = TextTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                minLines = 5,
                isError = descriptionError,
                supportingText = if (descriptionError) {
                    { Text("Description must be at least 10 characters", color = Color(0xFFCF6679)) }
                } else null,
                colors = textFieldColors,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (optional)") },
                placeholder = { Text("So we can follow up with you", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Attach Screenshot Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(EditorSurface)
                    .clickable { hasAttachment = !hasAttachment }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = if (hasAttachment) HasselbladOrange else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (hasAttachment) "Screenshot attached" else "Attach Screenshot",
                    color = if (hasAttachment) HasselbladOrange else TextSecondary,
                    fontSize = 14.sp
                )
                if (hasAttachment) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = HasselbladOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Submit Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    isSubmitting = true
                    scope.launch {
                        delay(1500)
                        isSubmitting = false
                        showSuccessDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isFormValid && !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HasselbladOrange,
                    disabledContainerColor = HasselbladOrange.copy(alpha = 0.3f),
                    contentColor = TextPrimary,
                    disabledContentColor = TextPrimary.copy(alpha = 0.4f)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = TextPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Submitting...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Submit Feedback",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        Dialog(
            onDismissRequest = {
                showSuccessDialog = false
                Toast.makeText(context, "Feedback submitted. Thank you!", Toast.LENGTH_SHORT).show()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EditorSurface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(HasselbladOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Thank You!",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your feedback has been submitted successfully. We appreciate your input!",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HasselbladOrange)
                            .clickable {
                                showSuccessDialog = false
                                Toast.makeText(context, "Feedback submitted. Thank you!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Done",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}