package com.dropiq.engine.user.service;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public final class PriceUtil {

    public static BigDecimal roundToMarketingPriceUp(BigDecimal price) {
        BigDecimal[] popularSteps = {
                new BigDecimal("49"), new BigDecimal("99"),
                new BigDecimal("149"), new BigDecimal("199"), new BigDecimal("249"),
                new BigDecimal("299"), new BigDecimal("399"), new BigDecimal("499"),
                new BigDecimal("599"), new BigDecimal("699"), new BigDecimal("799"),
                new BigDecimal("899"), new BigDecimal("999"), new BigDecimal("1499"),
                new BigDecimal("1999"), new BigDecimal("2499"), new BigDecimal("2999"),
                new BigDecimal("3999"), new BigDecimal("4999"), new BigDecimal("5999"),
                new BigDecimal("7999"), new BigDecimal("9999")
        };

        // Знаходимо найближчу красиву ціну, яка >= розрахованої ціни
        for (BigDecimal marketingPrice : popularSteps) {
            if (marketingPrice.compareTo(price) >= 0) {
                return marketingPrice;
            }
        }

        // Якщо ціна більша за всі стандартні кроки, використовуємо fallback логіку
        return roundToNearestMarketingFallback(price);
    }

    /**
     * Fallback округлення для високих цін
     */
    private BigDecimal roundToNearestMarketingFallback(BigDecimal price) {
        // Для цін > 10000, округляємо до найближчого "красивого" числа
        if (price.compareTo(new BigDecimal("10000")) > 0) {
            // Округляємо до тисяч з красивими закінченнями
            BigDecimal thousands = price.divide(new BigDecimal("1000"), 0, RoundingMode.UP);

            // Вибираємо красиве закінчення (999, 499, 299, 199)
            BigDecimal[] beautifulEndings = {
                    new BigDecimal("999"), new BigDecimal("499"),
                    new BigDecimal("299"), new BigDecimal("199")
            };

            for (BigDecimal ending : beautifulEndings) {
                BigDecimal candidate = thousands.multiply(new BigDecimal("1000")).subtract(new BigDecimal("1"))
                        .add(ending.remainder(new BigDecimal("1000")));
                if (candidate.compareTo(price) >= 0) {
                    return candidate;
                }
            }

            // Якщо нічого не підходить, просто додаємо до тисяч 999
            return thousands.multiply(new BigDecimal("1000")).subtract(new BigDecimal("1"));
        }

        // Для цін від 1000 до 10000
        BigDecimal hundreds = price.divide(new BigDecimal("100"), 0, RoundingMode.UP);
        return hundreds.multiply(new BigDecimal("100")).subtract(new BigDecimal("1")); // наприклад 1299, 1399, 1499
    }
}
