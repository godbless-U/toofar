package ir.madar.app;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Deterministic privacy-preserving normalization. NOT a generative language model. */
public final class PersianFormatter {
    private PersianFormatter() {}
    private static final Map<String, String> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("میخوام", "می‌خواهم"); WORDS.put("می‌خوام", "می‌خواهم");
        WORDS.put("میخام", "می‌خواهم"); WORDS.put("میخای", "می‌خواهی");
        WORDS.put("میخوای", "می‌خواهی"); WORDS.put("میخوایم", "می‌خواهیم");
        WORDS.put("میشه", "می‌شود"); WORDS.put("میشود", "می‌شود");
        WORDS.put("نمیشه", "نمی‌شود"); WORDS.put("نمی‌شه", "نمی‌شود");
        WORDS.put("می‌تونم", "می‌توانم"); WORDS.put("میتونم", "می‌توانم");
        WORDS.put("میتونیم", "می‌توانیم"); WORDS.put("میتونی", "می‌توانی");
        WORDS.put("بایدش", "باید"); WORDS.put("واسه", "برای");
        WORDS.put("برا", "برای"); WORDS.put("آخه", "زیرا");
        WORDS.put("چونکه", "زیرا"); WORDS.put("الان", "اکنون");
        WORDS.put("امروزم", "امروز هم"); WORDS.put("فردا", "فردا");
        WORDS.put("خونه", "خانه"); WORDS.put("خونمون", "خانه‌مان");
        WORDS.put("سرکار", "محل کار"); WORDS.put("رو", "را");
        WORDS.put("اینو", "این را"); WORDS.put("اونو", "آن را");
        WORDS.put("اینارو", "این‌ها را"); WORDS.put("اونا", "آن‌ها");
        WORDS.put("همون", "همان"); WORDS.put("اینجوری", "به این صورت");
        WORDS.put("اونجوری", "به آن صورت"); WORDS.put("یه", "یک");
        WORDS.put("یذره", "کمی"); WORDS.put("یکم", "کمی");
        WORDS.put("بزنم", "انجام دهم"); WORDS.put("برم", "بروم");
        WORDS.put("بیام", "بیایم"); WORDS.put("بگیرم", "دریافت کنم");
        WORDS.put("بخوام", "بخواهم"); WORDS.put("تموم", "تمام");
        WORDS.put("اوکی", "تأیید"); WORDS.put("اوکى", "تأیید");
        WORDS.put("چک", "بررسی"); WORDS.put("چیکار", "چه کاری");
        WORDS.put("کارام", "کارهایم"); WORDS.put("یادمه", "به یاد دارم");
        WORDS.put("بگم", "بگویم"); WORDS.put("بدم", "بدهم");
        WORDS.put("بفرستم", "ارسال کنم"); WORDS.put("کنم", "کنم");
    }

    public static String clean(String input) {
        if (input == null) return "";
        String output = input.trim().replace('ي','ی').replace('ك','ک');
        output = output.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        output = output.replaceAll("\\s+", " ");
        for (Map.Entry<String,String> rule : WORDS.entrySet()) {
            String w = rule.getKey();
            output = output.replaceAll("(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(w)+"(?![\\p{L}\\p{N}])",
                    java.util.regex.Matcher.quoteReplacement(rule.getValue()));
        }
        output = output.replaceAll("\\s+([،؛؟.!])", "$1");
        if (!output.isEmpty() && !output.matches(".*[.؟!?]$")) output += ".";
        return output;
    }

    public static String[] splitTasks(String text) {
        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) return new String[0];
        // Split only on explicit separators; never arbitrarily split a regular sentence.
        String[] chunks = input.split("(?:\\r?\\n|[؛;]+|[.؟!]\\s+|،\\s*(?:بعد|همچنین|سپس)\\s+|\\s+(?:کار بعدی|مورد بعدی|بعدش|و بعد(?! از))\\s+)");
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        for (String chunk : chunks) {
            String normalized = chunk.trim().replaceAll("[.؟!]+$", "").trim();
            if (!normalized.isEmpty()) list.add(normalized);
        }
        return list.toArray(new String[0]);
    }
}
