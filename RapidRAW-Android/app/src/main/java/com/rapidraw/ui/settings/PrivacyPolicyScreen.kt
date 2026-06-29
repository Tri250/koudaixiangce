package com.rapidraw.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Theme Colors ──
private val HasselbladOrange = Color(0xFFE85D04)
private val EditorBackground = Color(0xFF0D0D0D)
private val EditorSurface = Color(0xFF1A1A1A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF707070)

// ── Section data class ──
private data class PolicySection(
    val title: String,
    val content: String
)

private val sections = listOf(
    PolicySection(
        "Information Collection",
        "RapidRAW collects minimal data to improve your experience. The types of information we collect include:\n\n" +
                "• Device Information: We collect basic device information such as device model, operating system version, " +
                "and screen resolution. This helps us optimize the app's performance for your specific device.\n\n" +
                "• Crash Logs: When the app encounters an error, we collect anonymized crash reports that include " +
                "stack traces and technical diagnostic information. No personal photos or content are included in these reports.\n\n" +
                "• Usage Analytics: We collect anonymous usage data such as which features are most frequently used, " +
                "editing session durations, and feature interaction patterns. This data is aggregated and cannot be used to identify individual users.\n\n" +
                "• No Personal Identifiable Information: We do not collect your name, email address, phone number, " +
                "location data, or any other personally identifiable information unless you explicitly provide it (e.g., through the feedback form)."
    ),
    PolicySection(
        "Data Usage",
        "We use the collected data for the following purposes:\n\n" +
                "• App Improvement: Usage analytics help us understand which features are most valuable to our users, " +
                "allowing us to prioritize development efforts and improve the overall user experience.\n\n" +
                "• Bug Fixes: Crash logs and error reports enable our engineering team to identify, diagnose, and " +
                "resolve technical issues quickly and efficiently.\n\n" +
                "• Performance Optimization: Device information helps us optimize rendering performance, " +
                "memory usage, and battery consumption across different hardware configurations.\n\n" +
                "• No Data Sale: We do not sell, trade, rent, or otherwise transfer your data to third parties. " +
                "Your data is used exclusively for the purposes of improving RapidRAW."
    ),
    PolicySection(
        "Photo Privacy",
        "We take your photo privacy extremely seriously:\n\n" +
                "• Local Processing: All photo editing, including RAW file processing, color grading, LUT application, " +
                "and image adjustments, is performed entirely on your device. Your photos never leave your device during the editing process.\n\n" +
                "• No Photo Upload: We do not upload, transmit, or store your photos on any remote server or cloud service. " +
                "RapidRAW operates completely offline for photo processing.\n\n" +
                "• No Photo Access: The app only accesses photos that you explicitly select for editing. " +
                "We do not scan your photo library or access photos without your direct action.\n\n" +
                "• Export Control: When you export or share edited photos, you remain in full control of the destination. " +
                "RapidRAW does not intercept or copy exported images."
    ),
    PolicySection(
        "Third-Party Services",
        "RapidRAW uses the following third-party services and SDKs:\n\n" +
                "• TensorFlow Lite: Used for on-device machine learning features such as subject detection and " +
                "intelligent auto-enhancement. TensorFlow Lite processes all data locally on your device and does not collect " +
                "personally identifiable information.\n\n" +
                "• No Advertising SDKs: We do not integrate any advertising networks or ad-serving SDKs.\n\n" +
                "• No Analytics SDKs with PII: Any analytics services we use are configured to collect only " +
                "anonymized, aggregated data with no personally identifiable information.\n\n" +
                "• No Social Media SDKs: We do not embed social media SDKs that could track your activity."
    ),
    PolicySection(
        "Data Security",
        "We implement industry-standard security measures to protect your data:\n\n" +
                "• Encryption: All data stored by the app on your device is protected by the operating system's " +
                "built-in file-level encryption (Android FBE).\n\n" +
                "• Local Storage: App preferences, edit history, and settings are stored exclusively on your device. " +
                "No cloud synchronization of sensitive data occurs without your explicit consent.\n\n" +
                "• No Cloud Transmission: As all photo processing is performed locally, no photo data is transmitted " +
                "over the internet. Crash logs and analytics data are transmitted over encrypted HTTPS connections.\n\n" +
                "• Secure Communication: Any network communication between the app and our servers uses TLS 1.3 " +
                "encryption to prevent interception or tampering."
    ),
    PolicySection(
        "User Rights",
        "You have the following rights regarding your data:\n\n" +
                "• Delete Data: You can clear all app data at any time through your device's Settings > Apps > " +
                "RapidRAW > Storage > Clear Data. This removes all locally stored preferences and edit history.\n\n" +
                "• Opt-Out of Analytics: You can disable usage analytics in the app's Settings > Privacy menu. " +
                "When disabled, no analytics data will be collected or transmitted.\n\n" +
                "• Access Information: You can request information about what data we have collected about your " +
                "device by contacting support@rapidraw.app.\n\n" +
                "• Data Portability: Any data you create within the app (presets, LUTs, recipes) can be exported " +
                "and transferred at your discretion."
    ),
    PolicySection(
        "Contact",
        "If you have any questions, concerns, or requests regarding this Privacy Policy or our data practices, " +
                "please contact us at:\n\n" +
                "Email: support@rapidraw.app\n\n" +
                "We aim to respond to all inquiries within 48 hours. Your privacy matters to us, and we are committed " +
                "to addressing any concerns you may have."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Privacy Policy",
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
            Text(
                "Last Updated: June 15, 2026",
                color = TextTertiary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "This Privacy Policy explains how RapidRAW (\"we\", \"our\", or \"us\") collects, uses, " +
                        "and safeguards your information when you use our mobile application. By using RapidRAW, " +
                        "you agree to the collection and use of information in accordance with this policy.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(24.dp))

            sections.forEach { section ->
                SectionBlock(title = section.title, content = section.content)
                Spacer(Modifier.height(24.dp))
            }

            // Bottom spacing
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    content: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = HasselbladOrange,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            content,
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}