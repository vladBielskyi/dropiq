package com.dropiq.engine.integration.ai.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FeatureProductAnalysisResult {

    // ===== VISION АНАЛІЗ ЗОБРАЖЕННЯ =====
    private String productType;              // "жіноча сукня", "чоловіча сорочка"
    private String gender;                   // "жіночий", "чоловічий", "унісекс"
    private String clothingCategory;         // "верхній одяг", "штани", "сукні"
    private String primaryColor;            // основний колір
    private String secondaryColor;          // додатковий колір
    private String pattern;                 // "однотонний", "в горошок", "клітка"
    private String style;                   // "casual", "formal", "sport"
    private String season;                  // "весна", "літо", "осінь", "зима", "демісезон"
    private String materialType;            // "бавовна", "поліестер", "шовк"
    private Double visualQuality;           // якість фото 1-10
    private String fitType;                 // "облягаючий", "вільний", "regular"
    private String length;                  // "міні", "міді", "максі" (для сукень/спідниць)

    // ===== SEO КОНТЕНТ ДЛЯ HOROSHOP =====
    private String seoTitle;                // SEO заголовок (50-60 символів)
    private String productTitle;            // Комерційна назва товару
    private String description;             // Основний опис товару (200-400 слів)
    private String shortDescription;        // Короткий опис (50-100 слів)
    private String metaDescription;         // Meta description (140-160 символів)
    private String h1Title;                 // H1 заголовок для сторінки

    // ===== КАТЕГОРІЇ ДЛЯ HOROSHOP =====
    private String mainCategory;            // "Жіночий одяг"
    private String subCategory;             // "Сукні"
    private String microCategory;           // "Літні сукні"
    private String categoryPath;            // "Жіночий одяг / Сукні / Літні сукні"

    // ===== КЛЮЧОВІ СЛОВА ТА ТЕГИ =====
    private List<String> primaryKeywords;   // Основні ключові слова
    private List<String> longTailKeywords;  // Довгий хвіст
    private List<String> tags;              // Теги для фільтрації
    private List<String> styleKeywords;     // Стильові ключові слова

    // ===== КОМЕРЦІЙНИЙ КОНТЕНТ =====
    private String targetAudience;          // Цільова аудиторія
    private List<String> sellingPoints;     // Топ-3 переваги товару
    private String occasionDescription;     // Для яких випадків
    private String stylingTips;             // Поради зі стилізації
    private String careInstructions;        // Догляд за товаром
    private String sizeGuide;               // Поради щодо розміру

    // ===== АНАЛІТИКА ТА ОПТИМІЗАЦІЯ =====
    private Double trendScore;              // Трендовість 1-10
    private Double conversionScore;         // Потенціал конверсії 1-10
    private String priceCategory;           // "бюджетний", "середній", "преміум"
    private Boolean seasonalRelevance;      // Сезонна актуальність
    private String competitiveAdvantage;    // Конкурентна перевага

    // ===== СТРУКТУРОВАНІ ДАНІ =====
    private String brandName;               // Назва бренду (якщо видно)
    private String modelName;               // Назва моделі
    private List<String> availableSizes;    // Доступні розміри
    private List<String> availableColors;   // Доступні кольори
    private String fabricComposition;       // Склад тканини

    // ===== MARKETING =====
    private String uniqueSellingPoint;     // УТП
    private String emotionalTrigger;        // Емоційний тригер
    private String urgencyMessage;          // Повідомлення про терміновість
    private List<String> crossSellItems;    // Рекомендації для перехресних продажів

    // ===== ТЕХНІЧНІ ДАНІ =====
    private Double analysisConfidence;      // Впевненість аналізу 0-1
    private Long analysisTimestamp;         // Час аналізу
    private String analysisVersion;         // Версія аналізу

    public FeatureProductAnalysisResult() {
        this.analysisTimestamp = System.currentTimeMillis();
        this.analysisVersion = "3.0-horoshop";
    }

    /**
     * Валідація результатів аналізу
     */
    public boolean isValid() {
        return seoTitle != null && !seoTitle.isEmpty() &&
                description != null && description.length() > 50 &&
                metaDescription != null && !metaDescription.isEmpty() &&
                trendScore != null && trendScore >= 1.0 && trendScore <= 10.0;
    }

    /**
     * Генерація повного шляху категорії для Horoshop
     */
    public String getFullCategoryPath() {
        if (mainCategory == null) return "Одяг";

        StringBuilder path = new StringBuilder(mainCategory);
        if (subCategory != null) {
            path.append(" / ").append(subCategory);
            if (microCategory != null) {
                path.append(" / ").append(microCategory);
            }
        }
        return path.toString();
    }

    /**
     * Генерація структурованих даних для товару
     */
    public String generateStructuredData(String productUrl, Double price) {
        return String.format("""
            {
              "@context": "https://schema.org/",
              "@type": "Product",
              "name": "%s",
              "description": "%s",
              "category": "%s",
              "brand": {
                "@type": "Brand",
                "name": "%s"
              },
              "offers": {
                "@type": "Offer",
                "price": "%.0f",
                "priceCurrency": "UAH",
                "availability": "https://schema.org/InStock",
                "url": "%s"
              }
            }
            """,
                productTitle != null ? productTitle : "Стильний одяг",
                shortDescription != null ? shortDescription : description,
                getFullCategoryPath(),
                brandName != null ? brandName : "Fashion Brand",
                price != null ? price : 0.0,
                productUrl != null ? productUrl : ""
        );
    }

    /**
     * Генерація ALT тексту для зображень
     */
    public String generateAltText() {
        StringBuilder alt = new StringBuilder();

        if (productType != null) {
            alt.append(productType);
        }

        if (primaryColor != null) {
            alt.append(" ").append(primaryColor);
        }

        if (style != null) {
            alt.append(" ").append(style);
        }

        if (brandName != null) {
            alt.append(" ").append(brandName);
        }

        return alt.toString().trim();
    }

    /**
     * Створення заголовку для соцмереж
     */
    public String getSocialMediaTitle() {
        if (emotionalTrigger != null) {
            return emotionalTrigger;
        }
        return seoTitle != null ? seoTitle : productTitle;
    }

    /**
     * Перевірка сезонної актуальності
     */
    public boolean isCurrentlyRelevant() {
        if (!seasonalRelevance) return true;

        String currentSeason = getCurrentSeason();
        return season != null && (season.equals(currentSeason) || season.equals("демісезон"));
    }

    private String getCurrentSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        if (month >= 3 && month <= 5) return "весна";
        if (month >= 6 && month <= 8) return "літо";
        if (month >= 9 && month <= 11) return "осінь";
        return "зима";
    }

    /**
     * Генерація мета-тегів для сторінки
     */
    public Map<String, String> generateMetaTags(String productUrl) {
        Map<String, String> metaTags = new java.util.HashMap<>();

        metaTags.put("title", seoTitle);
        metaTags.put("description", metaDescription);
        metaTags.put("og:title", getSocialMediaTitle());
        metaTags.put("og:description", shortDescription != null ? shortDescription : metaDescription);
        metaTags.put("og:type", "product");
        metaTags.put("og:url", productUrl);

        if (primaryKeywords != null && !primaryKeywords.isEmpty()) {
            metaTags.put("keywords", String.join(", ", primaryKeywords));
        }

        return metaTags;
    }
}
