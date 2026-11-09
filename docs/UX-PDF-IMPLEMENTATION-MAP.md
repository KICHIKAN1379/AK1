# AK1 UX PDF — Implementation Map

Reference files:
- `ux.pdf`: 36 visual reference screens (primary visual source).
- `ux 2.pdf`: five-section information architecture, 26 primary/sub-pages, and mapping matrix.

Target branch: `redesign/home-command-center`
Main branch must remain untouched until explicit approval.

## Navigation order (right-to-left)

1. خانه
2. اعضا
3. پیشرفت
4. فروشگاه
5. تنظیمات

Keep this five-item bottom navigation visible on primary and secondary screens as shown in the reference. Child screens must return to their actual parent section, not to an unrelated default page.

## Screen placement matrix

| UX image | Screen | Parent / placement | Required content |
|---:|---|---|---|
| 35 | خانه | خانه | هویت حلقه، تاریخ شمسی، جمله روز، نبض هفتگی، دستیار مربی، میانبرهای قابل تنظیم |
| 36 | جزئیات نبض هفتگی | خانه ← نبض هفتگی | مأموریت فعال امروز، رد فعالیت‌ها، سوابق ماهانه، تعداد اعضا، تفکیک امتیازها |
| 2 | فهرست اعضا | اعضا | آمار حلقه، جست‌وجو، فیلتر نام/امتیاز/رتبه و سطح، فهرست چندعضوی، افزودن عضو |
| 1 | پرونده عضو | اعضا ← انتخاب عضو | آواتار و قاب، رتبه و سطح، XP، نقشه کمال، کارنامه و سوابق |
| 3 | سوابق کامل عضو | اعضا ← پرونده عضو ← سوابق | گروه‌بندی سال/ماه شمسی، فیلتر نوع رویداد، بازه و تاریخ |
| 4 | خروجی پرونده عضو | اعضا ← پرونده عضو ← خروجی | انتخاب بخش‌های خروجی و تولید PDF آماده چاپ |
| 32 | شناسنامه و ثبت امتیاز | پیشرفت ← شناسنامه | XP، امتیاز قابل‌خرج، الماس، سطح، دلیل تغییر و سابقه |
| 30 | مأموریت‌ها | پیشرفت ← مأموریت‌ها | مأموریت‌های فعال، انجام‌شده و ثبت انجام |
| 28 | مأموریت ویژه (مخفی) | پیشرفت ← مأموریت‌ها ← ویژه | مأموریت‌های پنهان و اقدام ثبت انجام |
| 31 | پازل گروهی | پیشرفت ← پازل | نقشه پازل، خانه‌های باز/بسته و ثبت تکه |
| 27 | کتاب‌خوانی | پیشرفت ← کتاب‌خوانی | رتبه مطالعه، ثبت کتاب، تعداد و روند مطالعه |
| 22 | گردونه | پیشرفت ← گردونه | چرخ، هزینه چرخش، اقدام چرخاندن و آخرین برندگان |
| 12 | تنظیم گردونه | تنظیمات ← پالایشگاه ← تنظیم گردونه | بخش‌ها، وزن هر بخش، هزینه و پیش‌نمایش |
| 33 | رتبه‌بندی اعضا | پیشرفت ← رقابت ← رتبه اعضا | سکوی سه نفر برتر و فهرست رتبه‌ها |
| 34 | رتبه‌بندی گروه‌ها | پیشرفت ← رقابت ← رتبه گروه‌ها | رتبه، امتیاز گروه و جایگاه حلقه |
| 25 | فهرست جلسات | پیشرفت ← جلسات | جلسات پیش‌رو/برگزارشده، جست‌وجو و افزودن جلسه |
| 26 | حضور و غیاب | پیشرفت ← جلسات ← حضور و غیاب | تقویم هفتگی/ماهانه، انتخاب روز، وضعیت هر عضو و آمار |
| 20 | صفحه اصلی فروشگاه | فروشگاه | موجودی امتیاز قابل‌خرج و الماس، دسته‌ها و پیشنهاد |
| 21 | جزئیات آیتم | فروشگاه ← انتخاب آیتم | تصویر، قیمت، موجودی پس از خرید و اقدام خرید/فعال‌سازی |
| 19 | جوایز | فروشگاه ← جوایز | شبکه جوایز قابل‌خرید با امتیاز |
| 23 | قاب‌ها | فروشگاه ← قاب‌ها | فیلتر وضعیت و شبکه قاب‌ها |
| 24 | آواتارها | فروشگاه ← آواتارها | فیلتر وضعیت و شبکه آواتارها |
| 18 | ویرایش پروفایل حلقه | تنظیمات ← ویرایش پروفایل | تصویر حلقه، نام حلقه و بنر خانه |
| 10 | صدا، پس‌زمینه و تم | تنظیمات ← ظاهر | موسیقی، صدا، پس‌زمینه، تم و پیش‌نمایش |
| 11 | جمله‌های روز | تنظیمات ← جمله‌های روز | فهرست، افزودن، ویرایش و حذف جمله |
| 7 | روزهای کلاس | تنظیمات ← روزهای کلاس | انتخاب روزهای برگزاری |
| 9 | میانبرهای خانه | تنظیمات ← میانبرهای خانه | فعال/غیرفعال کردن میانبرها با سقف نمایش مشخص |
| 8 | ورودی و خروجی اطلاعات | تنظیمات ← ورودی/خروجی | خروجی ZIP تصویری/کامل و ورود ZIP |
| 6 | بازگشت تنظیمات کارخانه | تنظیمات ← بازگشت کارخانه | هشدار روشن، پیامدها و تأیید صریح |
| 5 | راهنما | تنظیمات ← راهنما | راهنمای گام‌به‌گام و جست‌وجو |
| 16 | خانه تنظیمات | تنظیمات | پروفایل، پالایشگاه، جمله‌ها، ظاهر، میانبرها، روزهای کلاس، پشتیبان‌گیری، راهنما |
| 17 | خانه پالایشگاه | تنظیمات ← پالایشگاه | مدیریت مأموریت، جوایز، آواتار/قاب، گردونه و تنظیم نقشه کمال |
| 13 | تنظیم/ویرایش مأموریت | تنظیمات ← پالایشگاه ← مأموریت‌ها | عنوان، توضیح، پاداش، فعال/مخفی، ویرایش/حذف |
| 14 | آواتار و قاب | تنظیمات ← پالایشگاه ← آواتار/قاب | فهرست، افزودن تصویر و مدیریت |
| 15 | جوایز فروشگاه | تنظیمات ← پالایشگاه ← جوایز | عنوان، توضیح، قیمت و تصویر |
| 8 | تنظیم نقشه کمال | تنظیمات ← پالایشگاه ← تنظیم نقشه کمال | توانمندی‌ها و خودسازی، آیتم‌های قابل تعریف، فعال/مخفی، ویرایش/حذف |

## Attendance behavior — confirmed correction

- Statuses: حاضر، تأخیر، غایب، غیبت موجه، بدون ثبت.
- A reason is mandatory for غایب، غیبت موجه، and بدون ثبت.
- حاضر and تأخیر save immediately.
- The three reason-required statuses save immediately after a non-empty reason is entered.
- No separate «ثبت تغییرات» button.
- Changing date or filter must not leave unsaved pending changes.
- Attendance summaries and exports must distinguish غایب, غیبت موجه, تأخیر, and بدون ثبت; preserve the reason in the record/export.

## Visual and interaction acceptance criteria

- Follow the exact screen hierarchy and component placement of `ux.pdf`; do not substitute a generic admin dashboard.
- Preserve the agreed hybrid/bold direction and the PDF's dark navy surfaces, neon green/purple/pink accents, rounded glass-like cards, and persistent five-item navigation.
- Maintain readable Persian RTL layout, clear selected/disabled states, and responsive spacing on small Android screens.
- Every visible action must work; do not leave decorative controls.
- Verify each screen against its corresponding PDF image using device/emulator screenshots before declaring visual parity.
- Do not merge into `main` until the user reviews and approves the complete redesign.
