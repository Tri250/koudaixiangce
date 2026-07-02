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
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 用户协议 / 服务条款页面 — 涵盖服务描述、用户义务、知识产权、免责声明、终止、管辖法律。
 * 符合中国法律法规要求。
 */
@Composable
fun UserAgreementScreen(
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
                text = "用户协议",
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
                text = "更新日期：2026年6月30日",
                color = TextTertiary,
                fontSize = 12.sp,
            )
            Text(
                text = "生效日期：2026年6月30日",
                color = TextTertiary,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 引言
            AgreementSectionTitle("引言")
            AgreementParagraph(
                "欢迎您使用 RapidRAW！本用户协议（以下简称\"本协议\"）是您与 RapidRAW（以下简称\"我们\"）之间" +
                    "关于使用 RapidRAW 移动应用程序及相关服务所订立的协议。请您在使用我们的服务前，" +
                    "仔细阅读并充分理解本协议各条款。一旦您开始使用我们的服务，即视为您已阅读并同意本协议的约束。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 一、服务描述
            AgreementSectionTitle("一、服务描述")
            AgreementParagraph(
                "1.1 RapidRAW 是一款专业的 RAW 格式照片编辑应用，提供 RAW 解码、图像调整、胶片模拟、" +
                    "预设管理、LUT 应用、批量导出等功能。",
            )
            AgreementParagraph(
                "1.2 我们提供的具体服务内容以应用实际功能为准。我们有权根据产品发展需要，增加、变更或终止部分服务功能，" +
                    "并将通过应用内公告等方式通知您。",
            )
            AgreementParagraph(
                "1.3 您理解并同意，部分功能可能需要特定硬件支持（如 GPU 加速、HDR 显示），具体功能可用性取决于您的设备能力。",
            )
            AgreementParagraph(
                "1.4 云端同步功能（如预设同步、配方分享）需要网络连接，且可能因后端服务维护或升级而暂时不可用。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 二、用户义务
            AgreementSectionTitle("二、用户义务")
            AgreementParagraph(
                "2.1 您应当合法使用本应用，不得利用本应用从事任何违反法律法规的活动。",
            )
            AgreementParagraph(
                "2.2 您应当妥善保管自己的账号信息，因您的原因导致账号泄露所引起的损失由您自行承担。",
            )
            AgreementParagraph(
                "2.3 您不得对本应用进行反编译、反汇编、修改、拆解或以其他方式试图获取本应用的源代码。",
            )
            AgreementParagraph(
                "2.4 您不得使用自动化工具（如机器人、爬虫等）访问或操控本应用的服务。",
            )
            AgreementParagraph(
                "2.5 您在社区中分享的内容（编辑配方、LUT 等）应当遵守法律法规，不得包含违法、色情、暴力、侵权等不当内容。",
            )
            AgreementParagraph(
                "2.6 您理解并同意，我们有权对违反上述义务的用户采取警告、限制功能、封禁账号等措施。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 三、知识产权
            AgreementSectionTitle("三、知识产权")
            AgreementParagraph(
                "3.1 本应用的所有知识产权（包括但不限于软件代码、界面设计、图标、商标、品牌标识等）均归我们或相关权利人所有。",
            )
            AgreementParagraph(
                "3.2 我们授予您非独占的、不可转让的、有限的许可，允许您在个人设备上安装和使用本应用。" +
                    "您不得将本应用出租、出借、再授权或以其他方式向第三方提供。",
            )
            AgreementParagraph(
                "3.3 您使用本应用编辑的照片、创建的预设和配方的知识产权归您所有。" +
                    "您对您创作的内容享有完整的知识产权。",
            )
            AgreementParagraph(
                "3.4 您在社区中分享的配方和预设，授予我们非独占的、全球性的、免费的许可，用于在社区中展示和分发。" +
                    "您保留对分享内容的所有权利。",
            )
            AgreementParagraph(
                "3.5 内置于本应用的胶片模拟预设（如哈苏大师配方）为我们的专有内容，" +
                    "您可以在应用内使用但不得将其独立于应用进行分发或销售。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 四、免责声明
            AgreementSectionTitle("四、免责声明")
            AgreementParagraph(
                "4.1 本应用按\"现状\"和\"可获得性\"基础提供服务，我们不对服务的及时性、安全性、准确性做任何明示或暗示的保证。",
            )
            AgreementParagraph(
                "4.2 因不可抗力（包括但不限于自然灾害、政府行为、网络攻击等）导致服务中断或数据丢失，我们不承担责任。",
            )
            AgreementParagraph(
                "4.3 因您自身原因（如设备故障、操作不当等）导致的数据丢失，我们不承担责任。建议您定期备份重要数据。",
            )
            AgreementParagraph(
                "4.4 我们不对第三方服务（如云存储服务）的可用性和安全性做出保证。",
            )
            AgreementParagraph(
                "4.5 在适用法律允许的最大范围内，我们对您因使用本服务而产生的任何间接、附带、特殊或后果性损害不承担责任。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 五、协议终止
            AgreementSectionTitle("五、协议终止")
            AgreementParagraph(
                "5.1 您有权随时停止使用本应用并卸载应用，本协议自您停止使用之日起终止。",
            )
            AgreementParagraph(
                "5.2 如您严重违反本协议的规定，我们有权单方面终止向您提供服务，并有权采取必要的法律措施。",
            )
            AgreementParagraph(
                "5.3 协议终止后，我们无义务为您保留账号中的任何信息，但法律法规另有规定的除外。",
            )
            AgreementParagraph(
                "5.4 协议终止不影响任何一方在终止前已产生的权利和义务。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 六、管辖法律
            AgreementSectionTitle("六、管辖法律与争议解决")
            AgreementParagraph(
                "6.1 本协议的订立、执行和解释均适用中华人民共和国法律（不含港澳台地区法律）。",
            )
            AgreementParagraph(
                "6.2 因本协议引起的或与本协议有关的任何争议，双方应首先通过友好协商解决。" +
                    "协商不成的，任何一方均有权向有管辖权的人民法院提起诉讼。",
            )
            AgreementParagraph(
                "6.3 本协议的任何条款如因与相关法律法规相抵触而无效，不影响其他条款的效力。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 七、其他
            AgreementSectionTitle("七、其他")
            AgreementParagraph(
                "7.1 我们有权根据法律法规变化及产品发展需要修订本协议，修订后的协议将在应用内公布。" +
                    "如您在协议修订后继续使用本应用，即视为您接受修订后的协议。",
            )
            AgreementParagraph(
                "7.2 本协议的标题仅为方便阅读，不影响协议条款的含义和解释。",
            )
            AgreementParagraph(
                "7.3 如您对本协议有任何疑问，请通过应用内\"设置 → 反馈与建议\"或发送邮件至 legal@rapidraw.app 与我们联系。",
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun AgreementSectionTitle(title: String) {
    Text(
        text = title,
        color = HasselbladOrange,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AgreementParagraph(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
