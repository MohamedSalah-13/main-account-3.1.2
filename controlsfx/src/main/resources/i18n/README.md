# نظام اللغات المتعددة - Multi-Language System

## نظرة عامة - Overview

هذا النظام يدعم تعدد اللغات في التطبيق باستخدام ملفات `.properties` مع دعم كامل للعربية والإنجليزية.

## ملفات اللغات - Language Files

- `messages.properties` - الملف الافتراضي (عربي)
- `messages_ar.properties` - اللغة العربية
- `messages_en.properties` - اللغة الإنجليزية

## كيفية الاستخدام - How to Use

### 1. في Java Code

```java
import com.hamza.controlsfx.language.LanguageManager;

public class MyController {

    private LanguageManager langManager = LanguageManager.getInstance();

    public void initialize() {
        // الحصول على نص مترجم
        String itemTitle = langManager.getString("item.title");

        // تغيير اللغة إلى الإنجليزية
        langManager.setLocale(LanguageManager.ENGLISH);

        // تغيير اللغة إلى العربية
        langManager.setLocale(LanguageManager.ARABIC);

        // التبديل بين اللغتين
        langManager.toggleLanguage();

        // التحقق من اللغة الحالية
        if (langManager.isArabic()) {
            System.out.println("اللغة الحالية: عربي");
        }
    }
}
```

### 2. تحديث واجهة المستخدم

```java
public void updateUILanguage() {
    LanguageManager lang = LanguageManager.getInstance();

    // تحديث النصوص
    btnSave.setText(lang.getString("btn.save"));
    btnClose.setText(lang.getString("btn.close"));
    labelCode.setText(lang.getString("item.code"));
    labelName.setText(lang.getString("item.name"));

    // تحديث التبويبات
    tabBasicData.setText(lang.getString("tab.basic.data"));
    tabUnits.setText(lang.getString("tab.units"));
    tabOther.setText(lang.getString("tab.other"));
}
```

### 3. في FXML (مستقبلاً)

يمكن ربط FXML مباشرة مع ResourceBundle:

```java
FXMLLoader loader = new FXMLLoader();
loader.setLocation(getClass().getResource("view.fxml"));
loader.setResources(LanguageManager.getInstance().getResourceBundle());
Parent root = loader.load();
```

## إضافة مفاتيح جديدة - Adding New Keys

### في messages_ar.properties:
```properties
new.feature.title=عنوان الميزة الجديدة
new.feature.description=وصف الميزة
```

### في messages_en.properties:
```properties
new.feature.title=New Feature Title
new.feature.description=Feature Description
```

## التنسيق مع المتغيرات - Formatting with Variables

### في ملف properties:
```properties
msg.welcome=Welcome %s!
msg.items.count=You have %d items
```

### في Java:
```java
String welcome = langManager.getString("msg.welcome", "Ahmed");
String count = langManager.getString("msg.items.count", 10);
```

## أفضل الممارسات - Best Practices

1. **استخدام مفاتيح واضحة ومنظمة**
   - استخدم نمط `category.subcategory.item`
   - مثال: `item.buy.price`, `btn.save`, `msg.success`

2. **الحفاظ على تزامن الملفات**
   - تأكد من وجود نفس المفاتيح في جميع ملفات اللغات

3. **معالجة المفاتيح المفقودة**
   - النظام يرجع المفتاح نفسه إذا لم يجد ترجمة

4. **الترميز UTF-8**
   - جميع الملفات تستخدم UTF-8 encoding

## هيكل المفاتيح الحالي - Current Keys Structure

```
├── عام (General): app.*, save, close, add, edit, delete...
├── أزرار (Buttons): btn.*
├── أصناف (Items): item.*
├── تبويبات (Tabs): tab.*
├── وحدات (Units): unit.*
├── رسائل (Messages): msg.*
├── قوائم (Menus): menu.*
├── حسابات (Accounts): account.*
├── فواتير (Invoices): invoice.*
├── تقارير (Reports): report.*
├── إعدادات (Settings): settings.*
└── مستخدمين (Users): user.*
```

## مثال كامل - Complete Example

```java
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.*;

public class ItemController {

    @FXML private Label labelCode;
    @FXML private Label labelName;
    @FXML private Button btnSave;
    @FXML private Button btnClose;

    private LanguageManager lang;

    @FXML
    public void initialize() {
        lang = LanguageManager.getInstance();
        updateLanguage();
    }

    /**
     * تحديث جميع النصوص حسب اللغة الحالية
     */
    private void updateLanguage() {
        // Labels
        labelCode.setText(lang.getString("item.code"));
        labelName.setText(lang.getString("item.name"));

        // Buttons
        btnSave.setText(lang.getString("btn.save"));
        btnClose.setText(lang.getString("btn.close"));
    }

    /**
     * تبديل اللغة
     */
    @FXML
    private void onToggleLanguage() {
        lang.toggleLanguage();
        updateLanguage();

        // تحديث اتجاه النص
        String direction = lang.getTextDirection();
        // تطبيق direction على الواجهة
    }

    /**
     * عرض رسالة نجاح
     */
    private void showSuccessMessage() {
        String message = lang.getString("msg.save.success");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.show();
    }
}
```

## الميزات - Features

✅ دعم كامل للعربية والإنجليزية
✅ حفظ اللغة المفضلة تلقائياً
✅ سهولة التبديل بين اللغات
✅ دعم اتجاه النص (RTL/LTR)
✅ معالجة المفاتيح المفقودة
✅ نمط Singleton للأداء الأفضل

## التطوير المستقبلي - Future Development

- [ ] إضافة لغات أخرى
- [ ] ربط تلقائي مع FXML
- [ ] واجهة إعدادات اللغة في التطبيق
- [ ] تحديث ديناميكي للواجهة عند تغيير اللغة
