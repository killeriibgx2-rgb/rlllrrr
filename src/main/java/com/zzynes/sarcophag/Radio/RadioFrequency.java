package com.zzynes.sarcophag.Radio;

public final class RadioFrequency {
    public static final int MIN = 100000;
    public static final int MAX = 999999;
    public static final int DEFAULT = 145500;

    private RadioFrequency() {
    }

    public static boolean isValid(int frequency) {
        return frequency >= MIN && frequency <= MAX;
    }

    public static int normalize(int frequency) {
        if (isValid(frequency)) {
            return frequency;
        }
        return DEFAULT;
    }

    public static String format(int frequency) {
        int normalized = normalize(frequency);
        int left = normalized / 1000;
        int right = normalized % 1000;
        return String.format("%03d.%03d", left, right);
    }

    public static String formatInput(String digits) {
        if (digits == null || digits.isEmpty()) {
            return "";
        }

        String clean = onlyDigits(digits);

        if (clean.length() <= 3) {
            return clean;
        }

        return clean.substring(0, 3) + "." + clean.substring(3);
    }

    public static String onlyDigits(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c >= '0' && c <= '9') {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    public static int parseOrFallback(String digits, int fallback) {
        String clean = onlyDigits(digits);

        if (clean.length() != 6) {
            return normalize(fallback);
        }

        try {
            int frequency = Integer.parseInt(clean);
            return isValid(frequency) ? frequency : normalize(fallback);
        } catch (NumberFormatException ignored) {
            return normalize(fallback);
        }
    }
}