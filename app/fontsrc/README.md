# فونت‌های وزیرمتن (Base64)

فایل‌های متنی Base64 فونت وزیرمتن (Vazirmatn) را با نام‌های زیر در این پوشه قرار دهید تا تسک گریدل `decodeVazirmatnFonts` پیش از `preBuild` آن‌ها را اعتبارسنجی و به `app/src/main/res/font/*.ttf` دیکد کند:

- `vazirmatn_regular.b64` (یا `vazirmatn_regular.ttf.b64`)
- `vazirmatn_medium.b64` (یا `vazirmatn_medium.ttf.b64`)
- `vazirmatn_bold.b64` (یا `vazirmatn_bold.ttf.b64`)

قواعد اعتبارسنجی و امنیت:
- هر سه وزن باید فایل واقعی SFNT با حداقل حجم ۲۰ کیلوبایت، امضای معتبر (`0x00010000`، `true`، `ttcf` یا `OTTO`) و جدول‌های `head`، `cmap` و `glyf`/`CFF` باشند.
- فایل‌های `.ttf` دیکدشده و `.ttf`/`.otf`های داخل `app/fontsrc/` در `.gitignore` قرار دارند و هرگز نباید در مخزن کامیت شوند.
