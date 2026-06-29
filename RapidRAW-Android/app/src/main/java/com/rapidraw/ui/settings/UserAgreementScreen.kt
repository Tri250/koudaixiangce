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
import androidx.compose.material3.HorizontalDivider
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

private data class AgreementSection(
    val title: String,
    val content: String
)

private val sections = listOf(
    AgreementSection(
        "1. Acceptance of Terms",
        "By downloading, installing, or using the RapidRAW mobile application (\"the App\"), you agree to be bound " +
                "by these Terms of Service (\"Terms\"). If you do not agree to these Terms, you must not download, install, " +
                "or use the App. Your continued use of the App following any changes to these Terms constitutes your " +
                "acceptance of the revised Terms.\n\n" +
                "You represent that you are at least 13 years of age (or the age of digital consent in your country) " +
                "and have the legal capacity to enter into these Terms. If you are under the age of 18, you must have " +
                "a parent or legal guardian review and consent to these Terms."
    ),
    AgreementSection(
        "2. Service Description",
        "RapidRAW is a professional RAW photo editing application designed for mobile devices. The App provides " +
                "the following core services:\n\n" +
                "• RAW file processing and editing for various camera formats (DNG, CR2, CR3, NEF, ARW, etc.)\n\n" +
                "• Professional-grade color grading tools and LUT (Look-Up Table) application\n\n" +
                "• Non-destructive editing with full edit history and undo/redo support\n\n" +
                "• Community features including LUT marketplace and editing recipe sharing\n\n" +
                "• Local, on-device processing — no cloud upload of your photos is required for editing\n\n" +
                "RapidRAW reserves the right to modify, suspend, or discontinue any aspect of the App at any time " +
                "without prior notice. We may also introduce new features, change existing functionality, or impose " +
                "limits on certain features."
    ),
    AgreementSection(
        "3. User Responsibilities",
        "As a user of RapidRAW, you agree to:\n\n" +
                "• Legal Use: Use the App only for lawful purposes and in compliance with all applicable local, " +
                "national, and international laws and regulations.\n\n" +
                "• Copyright Respect: You are solely responsible for ensuring that you have the necessary rights " +
                "and permissions to edit, modify, and share any photos you process through the App. You must not " +
                "use RapidRAW to edit or distribute copyrighted material without proper authorization.\n\n" +
                "• Community Conduct: When using community features such as the LUT marketplace or recipe sharing, " +
                "you agree to:\n" +
                "    - Share only content that you have the right to distribute\n" +
                "    - Not upload malicious, offensive, or illegal content\n" +
                "    - Not impersonate other users or engage in deceptive practices\n" +
                "    - Respect the intellectual property rights of other users\n\n" +
                "• Account Security: If the App offers account features, you are responsible for maintaining the " +
                "confidentiality of your account credentials and for all activities that occur under your account."
    ),
    AgreementSection(
        "4. Intellectual Property",
        "4.1 RapidRAW Intellectual Property\n\n" +
                "The App, including but not limited to its source code, design, user interface, graphics, logos, " +
                "trademarks, and the \"RapidRAW\" name and brand, are the exclusive intellectual property of the " +
                "RapidRAW development team and are protected by copyright, trademark, and other intellectual property laws.\n\n" +
                "You may not:\n" +
                "• Copy, modify, reverse engineer, decompile, or disassemble the App\n" +
                "• Create derivative works based on the App\n" +
                "• Remove, alter, or obscure any proprietary notices on the App\n" +
                "• Use the RapidRAW name, logo, or trademarks without prior written permission\n\n" +
                "4.2 User Content\n\n" +
                "You retain full ownership of your photos and any content you create using the App. RapidRAW does not " +
                "claim any ownership rights over your photos, edits, presets, or LUTs. By sharing content through the " +
                "App's community features, you grant RapidRAW a non-exclusive, royalty-free, worldwide license to " +
                "display and distribute that content within the App's community platform."
    ),
    AgreementSection(
        "5. Disclaimer of Warranty",
        "THE APP IS PROVIDED ON AN \"AS IS\" AND \"AS AVAILABLE\" BASIS, WITHOUT WARRANTIES OF ANY KIND, " +
                "EITHER EXPRESS OR IMPLIED. TO THE FULLEST EXTENT PERMITTED BY APPLICABLE LAW, RAPIDRAW DISCLAIMS " +
                "ALL WARRANTIES, INCLUDING BUT NOT LIMITED TO:\n\n" +
                "• Implied warranties of merchantability, fitness for a particular purpose, and non-infringement\n\n" +
                "• Warranties that the App will be uninterrupted, error-free, secure, or free from viruses or " +
                "other harmful components\n\n" +
                "• Warranties regarding the accuracy, reliability, or completeness of any content or information " +
                "provided through the App\n\n" +
                "• Warranties that any defects or errors will be corrected\n\n" +
                "Your use of the App is at your sole risk. You are solely responsible for any damage to your device " +
                "or loss of data that results from using the App."
    ),
    AgreementSection(
        "6. Limitation of Liability",
        "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, IN NO EVENT SHALL RAPIDRAW, ITS DEVELOPERS, " +
                "AFFILIATES, OR LICENSORS BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR " +
                "PUNITIVE DAMAGES, INCLUDING BUT NOT LIMITED TO:\n\n" +
                "• Loss of profits, data, use, goodwill, or other intangible losses\n\n" +
                "• Damages resulting from the use or inability to use the App\n\n" +
                "• Unauthorized access to or alteration of your data or content\n\n" +
                "• Conduct or content of any third party on the App\n\n" +
                "In no event shall RapidRAW's total liability to you for all claims arising from or relating to " +
                "these Terms or your use of the App exceed the amount you paid to RapidRAW (if any) in the twelve " +
                "(12) months preceding the claim, or one hundred U.S. dollars (USD $100), whichever is greater."
    ),
    AgreementSection(
        "7. Termination",
        "These Terms remain effective until terminated by either you or RapidRAW.\n\n" +
                "You may terminate these Terms at any time by uninstalling the App and discontinuing your use of " +
                "the App's services.\n\n" +
                "RapidRAW may terminate or suspend your access to the App immediately, without prior notice or " +
                "liability, for any reason, including but not limited to your breach of these Terms. Upon termination, " +
                "your right to use the App will cease immediately.\n\n" +
                "Provisions of these Terms that by their nature should survive termination shall survive, including " +
                "but not limited to intellectual property rights, disclaimers, limitations of liability, and " +
                "governing law provisions."
    ),
    AgreementSection(
        "8. Governing Law",
        "These Terms shall be governed by and construed in accordance with the laws of the State of California, " +
                "United States of America, without regard to its conflict of law provisions.\n\n" +
                "Any dispute arising from or relating to these Terms or your use of the App shall be subject to " +
                "the exclusive jurisdiction of the state and federal courts located in San Francisco County, California.\n\n" +
                "If any provision of these Terms is held to be invalid or unenforceable, the remaining provisions " +
                "shall continue in full force and effect. The failure of RapidRAW to enforce any right or provision " +
                "of these Terms shall not constitute a waiver of such right or provision."
    ),
    AgreementSection(
        "9. Changes to Terms",
        "RapidRAW reserves the right to modify or replace these Terms at any time at our sole discretion. " +
                "If a revision is material, we will make reasonable efforts to provide at least 30 days' notice " +
                "prior to any new terms taking effect.\n\n" +
                "We will notify you of changes by:\n" +
                "• Posting the updated Terms within the App\n" +
                "• Displaying an in-app notification or dialog\n" +
                "• Updating the \"Effective Date\" at the top of this document\n\n" +
                "By continuing to access or use the App after revisions become effective, you agree to be bound " +
                "by the revised Terms. If you do not agree to the new Terms, you must stop using the App."
    ),
    AgreementSection(
        "10. Contact",
        "If you have any questions, concerns, or feedback regarding these Terms of Service, please contact us at:\n\n" +
                "Email: legal@rapidraw.app\n\n" +
                "We value your input and will make every effort to address your concerns in a timely manner. " +
                "For general support inquiries, please use support@rapidraw.app instead."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgreementScreen(
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
                        "Terms of Service",
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
                "Version 1.0",
                color = TextTertiary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Effective Date: June 15, 2026",
                color = TextTertiary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(6.dp))

            HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f), thickness = 1.dp)

            Spacer(Modifier.height(16.dp))

            Text(
                "Please read these Terms of Service carefully before using the RapidRAW application. " +
                        "These Terms constitute a legally binding agreement between you (\"User\", \"you\", or \"your\") " +
                        "and RapidRAW (\"we\", \"our\", or \"us\") regarding your use of the RapidRAW mobile application.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(24.dp))

            sections.forEach { section ->
                AgreementSectionBlock(title = section.title, content = section.content)
                Spacer(Modifier.height(24.dp))
            }

            // Bottom spacing
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AgreementSectionBlock(
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