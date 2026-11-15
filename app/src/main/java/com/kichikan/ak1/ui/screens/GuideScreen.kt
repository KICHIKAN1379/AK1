package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.ui.theme.Neon

private data class GuideTopic(
    val title: String,
    val summary: String,
    val purpose: String,
    val steps: List<String>,
    val note: String
)

private val quickGuideTopics = listOf(
    GuideTopic(
        "آشنایی با نگاه مربی",
        "نگاه مربی ابزار مدیریت حلقه و پیگیری مسیر رشد اعضاست؛ نه یک شبکه اجتماعی و نه یک حساب کاربری آنلاین.",
        "اطلاعات حلقه و فعالیت‌های ثبت‌شده در خود برنامه و روی دستگاه نگهداری می‌شوند. مربی از بخش‌های مختلف برای ثبت رویدادها و مرور وضعیت حلقه استفاده می‌کند.",
        listOf("برنامه را باز کن و مشخصات اولیه حلقه را تکمیل کن.", "از نوار پایین، خانه، اعضا، پیشرفت، فروشگاه و تنظیمات را بشناس.", "برای آشنایی با هر قابلیت، از همین راهنما وارد تب مربوط شو."),
        "برای استفاده روزمره، اطلاعات را همان زمان وقوع ثبت کن؛ گزارش‌ها بر اساس داده‌هایی هستند که در برنامه ثبت شده‌اند."
    ),
    GuideTopic(
        "راه‌اندازی حلقه",
        "قبل از ثبت فعالیت‌ها، هویت حلقه و برنامه برگزاری آن را مشخص کن.",
        "مشخصات حلقه، ظاهر برنامه و روزهای کلاس کمک می‌کنند محیط برنامه با شیوه کار مربی هماهنگ شود.",
        listOf("در تنظیمات، «ویرایش پروفایل حلقه» را باز کن.", "نام حلقه، نام کاربری مربی و در صورت نیاز تصویر و بنر حلقه را ثبت کن.", "در «روزهای کلاس»، روزهای برگزاری را انتخاب کن.", "در «صدا، پس‌زمینه و تم» ظاهر و موسیقی را مطابق نیاز تنظیم کن."),
        "تنظیم روزهای کلاس به برنامه‌ریزی مربی کمک می‌کند؛ خودش جلسه یا حضور و غیاب ایجاد نمی‌کند."
    ),
    GuideTopic(
        "ثبت اعضا",
        "فهرست اعضا پایه اصلی ثبت فعالیت‌ها، مأموریت‌ها و گزارش‌های فردی است.",
        "برای هر نوجوان یک پرونده مستقل وجود دارد که اطلاعات و سوابق ثبت‌شده او را نگه می‌دارد.",
        listOf("از نوار پایین وارد «اعضا» شو.", "عضو جدید را با اطلاعات لازم ثبت کن.", "برای مشاهده جزئیات، روی نام یا کارت عضو بزن.", "در پروفایل عضو، سوابق و اطلاعات مربوط به همان عضو را مرور کن."),
        "اطلاعات را با دقت و فقط به اندازه نیاز ثبت کن. آرشیوکردن عضو با حذف دائمی او یکسان نیست."
    ),
    GuideTopic(
        "جلسه و حضور و غیاب",
        "جلسات و حضور و غیاب را برای ثبت منظم فعالیت‌های حضوری حلقه به کار ببر.",
        "ابتدا جلسه را در بخش جلسات ثبت کن و سپس حضور اعضا را برای همان جلسه مشخص کن.",
        listOf("از خانه یا بخش خدمات وارد «جلسات» شو.", "جلسه جدید را با تاریخ و اطلاعات لازم ثبت کن.", "فهرست جلسات را باز کن و جلسه موردنظر را انتخاب کن.", "در صفحه حضور و غیاب، وضعیت هر عضو را ثبت و ذخیره کن.", "برای اصلاح، همان جلسه را دوباره باز کن."),
        "ثبت جلسه با ثبت حضور اعضا دو کار جداست. صرف ایجاد جلسه به معنی حاضر بودن همه اعضا نیست."
    ),
    GuideTopic(
        "اولین مأموریت و مرور حلقه",
        "با یک مأموریت ساده، چرخه ثبت فعالیت و مشاهده نتیجه را تجربه کن.",
        "مربی مأموریت را تعریف می‌کند و پس از انجام آن، ثبت انجام مأموریت و دریافت پاداش را برای عضو تأیید می‌کند.",
        listOf("به بخش پیشرفت و صفحه مأموریت‌ها برو.", "مأموریت مناسب را ایجاد یا انتخاب کن.", "پس از انجام مأموریت، دکمه ثبت همان مأموریت را بزن.", "عضو انجام‌دهنده را انتخاب و ثبت پاداش را تأیید کن.", "به خانه یا پروفایل عضو برگرد و نتیجه ثبت‌شده را بررسی کن."),
        "مأموریت مخفی یک‌بارمصرف است: پس از ثبت دریافت برای یک عضو، همان مأموریت برای او دوباره قابل دریافت نیست."
    )
)

private val fullGuideTopics = listOf(
    GuideTopic(
        "خانه",
        "خانه نقطه شروع کار روزانه مربی است و نمای فشرده‌ای از وضعیت حلقه ارائه می‌کند.",
        "در خانه می‌توانی هویت حلقه، خلاصه وضعیت و میانبرهای پرکاربرد را ببینی.",
        listOf("برای ورود به هر بخش از نوار پایین استفاده کن.", "میانبرهای خانه را از تنظیمات انتخاب کن؛ حداکثر چهار میانبر نمایش داده می‌شود.", "برای مشاهده جزئیات نبض هفتگی، کارت مربوط را باز کن."),
        "خانه جایگزین ثبت اطلاعات در بخش تخصصی نیست؛ برای اصلاح داده‌ها به صفحه همان قابلیت برو."
    ),
    GuideTopic(
        "اعضا و پرونده فردی",
        "بخش اعضا محل نگهداری فهرست و پرونده هر عضو است.",
        "از این بخش می‌توانی عضو را پیدا کنی، پروفایلش را ببینی و سوابق ثبت‌شده را مرور کنی.",
        listOf("با جست‌وجوی نام، عضو موردنظر را پیدا کن.", "از فیلترها و مرتب‌سازی برای محدودکردن فهرست استفاده کن.", "با بازکردن پروفایل، اطلاعات، دارایی‌های کسب‌شده و تاریخچه عضو را ببین.", "در صورت نیاز از گزارش عضو خروجی بگیر."),
        "گزارش فقط داده‌های ثبت‌شده در برنامه را منعکس می‌کند؛ نبود رکورد به‌تنهایی اثبات نمی‌کند که فعالیتی در دنیای واقعی انجام نشده است."
    ),
    GuideTopic(
        "جلسات",
        "جلسات برای ثبت رویدادهای برگزاری حلقه استفاده می‌شوند.",
        "فهرست جلسات، مسیر دسترسی به اطلاعات هر جلسه و حضور و غیاب آن است.",
        listOf("جلسه را با تاریخ و مشخصات لازم ثبت کن.", "از فهرست، جلسه موردنظر را باز کن.", "برای اصلاح اطلاعات، همان جلسه را ویرایش کن.", "برای ثبت حضور اعضا به صفحه حضور و غیاب برو."),
        "جلسه و حضور و غیاب دو موجودیت جدا هستند؛ حضور هر عضو باید در بخش مربوط ثبت شود."
    ),
    GuideTopic(
        "حضور و غیاب",
        "این بخش وضعیت حضور اعضا را برای یک جلسه مشخص ثبت می‌کند.",
        "نمای هفتگی و ماهانه برای مرور روزها و وضعیت‌های ثبت‌شده در دسترس است.",
        listOf("بازه هفتگی یا ماهانه را انتخاب کن.", "روز یا جلسه موردنظر را باز کن.", "برای هر عضو یکی از وضعیت‌های حاضر، غایب، تأخیر، موجه یا بدون ثبت را انتخاب کن.", "آمار خلاصه را برای کنترل ثبت‌ها مرور کن."),
        "«بدون ثبت» با «غایب» یکی نیست. تا وقتی وضعیت عضو ثبت نشده، او را غایب محسوب نکن."
    ),
    GuideTopic(
        "مأموریت‌ها",
        "مأموریت‌ها فعالیت‌هایی هستند که مربی برای پیگیری مشارکت و رشد اعضا تعریف می‌کند.",
        "مأموریت عادی، مأموریت مخفی و پازل گروهی مسیرهای جداگانه‌ای دارند.",
        listOf("مأموریت عادی را در بخش مأموریت‌ها تعریف کن.", "پس از انجام، دکمه ثبت روی همان مأموریت را بزن.", "عضو انجام‌دهنده را انتخاب و ثبت پاداش را تأیید کن.", "برای مأموریت گروهی، اعضای شرکت‌کننده را انتخاب کن.", "مأموریت مخفی را از بخش مربوط مدیریت کن؛ پاداش آن برای هر عضو فقط یک‌بار قابل دریافت است.", "پازل گروهی را از مسیر مستقل خودش مدیریت کن."),
        "ثبت مأموریت به معنی تأیید مربی است. پیش از تأیید، عضو درست و نوع مأموریت را بررسی کن."
    ),
    GuideTopic(
        "پیشرفت و اقتصاد",
        "پیشرفت، امتیازها و دارایی‌های درون برنامه را از هم تفکیک می‌کند.",
        "XP برای رشد سطح و رتبه است؛ امتیاز برای خرید در فروشگاه و الماس برای امکانات شخصی‌سازی به کار می‌رود.",
        listOf("در بخش پیشرفت، رتبه و سطح اعضا را مرور کن.", "تغییرات و پاداش‌ها را از مسیر ثبت مربوط انجام بده.", "در فروشگاه، هزینه هر قلم را پیش از خرید بررسی کن.", "تاریخچه عضو را برای پیگیری رویدادهای ثبت‌شده ببین."),
        "XP، امتیاز و الماس سه موجودی مستقل‌اند؛ آن‌ها را به‌جای یکدیگر در نظر نگیر."
    ),
    GuideTopic(
        "نقشه کمال",
        "نقشه کمال مسیر رشد عضو را در دو شاخه توانمندی و خودسازی نمایش می‌دهد.",
        "مربی از پالایشگاه ساختار و موارد قابل پیگیری را تنظیم می‌کند و وضعیت عضو را در صفحه نقشه مشاهده می‌کند.",
        listOf("از پروفایل عضو وارد نقشه کمال شو.", "دو شاخه توانمندی و خودسازی را جداگانه مرور کن.", "برای تنظیم ساختار و موارد نقشه، از پالایشگاه و تنظیمات نقشه کمال استفاده کن.", "پیشرفت را بر اساس شواهد و ثبت‌های واقعی به‌روزرسانی کن."),
        "نقشه کمال ابزار پیگیری تربیتی است؛ امتیازهای آن را بدون مشاهده و ارزیابی واقعی ثبت نکن."
    ),
    GuideTopic(
        "کتاب‌خوانی",
        "بخش کتاب‌خوانی برای ثبت سوابق کتاب‌های تکمیل‌شده اعضاست.",
        "سوابق ثبت‌شده در این بخش در پروفایل و گزارش‌های مرتبط قابل استفاده‌اند.",
        listOf("وارد بخش کتاب‌خوانی شو.", "عضو و کتاب مربوط را انتخاب کن.", "اطلاعات کتاب و تاریخ تکمیل را ثبت کن.", "برای مرور، سوابق ثبت‌شده را باز کن."),
        "اگر رکورد تکمیل کتاب وجود ندارد، این موضوع لزوماً به معنی مطالعه‌نکردن عضو نیست."
    ),
    GuideTopic(
        "دستیار مربی",
        "دستیار مربی برای مرور هشدارها و وضعیت‌های نیازمند پیگیری طراحی شده است.",
        "در تب «دستیار»، اعضا و هشدارهای مربوط به آن‌ها را می‌بینی. هشدار تا زمان اقدام و تأیید مربی باز می‌ماند.",
        listOf("عضو دارای هشدار را از فهرست انتخاب کن.", "تب هشدار مرتبط را باز کن و جزئیات را بخوان.", "پس از پیگیری واقعی، گزینه «انجام شد» را بزن.", "برای دیدن نمای کلی عضو، تب گزارش کلی را باز کن.", "برای خروجی گزارش، گزینه خروجی تصویر را به کار ببر."),
        "صرف بازکردن هشدار آن را نمی‌بندد. هشدارها بر اساس داده‌های ثبت‌شده و قواعد برنامه ساخته می‌شوند."
    ),
    GuideTopic(
        "دستیار هوشمند",
        "تب هوشمند پاسخ پرسش‌های آماده را با اتکا به داده‌های محلی برنامه ارائه می‌کند.",
        "این بخش برای مرور اطلاعات ثبت‌شده است و به‌جای مربی تصمیم تربیتی نمی‌گیرد.",
        listOf("وارد تب «هوشمند» شو.", "یکی از پرسش‌های آماده را انتخاب کن.", "شرح پرسش و محدوده داده را بخوان.", "پیش از اجرا، پرسش را تأیید کن.", "نتیجه و زمان استخراج را مرور کن."),
        "پاسخ‌ها بر اساس داده‌های موجود در همین برنامه ساخته می‌شوند؛ نبود داده را نباید به‌عنوان نبود فعالیت تعبیر کرد."
    ),
    GuideTopic(
        "فروشگاه",
        "فروشگاه محل خرج‌کردن امتیاز برای اقلام قابل خرید است.",
        "اقلامی مانند آواتار، قاب و جایزه در دسته‌های جدا نمایش داده می‌شوند.",
        listOf("دسته موردنظر را باز کن.", "جزئیات و هزینه قلم را بررسی کن.", "خرید را فقط پس از اطمینان تأیید کن.", "اقلام کسب‌شده عضو را در موجودی پروفایل او مرور کن."),
        "نشان‌ها با سطح عضو مرتبط‌اند و مانند اقلام فروشگاه خریدنی نیستند."
    ),
    GuideTopic(
        "پالایشگاه",
        "پالایشگاه ابزار تنظیم و مدیریت محتوای قابل استفاده در حلقه است.",
        "بسته به بخش انتخابی، مربی می‌تواند مأموریت‌ها، جوایز، دارایی‌ها، گردونه و تنظیمات نقشه کمال را مدیریت کند.",
        listOf("از تنظیمات وارد پالایشگاه شو.", "بخش موردنظر را انتخاب کن.", "پیش از ذخیره، عنوان، نوع و پاداش را بازبینی کن.", "برای مأموریت مخفی، محدودیت یک‌بار دریافت هر عضو را در نظر بگیر."),
        "تغییر در کاتالوگ یا پاداش‌ها می‌تواند بر استفاده بعدی اعضا اثر بگذارد؛ قبل از ثبت نهایی، جزئیات را کنترل کن."
    ),
    GuideTopic(
        "تنظیمات و پشتیبان",
        "تنظیمات برای شخصی‌سازی برنامه و نگهداری امن‌تر اطلاعات حلقه است.",
        "در این بخش پروفایل، ظاهر، جمله‌های روز، روزهای کلاس، میانبرها، ورود و خروج اطلاعات و بازنشانی قرار دارند.",
        listOf("برای تغییر ظاهر و موسیقی، «صدا، پس‌زمینه و تم» را باز کن.", "میانبرهای خانه را مطابق نیاز انتخاب و ذخیره کن.", "برای نگهداری نسخه پشتیبان، از خروجی اطلاعات استفاده کن.", "برای بازیابی، فایل پشتیبان سازگار را وارد کن.", "بازنشانی کارخانه‌ای را فقط پس از تهیه نسخه پشتیبان و اطمینان کامل اجرا کن."),
        "برنامه آفلاین‌محور است و داده‌ها روی دستگاه نگهداری می‌شوند؛ حذف یا خرابی دستگاه می‌تواند دسترسی به داده‌ها را از بین ببرد. نسخه پشتیبان را در محل امن نگه دار."
    )
)

@Composable
internal fun GuideScreen() {
    var sectionIndex by remember { mutableIntStateOf(0) }
    var topicIndex by remember { mutableIntStateOf(0) }
    val topics = if (sectionIndex == 0) quickGuideTopics else fullGuideTopics
    val selected = topics[topicIndex.coerceIn(topics.indices)]

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.30f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("راهنمای نگاه مربی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("از شروع سریع تا شناخت کامل ابزارهای مربی‌گری", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideSectionChip("شروع سریع", sectionIndex == 0) {
                        sectionIndex = 0
                        topicIndex = 0
                    }
                    GuideSectionChip("همه قابلیت‌ها", sectionIndex == 1) {
                        sectionIndex = 1
                        topicIndex = 0
                    }
                }
            }
        }

        Text(if (sectionIndex == 0) "مسیر پیشنهادی برای شروع" else "مرجع کامل قابلیت‌ها",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            topics.forEachIndexed { index, topic ->
                FilterChip(
                    selected = topicIndex == index,
                    onClick = { topicIndex = index },
                    label = { Text(topic.title, maxLines = 1) }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, Neon.Violet.copy(alpha = 0.32f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(selected.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(selected.summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                GuideContentBlock("این بخش چه کاربردی دارد؟", selected.purpose)
                GuideContentBlock("چطور از آن استفاده کنم؟", selected.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"))
                Surface(
                    color = Neon.Cyan.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.24f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("نکته مربی", color = Neon.Cyan, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Text(selected.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${topicIndex + 1} از ${topics.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (topicIndex == topics.lastIndex) "پایان این بخش" else "برای ادامه، تب بعدی را انتخاب کن",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GuideSectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) }
    )
}

@Composable
private fun GuideContentBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
