package com.rapidraw.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 隐私政策页面 — 涵盖数据收集、存储、使用、第三方共享、用户权利、儿童隐私、联系方式。
 * 符合中国《个人信息保护法》《数据安全法》合规要求。
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding(),
    ) {
        // ── Top Bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_revert),
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Text(
                text = "隐私政策",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // ── Scrollable Content ───────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 更新日期
            Text(
                text = "更新日期：2025年6月1日",
                color = TextTertiary,
                fontSize = 12.sp,
            )
            Text(
                text = "生效日期：2025年6月1日",
                color = TextTertiary,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 引言
            PolicySectionTitle("引言")
            PolicyParagraph(
                "RapidRAW（以下简称"我们"）深知个人信息对您的重要性，我们将按照法律法规的规定，" +
                    "保护您的个人信息及隐私安全。我们制定本隐私政策以帮助您了解我们如何收集、使用、存储和保护您的个人信息。" +
                    "请您在使用我们的产品和服务前，仔细阅读并充分理解本隐私政策。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 一、我们收集的信息
            PolicySectionTitle("一、我们收集的信息")
            PolicyParagraph(
                "为了向您提供 RAW 照片编辑服务，我们会收集以下信息：",
            )
            PolicyBulletItem("设备信息：设备型号、操作系统版本、屏幕分辨率、GPU 信息，用于优化图像处理性能")
            PolicyBulletItem("应用使用信息：功能使用频次、崩溃日志、性能指标，用于改善产品稳定性和用户体验")
            PolicyBulletItem("照片元数据：EXIF 信息（相机型号、镜头参数、拍摄设置），仅用于图像处理和展示，不会上传至服务器")
            PolicyBulletItem("用户设置：编辑偏好、预设收藏、界面设置，存储于本地设备")
            PolicyParagraph(
                "我们不会收集您的照片原始文件、地理位置信息、通讯录、短信等与核心功能无关的个人信息。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 二、信息存储
            PolicySectionTitle("二、信息存储")
            PolicyParagraph(
                "2.1 存储地点：您的个人信息存储在中华人民共和国境内。如需跨境传输，我们将按照法律法规要求进行安全评估并取得您的单独同意。",
            )
            PolicyParagraph(
                "2.2 存储期限：我们仅在为您提供服务所必需的期限内保留您的个人信息。超出必要期限后，我们将删除或匿名化处理您的个人信息。" +
                    "崩溃日志保留期限为 7 天，超过期限自动删除。",
            )
            PolicyParagraph(
                "2.3 存储安全：我们采用行业标准的安全技术和管理措施保护您的个人信息，包括数据加密、访问控制、安全审计等。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 三、信息使用
            PolicySectionTitle("三、我们如何使用信息")
            PolicyParagraph("我们收集的信息将用于以下目的：")
            PolicyBulletItem("提供和维护 RAW 照片编辑的核心功能")
            PolicyBulletItem("优化图像处理引擎性能，适配不同设备")
            PolicyBulletItem("诊断和修复应用崩溃及性能问题")
            PolicyBulletItem("提供个性化推荐，如预设和编辑配方推荐")
            PolicyBulletItem("改进产品功能和用户体验")
            PolicyBulletItem("遵守法律法规要求")
            PolicyParagraph(
                "我们不会将您的个人信息用于本隐私政策未载明的其他目的。如需改变使用目的，我们将再次征得您的同意。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 四、第三方共享
            PolicySectionTitle("四、第三方共享")
            PolicyParagraph(
                "4.1 我们不会向第三方出售您的个人信息。",
            )
            PolicyParagraph(
                "4.2 以下情况下，我们可能会共享您的信息：",
            )
            PolicyBulletItem("获得您的明确同意后")
            PolicyBulletItem("基于法律法规、诉讼、仲裁的需要")
            PolicyBulletItem("与关联公司共享：仅为实现服务功能所必需，且受本隐私政策约束")
            PolicyBulletItem("与授权合作伙伴共享：仅限于实现特定功能（如崩溃日志分析服务），我们会与其签署严格的数据保护协议")
            PolicyParagraph(
                "4.3 我们会对第三方进行严格的尽职调查，以保护您的个人信息安全。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 五、用户权利
            PolicySectionTitle("五、您的权利")
            PolicyParagraph("根据相关法律法规，您享有以下权利：")
            PolicyBulletItem("查阅权：您有权查阅您的个人信息")
            PolicyBulletItem("更正权：如我们发现您的信息有误，我们将根据您的要求进行更正")
            PolicyBulletItem("删除权：您可以要求我们删除您的个人信息，我们将在 15 个工作日内完成处理")
            PolicyBulletItem("撤回同意权：您可以撤回之前给予的授权同意，撤回后我们将不再处理相应信息")
            PolicyBulletItem("账号注销权：您可以申请注销账号，注销后我们将删除或匿名化您的所有个人信息")
            PolicyBulletItem("获取副本：您有权获取您的个人信息副本")
            PolicyParagraph(
                "行使上述权利的方式：请在应用内"设置 → 反馈与建议"中提交请求，或通过本政策列明的联系方式与我们联系。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 六、儿童隐私
            PolicySectionTitle("六、儿童隐私保护")
            PolicyParagraph(
                "6.1 我们的产品和服务主要面向成年人。如果您是未满 14 周岁的未成年人，请在您的监护人指导下使用我们的产品和服务，" +
                    "并在您的监护人阅读和同意本隐私政策后使用。",
            )
            PolicyParagraph(
                "6.2 我们不会主动收集未满 14 周岁未成年人的个人信息。如果我们发现在未获得监护人同意的情况下收集了未成年人的个人信息，" +
                    "我们将尽快删除相关信息。",
            )
            PolicyParagraph(
                "6.3 监护人有权查阅、更正、删除未成年人的个人信息，请通过本政策列明的联系方式与我们联系。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 七、隐私政策变更
            PolicySectionTitle("七、隐私政策的变更")
            PolicyParagraph(
                "我们可能会适时修订本隐私政策。当隐私政策发生变更时，我们将在应用内以弹窗或公告方式通知您。" +
                    "对于重大变更，我们会征求您的明确同意。未经您同意，我们不会削减您按照本隐私政策所应享有的权利。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 八、联系方式
            PolicySectionTitle("八、联系我们")
            PolicyParagraph(
                "如您对本隐私政策有任何疑问、意见或建议，请通过以下方式与我们联系：",
            )
            PolicyBulletItem("电子邮箱：privacy@rapidraw.app")
            PolicyBulletItem("意见反馈：应用内"设置 → 反馈与建议"")
            PolicyBulletItem("邮寄地址：广东省深圳市南山区 RapidRAW 隐私保护团队")
            PolicyParagraph(
                "我们将在 15 个工作日内回复您的请求。",
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PolicySectionTitle(title: String) {
    Text(
        text = title,
        color = HasselbladOrange,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PolicyParagraph(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PolicyBulletItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 4.dp),
    ) {
        Text(
            text = "•",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
    }
}
