# মডেল ম্যানিফেস্ট (সব মান বাস্তবে যাচাই করা)

যাচাইয়ের তারিখ: ২০২৬-১০-০৫। পদ্ধতি: HuggingFace API থেকে লাইসেন্স/সাইজ/LFS-sha256 পড়া, তারপর **আসল ফাইল নামিয়ে নিজে `sha256sum` হিসাব করে মেলানো**।

| মডেল | ফাইল | সাইজ (বাইট) | SHA-256 (নিজে হিসাব করা, HF-এর সঙ্গে মিলেছে) | লাইসেন্স |
|---|---|---|---|---|
| Gemma 4 E4B (প্রস্তাবিত) | `gemma-4-E4B-it.litertlm` | 3,659,530,240 | `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` | Apache-2.0 (HF ট্যাগ `license:apache-2.0`) |
| Gemma 4 E2B (হালকা বিকল্প) | `gemma-4-E2B-it.litertlm` | 2,588,147,712 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | Apache-2.0 |

- উৎস: `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm` ও `.../gemma-4-E2B-it-litert-lm` (ভিত্তি: `google/gemma-4-E4B-it`, `google/gemma-4-E2B-it`)। gated নয় — লগইন/টোকেন ছাড়া নামে।
- রানটাইম: LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.17.1`, Google Maven)। ফোনে চালানোর জন্য এই `.litertlm` ফাইলই লাগে।
- **নামানোর পরও checksum মিলিয়ে নিন**: অ্যাপ import-এর সময় SHA-256 হিসাব করে এই তালিকার সঙ্গে মেলায় (ModelStore/KnownModels)।
- মডেল কার্ড অনুযায়ী E4B ফাইলে মাল্টিমোডাল (ছবি/অডিও) অংশ আছে; এগুলো এখনো এই অ্যাপে **যাচাই/ব্যবহার করা হয়নি**।
- মডেল কার্ডের গতি/মেমোরি সংখ্যা Galaxy **S26 Ultra**-র; S24 Ultra-র নয়। আপনার ফোনের সংখ্যা: **ডিভাইস-টেস্ট বাকি**।
- ফোনে বসানোর উপায়: কম্পিউটারে ফাইল নামিয়ে `sha256sum` মিলান → ফোনের Downloads-এ কপি → অ্যাপের "মডেল import" বাটন।
- Gemma 3n (`google/gemma-3n-*-litert-lm`) gated (`manual`) — HF-এ লাইসেন্স গ্রহণ ও লগইন লাগে, তাই ডিফল্ট করা হয়নি।
