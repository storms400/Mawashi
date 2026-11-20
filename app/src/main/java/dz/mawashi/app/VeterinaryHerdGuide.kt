package dz.mawashi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VG=Color(0xFF526B55)
private val VC=Color(0xFFF7F3EA)
private val VI=Color(0xFF27332A)
private val VM=Color(0xFF778078)
private val VL=Color(0xFFE8E4DA)

private data class VetItem(val name:String,val group:String,val type:String,val purpose:String,val use:String,val risks:String,val timing:String,val caution:String)
private fun v(n:String,g:String,t:String,p:String,u:String,r:String,s:String,c:String)=VetItem(n,g,t,p,u,r,s,c)

private val vetItems=listOf(
v("لقاح طاعون المجترات الصغيرة PPR","اللقاحات","لقاح","للوقاية من طاعون المجترات الصغيرة.","ضمن برنامج التحصين الرسمي وبحسب وضع المنطقة والمنتج المرخص.","تفاعلات موضعية أو عابرة؛ اللقاحات الحية لها احتياطات خاصة.","الحملة والجرعة والمعزز يحددها البرنامج الرسمي ونشرة المنتج؛ لا يفترض تكراره سنوياً تلقائياً.","تحقق من العمر المسموح والحمل وسلسلة التبريد."),
v("لقاح جدري الأغنام","اللقاحات","لقاح","للوقاية من جدري الأغنام حيث يكون التحصين موصى به.","في القطعان المعرضة للخطر ووفق الخطة البيطرية المحلية.","قد تظهر تفاعلات موضعية أو حرارة عابرة؛ بعض المنتجات حية.","حسب نوع اللقاح والحملة والنشرة، وليس هناك فاصل واحد لكل المنتجات.","لا تخلط بين منتجات مختلفة أو طرق إعطاء مختلفة."),
v("لقاح التسمم المعوي","اللقاحات","لقاح","للوقاية من التسمم المعوي المرتبط بسموم المطثيات.","بحسب تركيب اللقاح والأمراض المستهدفة في القطيع.","تورم موضعي أو تفاعل عابر؛ يلزم إكمال التحصين الأولي والمعزز.","قد يحتاج جرعتين أوليتين ثم معززاً؛ الفاصل حسب النشرة.","قد يتداخل مع لقاح الكلوستريديا؛ قارن المستضدات قبل إنشاء برنامجين."),
v("لقاح الكلوستريديا","اللقاحات","لقاح","للوقاية من أمراض المطثيات التي يغطيها المستحضر.","يختار المنتج بحسب الأمراض ومخاطر القطيع.","تفاعلات موضعية ممكنة؛ تجنب تكرار مستحضرات متداخلة.","الأولي والمعزز وجرعات ما قبل الولادة تختلف حسب المنتج.","سجل المكونات لتجنب تكرار لقاح التسمم المعوي نفسه."),
v("لقاح الحمى القلاعية","اللقاحات","لقاح","للوقاية من الحمى القلاعية ضمن حملات الصحة الحيوانية.","بحسب النمط المصلي والبرنامج الرسمي.","تفاعلات موضعية أو عامة؛ الحماية مرتبطة بتغطية السلالات المناسبة.","مواعيد الحملات وإعادة التحصين تحددها السلطات والنشرة.","لا تستبدل توجيهات المصالح البيطرية بجدول عام."),
v("لقاح البروسيلا Rev.1","اللقاحات","لقاح حي","للوقاية من البروسيلا ضمن برامج مكافحة رسمية محددة.","فقط ضمن البرنامج الوطني وبواسطة مختص مخول.","لقاح حي قد يسبب عدوى بشرية عرضية ومخاطر تناسلية؛ احتياطات صارمة.","العمر والطريقة والتكرار يحددها البرنامج الرسمي والنشرة.","ليس لقاحاً روتينياً عشوائياً؛ حماية العاملين إلزامية."),
v("لقاح الإكثيما المعدية Orf","اللقاحات","لقاح حي","قد يستخدم في القطعان التي يتوطن فيها المرض.","عند وجود تاريخ مرضي وبحسب تقييم بيطري.","قد يسبب آفات موضعية وينقل العدوى للعاملين.","ليس تحصيناً عاماً؛ توقيته وطريقته حسب النشرة والخطر.","قفازات ومنع تلوث الأدوات والمواقع."),
v("ألبيندازول Albendazole","مضادات الطفيليات الداخلية","مضاد ديدان","لبعض الديدان المعوية والرئوية والشريطية والمثقوبات حسب التركيبة.","بعد تحديد الطفيلي ووزن الحيوان.","آثار هضمية محتملة؛ احتياطات خاصة للحمل وفترات السحب.","عند الحاجة والفحص والمقاومة؛ ليس شهرياً أو سنوياً تلقائياً.","الطيف والجرعة والسحب من نشرة المنتج المحلية."),
v("فينبندازول Fenbendazole","مضادات الطفيليات الداخلية","مضاد ديدان","لبعض الديدان الأسطوانية والرئوية وفق المستحضر.","بعد تحديد الطفيلي وقياس الوزن.","المقاومة واردة؛ نقص الجرعة بسبب تقدير الوزن قد يفشل العلاج.","حسب الفحص وخطة مكافحة المقاومة، لا تكرار آلي.","التراكيز وفترات السحب تختلف بين المنتجات."),
v("إيفرمكتين Ivermectin","مضادات الطفيليات الداخلية","مضاد طفيليات","لبعض الديدان والطفيليات الخارجية حسب المادة والتركيبة والطريق.","فقط إذا كانت النشرة تسجل الأغنام والطفيلي المقصود.","الجرعة أو الطريق الخاطئ قد يسبب تسمماً؛ فترات السحب مهمة.","حسب الطفيلي والمنتج؛ لا موعد سنوي موحد.","لا تستخدم منتج الأبقار أو السكب الموضعي تلقائياً للأغنام."),
v("ليفاميزول Levamisole","مضادات الطفيليات الداخلية","مضاد ديدان","لبعض الديدان الأسطوانية المعوية.","بعد تقدير الوزن بدقة واختيار المنتج المناسب.","هامش الأمان أضيق؛ الجرعة الزائدة قد تسبب لعاباً وارتعاشاً وضيق تنفس.","علاج موجّه حسب الفحص والمقاومة.","يلزم الالتزام الصارم بالتركيز والوزن."),
v("تريكلابندازول Triclabendazole","مضادات الطفيليات الداخلية","مضاد ديدان","يستهدف ديدان الكبد وبعض أطوار الفاشيولا بحسب المنتج.","عند وجود خطر بيئي أو دليل على الإصابة.","المقاومة ممكنة وفترات السحب تختلف.","حسب دورة الفاشيولا والبيئة ونتائج الفحص.","لا يغطي كل الطفيليات ولا يستبدل مضاد الديدان العام."),
v("مستحضرات الرش والغمس","الطفيليات الخارجية","مكافحة خارجية","للسيطرة على القراد والقمل والجرب وطفيليات خارجية أخرى بحسب المادة.","بعد تحديد الطفيلي والمنتج المناسب للأغنام.","التسمم وسوء التخفيف والتعرض للعاملين وتلوث المياه.","وفق دورة الطفيلي وفاصل إعادة المعالجة بالنشرة.","لا تخلط المواد ولا تستخدم تركيزاً لنوع حيواني آخر."),
v("مستحضرات السكب الموضعي","الطفيليات الخارجية","مكافحة خارجية","لبعض الطفيليات الخارجية أو الداخلية بحسب المادة.","فقط إذا كانت النشرة تجيز الأغنام وطريق السكب والطفيلي المستهدف.","تهيج أو تسمم عند استخدام منتج أو جرعة غير مناسبة.","حسب المادة والطفيلي والنشرة.","السكب الموضعي ليس مادة فعالة واحدة."),
v("مستحضرات الجرب والقمل","الطفيليات الخارجية","مكافحة خارجية","لعلاج الجرب أو القمل بعد تأكيد السبب.","قد تتطلب معالجة المخالطين وتنظيف البيئة.","المقاومة وإعادة العدوى وسوء التشخيص محتملة.","حسب دورة حياة الطفيلي ونشرة المستحضر.","الحكة وتساقط الصوف لا يثبتان وحدهما الجرب."),
v("مطهرات الحظائر","التطهير والنظافة","مطهر بيئي","لتقليل الحمل الميكروبي على الأسطح والأدوات.","بعد إزالة الروث والمواد العضوية مع احترام التخفيف وزمن التماس.","حروق وأبخرة وسمية؛ لا تخلط الكلور بالأحماض أو الأمونيا.","ضمن خطة التنظيف أو التفشي وفق الملصق.","أبعد الحيوانات وأعدها بعد التهوية والجفاف حسب النشرة."),
v("مطهرات الجلد والجروح","التطهير والنظافة","مطهر موضعي","لتنظيف الجلد والجروح السطحية بمنتج مخصص للأنسجة.","بعد تقييم عمق الجرح؛ العميق يحتاج فحصاً.","بعض المواد مهيجة أو سامة للأنسجة بتركيز مرتفع.","عند الحاجة ضمن العناية بالجروح.","لا تستخدم مطهر الأسطح على الجروح أو العين."),
v("مستحضرات التهابات الضرع","علاج الضرع","علاج بيطري","لبعض حالات التهاب الضرع وفق التشخيص.","فحص الضرع والحليب والحالة العامة؛ تؤخذ عينة عند الإمكان.","مضاد غير مناسب يفشل العلاج ويزيد المقاومة؛ فترات السحب واجبة.","عند حدوث الحالة وبوصفة، وليس موعداً سنوياً.","الحمى أو الضرع الساخن المؤلم يستلزمان تقييماً سريعاً."),
v("محاليل الأملاح والسوائل","الجهاز الهضمي والجفاف","دعم سوائل","لتعويض الماء والأملاح في الجفاف وفقد السوائل.","بعد تقييم الجفاف والقدرة على البلع.","الحالات الشديدة قد تحتاج سوائل وريدية؛ خطر شفط السوائل عند الإعطاء الخاطئ.","حسب الحالة ومراقبة الاستجابة.","الخمول الشديد أو عدم الوقوف يستلزم طبيباً."),
v("تولترازوريل Toltrazuril","الجهاز الهضمي والجفاف","مضاد كوكسيديا","لعلاج/مكافحة الكوكسيديا في الحملان وفق التشخيص والمنتج.","عند وجود خطر أو تشخيص مناسب؛ ليس لكل إسهال.","الاستخدام غير المبرر يعزز المقاومة؛ فترات السحب تختلف.","يرتبط بالعمر والضغط الوبائي ونشرة المنتج.","النظافة وتقليل الاكتظاظ جزء أساسي من المكافحة."),
v("مستحضرات علاج النفاخ","الجهاز الهضمي والجفاف","علاج إسعافي","لبعض أنواع النفاخ وفق المادة وطبيعة الحالة.","النفاخ قد يكون طارئاً؛ يجب تمييز الرغوي من الغازي.","ضغط على التنفس وقد يؤدي للوفاة سريعاً؛ التأخير خطر.","عند ظهور الحالة فقط.","الانتفاخ الشديد أو صعوبة التنفس حالة إسعافية."),
v("محاليل علاج الإسهال","الجهاز الهضمي والجفاف","دعم علاجي","لتعويض السوائل والأملاح؛ وبعضها يحتوي مكونات إضافية.","تحديد السبب والعمر ودرجة الجفاف.","الإسهال قد يكون كوكسيديا أو عدوى أو تغذية؛ المحلول لا يعالج كل الأسباب.","حسب الجفاف وتعليمات المنتج ومراقبة الاستجابة.","الحمل غير القادر على الرضاعة يحتاج تدخلاً سريعاً."),
v("فيتامينات A وD وE","الفيتامينات والمكملات","مكمل","لتصحيح نقص أو خطر غذائي محدد.","بعد تقييم العليقة والحالة والتحليل عند الحاجة.","فرط A وD قد يكون ساماً؛ تختلف المستحضرات الفموية والحقنية.","لا تعطى سنوياً تلقائياً؛ حسب النقص والنشرة.","احسب إجمالي ما يتناوله الحيوان من جميع المصادر."),
v("مكملات الأملاح المعدنية","الفيتامينات والمكملات","مكمل معدني","لتغطية احتياجات المعادن عند قصور العليقة أو المرعى.","بحسب تحليل العليقة والماء والمرحلة الإنتاجية.","زيادة النحاس قد تكون سامة للأغنام؛ خلطات الأبقار قد لا تناسبها.","توفير مضبوط أو جرعات حسب المنتج والتحليل.","اختر خلطة مخصصة للأغنام."),
v("مكملات العناصر النادرة","الفيتامينات والمكملات","مكمل معدني","لتصحيح نقص السيلينيوم أو الكوبالت أو الزنك وغيرها عند ثبوته.","بحسب التحليل أو العلامات وتقييم بيطري.","السيلينيوم والنحاس لهما هامش أمان محدود والزيادة سامة.","يعتمد على العنصر ومصدر النقص والمنتج.","لا تجمع مكملات متعددة دون حساب إجمالي المدخول."),
v("أوكسيتوسين Oxytocin","التكاثر والولادة","هرمون بوصفة","قد يستخدم في حالات توليدية محددة أو لتحفيز نزول الحليب.","بعد استبعاد انسداد قناة الولادة وتشخيص السبب.","استخدامه مع انسداد أو وضع جنيني خاطئ قد يمزق الرحم ويهدد الحياة.","ليس محفز ولادة منزلياً؛ الطبيب يحدد الحاجة والتوقيت.","لا يعطى لمجرد تأخر الولادة دون فحص."),
v("البروستاغلاندين","التكاثر والولادة","هرمون بوصفة","لبعض بروتوكولات الشبق أو إنهاء الحمل بحسب المادة والاستطباب.","ضمن بروتوكول تناسلي بيطري وبعد التحقق من الحمل.","قد يسبب إجهاضاً؛ بعض الأنواع خطرة على الحوامل والعاملين.","حسب المادة والهدف والبروتوكول.","يجب تحديد المادة الفعالة والتركيز لا اسم المجموعة فقط."),
v("مستحضرات الكالسيوم","التكاثر والولادة","علاج/مكمل","لعلاج نقص الكالسيوم أو الوقاية المحددة وفق الحالة.","للنعاج حول الولادة أو الحالات المثبتة بتقييم بيطري.","الحقن الوريدي السريع قد يسبب اضطراب القلب والوفاة.","حسب التشخيص وطريق الإعطاء والمنتج.","النعجة الراقدة تحتاج تمييز نقص الكالسيوم من تسمم الحمل."),
v("البروبيلين غليكول","التكاثر والولادة","دعم طاقة","قد يدعم الطاقة في خطر الكيتوزية/تسمم الحمل ضمن خطة علاجية.","للنعاج عالية الاحتياج بأواخر الحمل بعد تقييمها.","لا يعوض العليقة المتوازنة؛ الإعطاء القسري قد يسبب شفطاً رئوياً.","بحسب تقييم الطاقة والمرحلة وتعليمات المنتج.","فقد الشهية بأواخر الحمل يستلزم فحصاً سريعاً."),
v("مطهر سرة الحملان","رعاية الحملان","مطهر موضعي","لتقليل تلوث السرة بعد الولادة.","بعد كل ولادة وفق المادة والتركيز المناسبين.","المحاليل الخاطئة أو الملوثة قد تؤذي الأنسجة؛ راقب التهاب السرة والمفاصل.","إجراء مرتبط بكل ولادة وليس موعداً سنوياً.","الفرشة الجافة والنظافة ومراقبة الحمل أهم."),
v("بدائل اللبأ","رعاية الحملان","بديل غذائي","لتوفير الأجسام المناعية والطاقة للحمل الذي لم يحصل على لبأ كافٍ إذا كان بديلاً حقيقياً.","في الساعات الأولى وفق تعليمات المنتج؛ الأولوية للبأ الأم السليم.","بديل الحليب ليس بديلاً للبأ؛ التأخر يقلل امتصاص الأجسام المناعية.","يرتبط بساعات الحياة الأولى والكمية وتركيز المنتج.","ميّز بين بديل اللبأ ومكمل اللبأ وبديل الحليب."),
v("بدائل حليب الحملان","رعاية الحملان","بديل حليب","لتغذية الحملان عند تعذر الرضاعة أو ضمن التربية الاصطناعية.","بعد ضمان حصول الحمل على اللبأ أولاً.","الخلط الخاطئ أو التغيير المفاجئ يسبب اضطرابات هضمية.","وجبات حسب العمر والكمية وطريقة الخلط على العبوة.","لا يحل محل اللبأ في الساعات الأولى."),
v("زجاجة رضاعة الحملان","رعاية الحملان","أداة تغذية","لإعطاء اللبأ أو بديل الحليب بطريقة مضبوطة.","عند الرضاعة اليدوية.","التدفق السريع أو الوضعية الخاطئة قد يسببان شفطاً رئوياً؛ التلوث ينقل العدوى.","تنظيف وتعقيم بعد كل استخدام؛ الرضعات حسب العمر.","لا تسكب السوائل بالقوة في فم حمل ضعيف."),
v("مضادات حيوية بيطرية بوصفة","أدوية بوصفة بيطرية","مضاد ميكروبي","لعلاج عدوى بكتيرية مشخصة أو مرجحة بعد الفحص.","بوصفة تحدد المادة والجرعة والطريق والمدة.","المقاومة والحساسية والسمية وبقايا الدواء في اللحم والحليب.","عند الحاجة الطبية فقط؛ لا تكرر وصفة قديمة.","احترام فترات السحب ومنع الاستخدام العشوائي."),
v("مضادات التهاب ومسكنات بوصفة","أدوية بوصفة بيطرية","مسكن/مضاد التهاب","لتخفيف الألم والالتهاب والحمى في حالات مختارة.","بعد تشخيص السبب ومراجعة الجفاف والكلى والجهاز الهضمي.","قرحة أو أذية كلوية أو نزف؛ لا تجمع مضادات الالتهاب دون توجيه.","حسب المادة والحالة والوصفة؛ لا موعد سنوياً.","تخفيف الألم لا يغني عن علاج السبب.")
)

private val vetGroups=listOf("الكل","اللقاحات","مضادات الطفيليات الداخلية","الطفيليات الخارجية","التطهير والنظافة","علاج الضرع","الجهاز الهضمي والجفاف","الفيتامينات والمكملات","التكاثر والولادة","رعاية الحملان","أدوية بوصفة بيطرية")

@Composable
fun VeterinaryHerdScreen(modifier:Modifier=Modifier,onBack:()->Unit){
 var query by rememberSaveable{mutableStateOf("")}
 var group by rememberSaveable{mutableStateOf("الكل")}
 var expanded by rememberSaveable{mutableStateOf<String?>(null)}
 val visible=vetItems.filter{(group=="الكل"||it.group==group)&&(query.isBlank()||listOf(it.name,it.group,it.purpose,it.use).any{x->x.contains(query,true)})}
 LazyColumn(modifier.fillMaxSize().background(VC),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Row(verticalAlignment=Alignment.CenterVertically){Surface(color=VG,shape=RoundedCornerShape(13.dp)){Text("⚕️",fontSize=25.sp,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("بيطري القطيع",color=VG,fontSize=22.sp,fontWeight=FontWeight.ExtraBold);Text("دليل الأدوية واللقاحات والعلاجات",color=VM,fontSize=11.sp)};TextButton(onClick=onBack){Text("الرئيسية",color=VG)}}}
  item{Surface(color=VG,shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("اعرف المنتج قبل استعماله",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold);Text("دواعي الاستعمال والمخاطر والتوقيت. الجرعات النهائية تُراجع من نشرة المنتج والطبيب البيطري.",color=Color.White,fontSize=11.sp,lineHeight=18.sp);Text(vetItems.size.toString()+" صنفاً بيطرياً • "+(vetGroups.size-1).toString()+" مجموعات",color=Color.White,fontSize=10.sp)}}}
  item{OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("ابحث عن دواء أو لقاح")},leadingIcon={Icon(Icons.Outlined.Search,null,tint=VG)},shape=RoundedCornerShape(14.dp))}
  item{Text("الأقسام",color=VI,fontSize=15.sp,fontWeight=FontWeight.Bold)}
  item{Column(verticalArrangement=Arrangement.spacedBy(6.dp)){vetGroups.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{g->FilterChip(selected=group==g,onClick={group=g;expanded=null},label={Text(g,fontSize=9.sp,maxLines=1)},modifier=Modifier.weight(1f),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=VG.copy(alpha=.14f),selectedLabelColor=VG))};if(row.size==1)Spacer(Modifier.weight(1f))}}}}
  item{Row(verticalAlignment=Alignment.CenterVertically){Text("المنتجات",fontSize=16.sp,fontWeight=FontWeight.Bold,color=VI);Spacer(Modifier.weight(1f));Text(visible.size.toString()+" صنف",fontSize=10.sp,color=VM)}}
  items(visible,key={it.name}){p->val open=expanded==p.name
   Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,VL)){
    Column(Modifier.fillMaxWidth().clickable{expanded=if(open)null else p.name}.padding(11.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
      ProductPackArt(p.type,p.name,Modifier.width(90.dp).height(98.dp))
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(p.name,color=VI,fontSize=13.sp,fontWeight=FontWeight.Bold,lineHeight=18.sp);Text(p.group,color=VM,fontSize=10.sp);Surface(color=VG.copy(alpha=.1f),shape=RoundedCornerShape(20.dp)){Text(p.type,color=VG,fontSize=9.sp,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))};Text(if(open)"إخفاء التفاصيل" else "اضغط لعرض الاستعمال والمخاطر",color=VG,fontSize=10.sp,fontWeight=FontWeight.SemiBold)}
     }
     if(open){Divider(color=VL);VetDetail("دواعي الاستعمال",p.purpose);VetDetail("متى ولماذا يستخدم؟",p.use);VetDetail("المخاطر والاحتياطات",p.risks);VetDetail("الترتيب الزمني خلال السنة",p.timing);Surface(color=Color(0xFFFFF4E7),shape=RoundedCornerShape(11.dp)){Row(Modifier.fillMaxWidth().padding(9.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top){Icon(Icons.Outlined.WarningAmber,null,tint=Color(0xFF9A6729),modifier=Modifier.size(17.dp));Text(p.caution,color=VI,fontSize=10.sp,lineHeight=17.sp)}}}
    }
   }
  }
  item{Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,VL)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("المراجع العلمية ومنهجية التحرير",color=VG,fontSize=13.sp,fontWeight=FontWeight.Bold);Text("• WOAH: المعايير الدولية للصحة الحيوانية والتحصين ومكافحة الأمراض.",color=VI,fontSize=10.sp);Text("• FAO: صحة الأغنام والأمن الحيوي ومكافحة الطفيليات.",color=VI,fontSize=10.sp);Text("• MSD Veterinary Manual: الأمراض والتشخيص والرعاية السريرية للمجترات الصغيرة.",color=VI,fontSize=10.sp);Text("• نشرات SPC الرسمية: الجرعة والفاصل وفترة السحب وموانع الاستعمال لكل منتج مسجل.",color=VI,fontSize=10.sp);Text("لا تُستنتج الجرعة أو فترة السحب من اسم المادة وحده. الصور هنا رسومات تعريفية وليست عبوات تجارية حقيقية. تُضاف صور العبوات الفعلية ونشراتها عند توفرها.",color=VM,fontSize=10.sp,lineHeight=16.sp)}}}
 }
}

@Composable private fun VetDetail(title:String,body:String){Column(verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,color=VG,fontSize=11.sp,fontWeight=FontWeight.Bold);Text(body,color=VI,fontSize=10.sp,lineHeight=17.sp)}}

@Composable private fun ProductPackArt(type:String,name:String,modifier:Modifier=Modifier){
 val vaccine=type.contains("لقاح")||type.contains("حي")
 val accent=if(vaccine)Color(0xFF647C69) else Color(0xFF8B9A83)
 Surface(modifier,color=Color(0xFFF3F0F6),shape=RoundedCornerShape(11.dp)){
  Box(contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)){
   Box(Modifier.width(56.dp).height(59.dp).background(Color.White,RoundedCornerShape(5.dp)).border(1.dp,VL,RoundedCornerShape(5.dp)).padding(4.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)){Box(Modifier.fillMaxWidth().height(6.dp).background(accent,RoundedCornerShape(2.dp)));Text(if(vaccine)"VACCINE" else "VETERINARY",fontSize=6.sp,fontWeight=FontWeight.Bold,color=accent);Icon(Icons.Outlined.MedicalServices,null,tint=accent,modifier=Modifier.size(19.dp));Text(name.take(13),fontSize=5.sp,color=VI,maxLines=2,lineHeight=6.sp)}}
   Box(Modifier.width(18.dp).height(6.dp).background(Color(0xFFD6D9D5),RoundedCornerShape(2.dp)))
   Box(Modifier.width(34.dp).height(24.dp).background(Color(0xFFE5EBE2),RoundedCornerShape(5.dp)).border(1.dp,Color(0xFFD2DCCF),RoundedCornerShape(5.dp)),contentAlignment=Alignment.Center){Text("VET",fontSize=8.sp,color=accent,fontWeight=FontWeight.Bold)}
  }}
 }
}
