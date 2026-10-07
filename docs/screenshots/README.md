# স্ক্রিন-রেন্ডার (ফোনের স্ক্রিনশট নয়)
আসল অ্যাপ-কোড থেকে Robolectric native graphics দিয়ে বিল্ড-মেশিনে রেন্ডার করা (S24 Ultra-র আকার ≈ 412×915 dp, হালকা থিম)। ফোনে ফন্ট, রঙ, সিস্টেম বার ভিন্ন দেখাতে পারে।
আবার বানাতে: `./gradlew testDebugUnitTest --tests '*ScreenshotTest*'` → `app/build/screenshots`।
1. `1_splash.png` — খোলার পর্দা · 2. `2_first_setup.png` — প্রথম PIN সেটআপ · 3. `3_home.png` — হোমস্ক্রিন (ব্যানার, ফাইল, মডেল, পরিভাষা… নিচে আরও আছে)
