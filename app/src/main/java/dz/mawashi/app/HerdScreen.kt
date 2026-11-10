package dz.mawashi.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val HerdGreen = Color(0xFF526B55)
private val HerdCream = Color(0xFFF7F3EA)
private val HerdInk = Color(0xFF27332A)
private val HerdMuted = Color(0xFF778078)
private val HerdLine = Color(0xFFE8E4DA)
private const val HERD_PREFS = "mawashi_herd_local"
private const val HERD_KEY = "animals"
internal data class HerdAnimal(val id:String,val tag:String,val kind:String,val ageMonths:Int,val health:String,val notes:String,val weightKg:Double?=null,val birthDate:String="",val birthWeightKg:Double?=null,val motherTag:String="")

internal fun loadHerd(c:Context):List<HerdAnimal> = try {
 val a=JSONArray(c.getSharedPreferences(HERD_PREFS,0).getString(HERD_KEY,"[]")?:"[]")
 buildList { for(i in 0 until a.length()){val x=a.getJSONObject(i);add(HerdAnimal(x.optString("id"),x.optString("tag"),x.optString("kind","نعجة"),x.optInt("ageMonths"),x.optString("health","جيدة"),x.optString("notes"),if(x.has("weightKg")&&!x.isNull("weightKg"))x.optDouble("weightKg") else null,x.optString("birthDate"),if(x.has("birthWeightKg")&&!x.isNull("birthWeightKg"))x.optDouble("birthWeightKg") else null,x.optString("motherTag")))} }
} catch(_:Exception){emptyList()}
private fun saveHerd(c:Context,list:List<HerdAnimal>){val a=JSONArray();list.forEach{x->a.put(JSONObject().apply{put("id",x.id);put("tag",x.tag);put("kind",x.kind);put("ageMonths",x.ageMonths);put("health",x.health);put("notes",x.notes);put("weightKg",x.weightKg);put("birthDate",x.birthDate);put("birthWeightKg",x.birthWeightKg);put("motherTag",x.motherTag)})};c.getSharedPreferences(HERD_PREFS,0).edit().putString(HERD_KEY,a.toString()).apply()}

@Composable fun HerdScreen(modifier:Modifier=Modifier){
 val context=LocalContext.current
 val animals=remember{mutableStateListOf<HerdAnimal>().apply{addAll(loadHerd(context))}}
 var search by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("الكل")}
 var editor by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<HerdAnimal?>(null)};var deleting by remember{mutableStateOf<HerdAnimal?>(null)}
 val visible=animals.filter{it.tag.contains(search.trim(),true)&&(filter=="الكل"||it.kind==filter)}
 Column(modifier.fillMaxSize().background(HerdCream).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Spacer(Modifier.height(7.dp))
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("القطيع",color=HerdInk,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Text("متابعة كل نعجة وحمل بصحة ووضوح",color=HerdMuted,fontSize=12.sp)};FilledTonalIconButton(onClick={selected=null;editor=true}){Icon(Icons.Outlined.Add,"إضافة",tint=HerdGreen)}}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){HerdStat("إجمالي القطيع",animals.size.toString(),Modifier.weight(1f));HerdStat("النعاج",animals.count{it.kind=="نعجة"}.toString(),Modifier.weight(1f));HerdStat("الحملان",animals.count{it.kind=="حمل/رخلة"}.toString(),Modifier.weight(1f))}
  OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text("ابحث برقم التعريف",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("الكل","نعجة","كبش","حمل/رخلة").forEach{FilterChip(filter==it,{filter=it},label={Text(it,fontSize=10.sp)})}}
  if(visible.isEmpty()){
   Column(Modifier.fillMaxWidth().weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
    EweLogo(76);Spacer(Modifier.height(12.dp));Text(if(animals.isEmpty())"ابدأ بتسجيل حيوانات القطيع" else "لا توجد نتائج مطابقة",color=HerdInk,fontWeight=FontWeight.Bold)
    Text(if(animals.isEmpty())"امنح كل حيوان رقم تعريف لتسهيل متابعته" else "جرّب تغيير البحث أو نوع الحيوان",color=HerdMuted,fontSize=11.sp)
    if(animals.isEmpty()){Spacer(Modifier.height(12.dp));Button(onClick={selected=null;editor=true},colors=ButtonDefaults.buttonColors(containerColor=HerdGreen)){Icon(Icons.Outlined.Add,null);Text("إضافة أول حيوان")}}
   }
  }else{
   Text("الحيوانات المسجلة (${visible.size})",color=HerdInk,fontWeight=FontWeight.Bold,fontSize=14.sp)
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){items(visible,key={it.id}){a->Surface(onClick={selected=a},modifier=Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){EweLogo(45);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(a.tag,color=HerdInk,fontWeight=FontWeight.Bold,fontSize=14.sp);Text("${a.kind} • ${a.ageMonths} شهر • ${a.health}",color=HerdMuted,fontSize=10.sp);Text("الوزن الحالي: ${a.weightKg?.let{"%.2f".format(java.util.Locale.US,it)}?.plus(" كغ")?:"غير مسجل"}",color=HerdGreen,fontSize=10.sp,fontWeight=FontWeight.SemiBold);if(a.kind=="حمل/رخلة")Text("الميلاد: ${a.birthDate.ifBlank{"غير مسجل"}} • وزن الميلاد: ${a.birthWeightKg?.let{"%.2f".format(java.util.Locale.US,it)+" كغ"}?:"غير مسجل"} • الأم: ${a.motherTag.ifBlank{"غير مسجل"}}",color=HerdMuted,fontSize=9.sp);if(a.notes.isNotBlank())Text(a.notes,color=HerdMuted,fontSize=10.sp,maxLines=1)};IconButton(onClick={selected=a;editor=true}){Icon(Icons.Outlined.Edit,"تعديل",tint=HerdGreen)};IconButton(onClick={deleting=a}){Icon(Icons.Outlined.DeleteOutline,"حذف",tint=HerdMuted)}}}}}
 }
 }
 if(editor)HerdEditor(selected,animals.filter{it.id!=selected?.id}.map{it.tag},{editor=false;selected=null}){a->val i=animals.indexOfFirst{it.id==a.id};if(i>=0)animals[i]=a else animals.add(a);saveHerd(context,animals.toList());editor=false;selected=null}
 deleting?.let{a->AlertDialog(onDismissRequest={deleting=null},title={Text("حذف الحيوان؟")},text={Text("سيتم حذف سجل ${a.tag} من هذا الجهاز.")},confirmButton={TextButton(onClick={animals.removeAll{it.id==a.id};saveHerd(context,animals.toList());deleting=null}){Text("حذف",color=Color(0xFFB3261E))}},dismissButton={TextButton(onClick={deleting=null}){Text("إلغاء")}})}
}

@Composable private fun EweLogo(size:Int){
 Box(Modifier.size(size.dp).background(Color(0xFFE8EDE5),CircleShape),contentAlignment=Alignment.Center){
  Text("🐑",fontSize=(size*.55f).sp)
 }
}
@Composable private fun HerdStat(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,color=Color.White,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,HerdLine)){Column(Modifier.padding(10.dp)){Text(value,color=HerdGreen,fontWeight=FontWeight.ExtraBold,fontSize=20.sp);Text(label,color=HerdMuted,fontSize=9.sp,maxLines=1)}}}
@Composable private fun HerdEditor(initial:HerdAnimal?,existing:List<String>,dismiss:()->Unit,save:(HerdAnimal)->Unit){
 var tag by remember(initial){mutableStateOf(initial?.tag?:"")};var kind by remember(initial){mutableStateOf(initial?.kind?:"نعجة")};var age by remember(initial){mutableStateOf(initial?.ageMonths?.toString()?:"")};var health by remember(initial){mutableStateOf(initial?.health?:"جيدة")};var notes by remember(initial){mutableStateOf(initial?.notes?:"")};var weight by remember(initial){mutableStateOf(initial?.weightKg?.toString()?:"")};var birthDate by remember(initial){mutableStateOf(initial?.birthDate?:"")};var birthWeight by remember(initial){mutableStateOf(initial?.birthWeightKg?.toString()?:"")};var motherTag by remember(initial){mutableStateOf(initial?.motherTag?:"")};var error by remember{mutableStateOf<String?>(null)}
 AlertDialog(onDismissRequest=dismiss,title={Text(if(initial==null)"إضافة حيوان للقطيع" else "تعديل بيانات الحيوان")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(tag,{tag=it;error=null},label={Text("رقم التعريف / الوسم")},singleLine=true,modifier=Modifier.fillMaxWidth());Text("نوع الحيوان");Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){listOf("نعجة","كبش","حمل/رخلة").forEach{FilterChip(kind==it,{kind=it},label={Text(it,fontSize=10.sp)})}};OutlinedTextField(age,{age=it.filter(Char::isDigit);error=null},label={Text("العمر بالأشهر")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(weight,{weight=it.filter{ch->ch in "0123456789.,"};error=null},label={Text("الوزن الحالي (كغ)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth());if(kind=="حمل/رخلة"){Text("بيانات الميلاد",fontWeight=FontWeight.Bold,color=HerdGreen);OutlinedTextField(birthDate,{birthDate=it;error=null},label={Text("تاريخ الميلاد (YYYY-MM-DD)")},placeholder={Text("2026-11-10")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(birthWeight,{birthWeight=it.filter{ch->ch in "0123456789.,"};error=null},label={Text("وزن الميلاد (كغ)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(motherTag,{motherTag=it;error=null},label={Text("رقم تعريف الأم")},singleLine=true,modifier=Modifier.fillMaxWidth())};Text("الحالة الصحية");Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){listOf("جيدة","تحتاج متابعة","مريضة").forEach{FilterChip(health==it,{health=it},label={Text(it,fontSize=10.sp)})}};OutlinedTextField(notes,{notes=it},label={Text("ملاحظات اختيارية")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=3);error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=11.sp)}}},confirmButton={TextButton(onClick={val t=tag.trim();val m=age.toIntOrNull();val w=weight.replace(",",".").toDoubleOrNull();val bw=birthWeight.replace(",",".").toDoubleOrNull();val mother=motherTag.trim();error=when{t.isBlank()->"أدخل رقم التعريف.";existing.any{it.equals(t,true)}->"رقم التعريف مستخدم.";m==null||m !in 0..240->"أدخل عمراً صحيحاً من 0 إلى 240 شهراً.";weight.isNotBlank()&&(w==null||w<=0.0||w>300.0)->"أدخل وزناً صحيحاً بالكيلوغرام.";kind=="حمل/رخلة"&&!birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))->"أدخل تاريخ الميلاد بصيغة YYYY-MM-DD.";kind=="حمل/رخلة"&&(bw==null||bw<=0.0||bw>20.0)->"أدخل وزن الميلاد بالكيلوغرام.";kind=="حمل/رخلة"&&mother.isBlank()->"أدخل رقم تعريف الأم.";else->null};if(error==null)save(HerdAnimal(initial?.id?:java.util.UUID.randomUUID().toString(),t,kind,m!!,health,notes.trim(),w,if(kind=="حمل/رخلة")birthDate.trim() else "",if(kind=="حمل/رخلة")bw else null,if(kind=="حمل/رخلة")mother else ""))}){Text("حفظ",color=HerdGreen)}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}
