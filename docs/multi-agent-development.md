# نظام التطوير والاختبار متعدد الوكلاء

هذا النظام يشغّل كل طلب تطوير داخل Git worktree مستقل، ثم يطبّق بوابتين مستقلتين يملكهما
المنسّق لا الوكيل: `mvn clean test` ومراجعة آلية للـdiff. لا يدمج النظام إلى `main` ولا ينفّذ
`push` أو إصدارًا؛ الناتج فرع وworktree قابلان للمراجعة.

**الوكيل قطعة قابلة للتبديل.** `-Agent codex` (الافتراضي) أو `-Agent claude`، وكلاهما يمرّ بنفس
المراحل ونفس البوابتين ونفس عقد المراجعة. القواعد التي يلتزم بها الوكيل ليست داخل هذه السكربتات:
هي في **[`docs/agent-worktree-rules.md`](agent-worktree-rules.md)**، ويشير إليها `AGENTS.md`
و`CLAUDE.md`، فتسري أيضًا على وكيل تفتحه بيدك بلا أي سكربت.

يعتمد التصميم على نمطين مختلفين:

- **المهمة الرئيسية** تملك worktree وفرعًا مستقلين، ولذلك لا تتداخل ملفاتها أو مجلدات
  `target` مع عمل آخر.
- **مرحلة الاستكشاف** تعمل في جلسة `read-only` وتوزّع التحليل بالتوازي على الوكلاء
  المتخصصين. تنتهي بالكامل قبل بدء جلسة التنفيذ `workspace-write`.
- **التنفيذ والاختبار المستهدف** متسلسلان: ينتهي `implementer` أولًا، ثم يبدأ
  `test_engineer`. بعدهما يشغّل المنسق Maven ومراجعة مستقلة في جلسة `read-only`.

## المكوّنات

- **[`docs/agent-worktree-rules.md`](agent-worktree-rules.md)**: عقد محايد لأي وكيل يعمل على فرع
  خاص به — ما لا يُدمج ولا يُدفع، وأي اختبارات لا تُشغَّل، وأي ملفات لا تُنشأ. يعلو على أي prompt.
- `.codex/config.toml` و`.codex/agents/`: ستة أدوار (المعماري، المنفّذ، مهندس الاختبار، مراجع
  الكود، مراجع قاعدة البيانات، ومراجع الترجمة) — **وهذه ميزة Codex وحدها**. مع `-Agent claude`
  يُوصف التقسيم نفسه نصًّا في الـprompt بدل الاعتماد على آلية تفويض خاصة بأداة.
- `.worktreeinclude`: يبقى فارغًا افتراضيًا. يقبل ملفات محلية دقيقة ومتجاهلة وغير حساسة
  فقط، ويرفض ملفات الإعداد والمفاتيح المعروفة صراحةً.
- `scripts/agents/Invoke-MultiAgent.ps1`: ينشئ الفرع والـworktree، يشغّل دورة التطوير،
  يبني المشروع، يراجع التغييرات، ويمنح المنفّذ محاولة إصلاح إضافية افتراضيًا.
- `scripts/agents/AgentCommand.ps1`: يحدّد أين هي أداة الوكيل. `codex` من PATH وإلا من نسخة
  تطبيق Codex المكتبي تحت `%LOCALAPPDATA%\OpenAI\Codexin\<بصمة البناء>\codex.exe` (ليست على
  PATH وتتغيّر بصمتها مع كل تحديث، فيُبحث عن الأحدث)، و`claude` من PATH وإلا من shim الخاص بـnpm.
  تجاوزهما بـ`-AgentPath`.
- `scripts/agents/Get-MultiAgentRun.ps1`: يعرض نتائج التشغيل السابقة.
- `scripts/agents/Remove-MultiAgentWorktree.ps1`: يزيل worktree نظيفًا فقط ويبقي الفرع.
- `.agent-runs/`: سجل محلي متجاهل يحفظ prompt والـlogs والنتيجة والمراجعة.

## التشغيل

نفّذ فحص الإعداد مرة واحدة. هذا يفحص صياغة PowerShell وJSON، ويعثر على `codex` ويشغّله على
سياق المستودع دون استدعاء نموذج. **لا يثبت أن الوكلاء الستة سُجّلوا فعلًا** — السياق المعروض لا
يذكرهم، ولا يظهر ذلك إلا في تشغيل حقيقي. بناء Maven الكامل يتم داخل دورة التشغيل نفسها:

```powershell
powershell -ExecutionPolicy Bypass -File ./scripts/agents/Test-MultiAgentSetup.ps1
```

`pwsh` (PowerShell 7) يعمل أيضًا. السكربتات مكتوبة لتعمل على Windows PowerShell 5.1 الموجود مع
النظام: `Invoke-Native` يخفض `$ErrorActionPreference` أثناء استدعاء برنامج خارجي، لأن 5.1 يحوّل
كل سطر يكتبه Maven أو Codex على stderr إلى خطأ منهٍ حتى لو كان رمز الخروج صفرًا.

اعرض ما سيفعله التشغيل دون إنشاء شيء:

```powershell
pwsh ./scripts/agents/Invoke-MultiAgent.ps1 `
  -Task "إضافة تحقق جديد عند حفظ فاتورة البيع" `
  -DryRun
```

ابدأ دورة كاملة:

```powershell
pwsh ./scripts/agents/Invoke-MultiAgent.ps1 `
  -Task "إضافة تحقق جديد عند حفظ فاتورة البيع"
```

يجب أولًا أن تكون ملفات هذا النظام موجودة في commit الذي يشير إليه `-BaseBranch` (الافتراضي
`main`). يرفض التشغيل الفعلي إنشاء مهمة من revision أقدم لا يحتوي تعريفات الوكلاء.

يُنشأ فرع باسم `agents/...` وworktree تحت `.agent-worktrees/<اسم المستودع>/<runId>` بجوار
المستودع لا داخله، فلا يراه Maven ولا أدوات البحث في نسخة العمل الأصلية. بعد انتهاء Codex يشغّل
المنسّق `mvn -o clean test` بنفسه حتى لو قال وكيل التطوير إنه اختبر التغيير. الوضع offline
هو الافتراضي ليكون التشغيل قابلًا للتكرار ولا يصل إلى الشبكة دون قصد. على clone جديد أو عند
نقص Maven cache اسمح بحل الاعتماديات من الشبكة صراحةً:

```powershell
pwsh ./scripts/agents/Invoke-MultiAgent.ps1 `
  -Task "إضافة تحقق جديد عند حفظ فاتورة البيع" `
  -AllowOnlineMaven
```

إذا فشل البناء
أو أعادت المراجعة finding من P0 إلى P2، تُرسل الملاحظات إلى دورة إصلاح واحدة ثم يعاد
الاختبار والمراجعة. غيّر العدد بـ`-MaxFixPasses 0..3`.

يمرّر المنسّق `--ignore-user-config` ليبقى التشغيل مستقلًا عن إعدادك المحلي، وهذه الراية تُسقط
**اختيار النموذج** أيضًا — أول تشغيل حقيقي سقط بصمت إلى نموذج افتراضي بـ`reasoning effort: none`.
لذلك يقرأ المنسّق `model` و`model_reasoning_effort` من `~/.codex/config.toml` (المفاتيح العليا
فقط، فلا يلتقط قيمة من `[profiles.…]`) ويعيد تمريرهما صراحةً بـ`--config`، ويسجّلهما في
`result.json`. تجاوزهما بـ`-Model` و`-ReasoningEffort`:

```powershell
powershell -ExecutionPolicy Bypass -File ./scripts/agents/Invoke-MultiAgent.ps1 `
  -Task "إضافة تحقق جديد عند حفظ فاتورة البيع" `
  -Model gpt-5.6-sol -ReasoningEffort high
```

جهد التفكير المعلن داخل `.codex/agents/*.toml` يخصّ الوكيل نفسه ويبقى فوق هذا الافتراضي.

## التشغيل بوكيل آخر

```powershell
powershell -ExecutionPolicy Bypass -File ./scripts/agents/Invoke-MultiAgent.ps1 `
  -Task "إضافة تحقق جديد عند حفظ فاتورة البيع" -Agent claude
```

ما يختلف عند `-Agent claude` ثلاثة أشياء فقط، وكل ما عداها مشترك:

- **لا sandbox flag**: مرحلة القراءة تعمل بـ`--permission-mode plan` وهو لا يستطيع تعديل ملف.
  مرحلة الكتابة تعمل بما تختاره في `-ClaudePermissionMode` (الافتراضي `acceptEdits`). لو رفض
  الوكيل تنفيذ أمر لازم للمهمة فسيقول ذلك في تقريره، وعندها `bypassPermissions` قرار ثقة تتخذه
  أنت لا افتراضًا يُفرض عليك.
- **لا تفويض إلى أدوار مسمّاة**: التقسيم نفسه يُوصف في الـprompt.
- **لا فرض لمخطط JSON من الـCLI**: يُوضع المخطط داخل الـprompt ويُستخرج الـJSON من الإجابة.
  عقد الحكم واحد للاثنين: أي ملاحظة P0-P2 = رسوب، حتى لو قال الوكيل `pass`.

`-Model` و`-ReasoningEffort` تخصّان الأداة المختارة؛ لا يُورَّث معرّف نموذج من أداة إلى أخرى.

اعرض النتائج:

```powershell
pwsh ./scripts/agents/Get-MultiAgentRun.ps1
pwsh ./scripts/agents/Get-MultiAgentRun.ps1 -RunId 20260906-120000-a1b2c3-task -AsJson
```

## اختبارات قاعدة البيانات

المسار الافتراضي لا يشغّل الاختبارات المشروطة بـ`account.db.acceptance`، لأن عدة fixtures
تستخدم حالة process-wide أو معرّفات ثابتة وبعضها ينفّذ commit ثم cleanup. لا يجوز تشغيلها
من عدة worktrees على schema واحدة.

لتشغيلها يجب تجهيز MySQL schema مؤقت مستقل لهذا التشغيل، وضبط `account/config.xml` عليه،
ثم التصريح بذلك صراحةً:

```powershell
pwsh ./scripts/agents/Invoke-MultiAgent.ps1 `
  -Task "تعديل حساب رصيد المخزن" `
  -DatabaseAcceptance `
  -ConfirmDisposableDatabase `
  -DatabaseConfigPath C:\scratch-account-config\config.xml `
  -DatabaseConfigKeyPath C:\scratch-account-config\config.key
```

تشغّل البوابة عندها الاختبارات تسلسليًا بالأمر:

```text
mvn -o -pl account -am clean test -Daccount.db.acceptance=true
```

العلامة والمسار إلزاميان. لا يرى وكلاء التطوير ملف قاعدة البيانات؛ ينسخه المنسّق إلى
`account/config.xml` فقط أثناء عملية Maven ويحذفه في `finally`. كما يأخذ قفلًا حصريًا من
بصمة ملف الإعداد، فلا تعمل عمليتان بالملف نفسه بالتوازي. ملف المفتاح اختياري إذا كان
`ACCOUNT_CONFIG_KEY` موجودًا في البيئة. المنظومة لا تنشئ قاعدة مؤقتة ولا تستطيع إثبات أن
ملفين مختلفين لا يشيران إلى schema واحدة، لذلك يجب إنشاء schema فريدة لكل تشغيل وألا يشير
الملف إلى قاعدة تطوير أو إنتاج.

## مراجعة الناتج والتكامل

افتح `result.json` داخل مسار السجل الظاهر في نهاية التشغيل. يُكتب السجل قبل إنشاء worktree
ويحمل الحالة `starting` ثم `passed` أو `failed`، لذلك تظل الأعطال المبكرة قابلة للتتبع والتنظيف.
النجاح يعني أن تشغيل Codex
اكتمل، وبوابة Maven نجحت، والمراجعة المستقلة لم تجد P0-P2. لا يثبت ذلك سلوك شاشة JavaFX
فعليًا ولا سلامة قاعدة البيانات إن لم تُشغّل بوابتها الخاصة.

راجع التغييرات من مسار الـworktree، ثم أنشئ commit على فرعه. بعد دمج الفرع، نظّف الـworktree:

```powershell
pwsh ./scripts/agents/Remove-MultiAgentWorktree.ps1 `
  -RunId 20260906-120000-a1b2c3-task
```

أداة التنظيف تتحقق من هوية الـworktree في Git، وترفض التغييرات المتتبعة وغير المتتبعة
والملفات المتجاهلة المحلية (مع استثناء مجلدات Maven `target` القابلة لإعادة البناء)، ولا
تحذف الفرع. هذا يبقي سجل Git
قابلًا للاسترجاع حتى بعد إزالة النسخة الإضافية من الملفات.

## استخدامه من تطبيق Codex مباشرةً

يمكن بدء مهمة جديدة باختيار **Worktree** تحت صندوق الكتابة ثم إرسال طلب مثل:

```text
نفّذ هذا الطلب باستخدام project_architect وtest_engineer للاستكشاف المتوازي، ثم implementer
للكتابة، ثم code_reviewer للمراجعة. انتظر جميع الوكلاء، شغّل mvn -o clean test، ولا تدمج أو
تدفع الفرع: <وصف الطلب>
```

Codex-managed worktrees تنقل الملفات الدقيقة المذكورة في `.worktreeinclude`، وهو فارغ
افتراضيًا. أما المنسق المحلي فيمنع `.env` و`license.dat` و`private_key.pem` و`config.xml`
و`config.key` صراحةً؛ وكل ملف آخر تضيفه يجب أن يظل متجاهلًا من Git وألا يحتوي بيانات حساسة.

تحتفظ `.agent-runs/` بالـprompts وسجلات Codex وMaven محليًا ولا ترفعها إلى Git. قد تتضمن
رسائل الخطأ بيانات من المهمة، لذلك راجعها واحذف التشغيلات القديمة وفق سياسة الاحتفاظ لديك.
