package org.chinaquest.app

import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.chinaquest.core.ReviewOutcome
import java.time.LocalDate

/** Finite static pages. No timers, animated transitions, feeds or network calls. */
class MainActivity : Activity() {
    private lateinit var store: QuestStore
    private lateinit var gate: ParentGate
    private lateinit var body: LinearLayout
    private var selected: String? = null
    private var page = "picker"
    private var parentAuthorized = false
    private var relock = false
    private var pendingSnapshot:String? = null
    private fun today() = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store=QuestStore(applicationContext); gate=ParentGate(applicationContext)
        selected=savedInstanceState?.getString("child")
        if(!gate.isConfigured) setup() else when(savedInstanceState?.getString("page")) {
            "lesson" -> if(selected!=null) lesson() else picker()
            "today" -> if(selected!=null) todayPage() else picker()
            else -> picker()
        }
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("child",selected);outState.putString("page",page);super.onSaveInstanceState(outState) }
    override fun onPause() { if(parentAuthorized) { parentAuthorized=false;relock=true };super.onPause() }
    override fun onResume() { super.onResume();if(relock) { relock=false;parentLogin() } }
    override fun onDestroy() { store.close();super.onDestroy() }
    @Deprecated("Platform Activity result for offline document export")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=401) return
        val snapshot=pendingSnapshot;pendingSnapshot=null
        val uri=data?.data
        if(resultCode==RESULT_OK && snapshot!=null && uri!=null) {
            try {
                val stream=contentResolver.openOutputStream(uri) ?: error("File unavailable")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                Toast.makeText(this,"家长报告已导出。请保管好这个文件。",Toast.LENGTH_LONG).show()
            } catch(_:Exception) { Toast.makeText(this,"导出未完成，本机进度不受影响。",Toast.LENGTH_LONG).show() }
        }
    }
    @Deprecated("Static local navigation")
    override fun onBackPressed() { if(!gate.isConfigured) setup() else if(page=="lesson") todayPage() else picker() }

    private fun frame(title:String,kicker:String="我的中国远征 · Misión China",parent:Boolean=false) {
        if(parent) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val scroll=ScrollView(this).apply { isFillViewport=true;isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER;setBackgroundColor(Color.WHITE) }
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(28),dp(24),dp(28),dp(24));layoutTransition=null }
        scroll.addView(body);setContentView(scroll)
        label("CHINA QUEST",16,bold=true)
        label(kicker,17)
        rule();label(title,30,bold=true)
    }
    private fun label(value:String,size:Int=20,bold:Boolean=false,center:Boolean=false):TextView {
        return TextView(this).apply { text=value;textSize=size.toFloat();setTextColor(Color.BLACK);setLineSpacing(dp(4).toFloat(),1f);if(bold) setTypeface(typeface,Typeface.BOLD);if(center) gravity=Gravity.CENTER;setPadding(0,dp(7),0,dp(7));body.addView(this,LinearLayout.LayoutParams(-1,-2)) }
    }
    private fun button(value:String,tag:String,primary:Boolean=false,action:()->Unit):Button {
        return Button(this).apply {
            text=value;textSize=20f;isAllCaps=false;this.tag=tag;minHeight=dp(60);setPadding(dp(12),dp(10),dp(12),dp(10))
            setTextColor(if(primary) Color.WHITE else Color.BLACK)
            background=GradientDrawable().apply { setColor(if(primary) Color.BLACK else Color.WHITE);setStroke(dp(2),Color.BLACK);cornerRadius=dp(4).toFloat() }
            stateListAnimator=null
            body.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12);bottomMargin=dp(2) })
            setOnClickListener { if(isEnabled) { isEnabled=false;action();isEnabled=true } }
        }
    }
    private fun input(hintValue:String,tagValue:String,pin:Boolean=false,initial:String=""):EditText = EditText(this).apply {
        hint=hintValue;tag=tagValue;textSize=22f;minHeight=dp(60);setTextColor(Color.BLACK);setHintTextColor(Color.DKGRAY)
        inputType=if(pin) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setText(initial);isSingleLine=true;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        body.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    private fun rule() { body.addView(View(this).apply { setBackgroundColor(Color.BLACK) },LinearLayout.LayoutParams(-1,dp(2)).apply { topMargin=dp(14);bottomMargin=dp(14) }) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()

    private fun setup() {
        page="setup";frame("先请家长准备",parent=true)
        label("Para mamá o papá",22,bold=true)
        label("每天一点点，在纸上、生活里学习。\n离线使用，两个孩子各有自己的旅程。")
        label("请设置 6–12 位家长 PIN。不要告诉孩子。\nCrea un PIN de 6–12 números.",18)
        val pin=input("家长 PIN","pin",true)
        val confirm=input("再输入一次 / Repetir","confirm-pin",true)
        val error=label("",18)
        button("保存并开始 / Empezar","save-pin",true) {
            val value=pin.text.toString()
            if(!value.matches(Regex("[0-9]{6,12}"))) error.text="请输入 6–12 位数字。"
            else if(value!=confirm.text.toString()) error.text="两次 PIN 不一致，请再试一次。"
            else { gate.setPin(value);picker() }
        }
        label("请安全记住 PIN。本地试用版没有在线找回；忘记时不要卸载，以免丢失进度。",16)
        label("家长陪伴版：字典事实已溯源。中文释义、例句和西语学习内容尚待审核，首次学习请家长一起读、解释和写。",16)
    }
    private fun picker() {
        page="picker";selected=null;parentAuthorized=false
        frame("今天，谁来探索？")
        label("Elige tu viaje",22)
        store.children().forEach { child ->
            val s=store.status(child.id,today())
            button("${child.name}\n${if(s.completed) "今天已完成 · Listo" else "打开我的旅程 · Mi aventura"}",child.id,true) { selected=child.id;todayPage() }
        }
        label("学习 · 创造 · 探索\nLearn · Create · Explore",20,center=true)
        rule();button("家长空间 / Para familias","parent") { parentLogin() }
        label("离线可用 · Sin conexión\n每个人都有自己的节奏。",16)
    }
    private fun todayPage() {
        val id=selected ?: return picker()
        page="today"
        val child=store.child(id);val l=store.lesson(id,today())
        frame("${child.name}，你好！","今天 · ${today()} / Hoy")
        if(l.completed) {
            label("今日印章  ✓",36,bold=true,center=true)
            label("今天的学习已经记住了。\n¡Buen trabajo! Ahora, a explorar.")
            label("去生活里找一个今天学过的字，\n或者和家人讲讲你的发现。")
            label("下次还有新的冒险。休息也很好。",18)
        } else if(l.tasks.isEmpty()) {
            label("今天没有到期复习。",24)
            label("今天可以和家长读一本书，或者在生活里找字。\nHoy puedes leer con tu familia.")
            if(child.reviewOnly || child.target==0) label("家长已选择只复习模式。",18)
            else label("本地新字已学完。请家长安排更多内容。",18)
        } else {
            label("新字  ${l.newCount}     复习  ${l.reviewCount}",27,bold=true)
            label("约 10–15 分钟 · A tu ritmo\n准备纸和笔，请家长陪你一起。")
            label("学习 → 想一想 → 试一试 → 去探索",18)
            if(l.cursor>0) label("已经保存：${l.cursor} / ${l.tasks.size} 步。",18)
            val remaining=(store.dueCount(id,today())-l.reviewCount).coerceAtLeast(0)
            if(remaining>0) label("还有 $remaining 个字待复习，之后慢慢来。",18)
            button(if(l.cursor>0) "继续 / Continuar" else "开始 / Empezar","start",true) { lesson() }
        }
        rule();button("切换孩子 / Cambiar","switch-child") { picker() }
        label("进度自动保存在这台设备。已准备 ${store.plannedDays(id,today())} 天本地计划。",16)
    }
    private fun lesson() {
        val id=selected ?: return picker();page="lesson"
        val l=store.lesson(id,today())
        if(l.completed) return todayPage()
        if(l.cursor>=l.tasks.size) {
            frame("把今天带进生活")
            label("说一个你记得的字。\n在家里找一找，哪里可能用到它？")
            label("和家人分享后，就可以放下屏幕了。\nComparte lo que aprendiste y descansa.")
            button("完成今天 / Terminar","complete",true) { store.complete(id,today());todayPage() }
            return
        }
        val t=l.tasks[l.cursor];val c=store.card(t.characterId)
        val title=when(t.kind) { "LEARN"->"认识一个字";"REVIEW"->"让记忆更清楚";"FIND"->"想一想，找一找";"READ"->"大声说出来";else->"在纸上试一试" }
        frame(title,"${store.child(id).name} · ${l.cursor+1} / ${l.tasks.size}")
        when(t.kind) {
            "LEARN" -> {
                label(c.character,104,bold=true,center=true);label(c.pinyin,32,center=true)
                label("部首 ${c.radical}    ·    ${c.strokes} 画",18,center=true)
                if(c.approvedTeaching) {
                    c.meaningZh?.let { label(it,22) };c.meaningEs?.let { label(it,18) }
                    if(c.words.isNotEmpty()) label(c.words.joinToString(" · "),23)
                    c.sentence?.let { label(it,20) }
                }
                label("① 和家长大声读两遍。\n② 观察这个字，用手指描一描。\n③ 在纸上写三遍，再盖住屏幕写一次。",20)
                label("Lee con tu familia. Mira, traza y escribe en papel.",17)
                if(store.child(id).ageGroup=="AGE_UNDER_6") label("小探索家可以请家长帮忙，先描一遍也很好。",17)
                button("我练习好了 / Listo","learn-done",true) { store.answer(id,l.date,l.cursor,null);lesson() }
            }
            "FIND", "REVIEW" -> {
                label("找出读作这个音的字：",22);label(c.pinyin,42,bold=true,center=true)
                label("Busca el carácter. Puedes pedir ayuda para leer.",17)
                val alternatives=store.cards().filter { it.id!=c.id && it.pinyin!=c.pinyin }.take(2)
                val options=(alternatives+c).sortedBy { (it.id+l.date.toString()+l.cursor).hashCode() }
                options.forEach { candidate -> button(candidate.character,"answer-${candidate.id}",false) {
                    val outcome=if(candidate.id==c.id) ReviewOutcome.CORRECT else ReviewOutcome.WRONG
                    store.answer(id,l.date,l.cursor,outcome);feedback(c,outcome)
                } }
                button("还不会 / Todavía no","dont-know") { store.answer(id,l.date,l.cursor,ReviewOutcome.WRONG);feedback(c,ReviewOutcome.WRONG) }
            }
            "READ", "WRITE" -> {
                val write=t.kind=="WRITE"
                if(write) { label(c.pinyin,38,bold=true,center=true);label("想想这个字，在纸上写一次。\nEscríbelo en papel sin mirar.") }
                else { label(c.character,104,bold=true,center=true);label("大声读两遍，请家长听一听。\nLéelo en voz alta con tu familia.") }
                button("看看提示 / Ver pista","reveal") {
                    label("${c.character}   ${c.pinyin}",32,bold=true,center=true)
                    label("这是自己的判断，家长之后会帮你检查。",17)
                    button("我会 / Ya puedo","self-correct",true) { store.answer(id,l.date,l.cursor,ReviewOutcome.CORRECT);lesson() }
                    button("还在学 / Estoy aprendiendo","self-almost") { store.answer(id,l.date,l.cursor,ReviewOutcome.ALMOST);lesson() }
                    button("还不会 / Todavía no","self-wrong") { store.answer(id,l.date,l.cursor,ReviewOutcome.WRONG);lesson() }
                    body.findViewWithTag<Button>("reveal").visibility=View.GONE
                }
            }
        }
        rule();button("先休息，保存进度 / Pausa","pause") { todayPage() }
    }
    private fun feedback(c:CharacterCard,outcome:ReviewOutcome) {
        page="lesson";frame(if(outcome==ReviewOutcome.CORRECT) "你认出来了" else "一起再看一次")
        label(c.character,104,bold=true,center=true);label(c.pinyin,32,center=true)
        label(if(outcome==ReviewOutcome.CORRECT) "记忆会慢慢变稳。\nLo recordaremos otro día." else "没关系。看看它的样子，再读一遍。\nEstá bien. Mira y lee otra vez.")
        if(outcome!=ReviewOutcome.CORRECT) label("已记入待复习，不会清除以前的努力。",18)
        button("继续 / Continuar","continue",true) { lesson() }
    }
    private fun parentLogin() {
        page="parent-login";parentAuthorized=false
        frame("家长空间",parent=true);label("请输入家长 PIN / PIN familiar",20)
        val pin=input("6–12 位 PIN","parent-pin",true);val error=label("",18)
        button("进入 / Entrar","unlock-parent",true) {
            if(gate.verify(pin.text.toString())) { parentAuthorized=true;parentHome() }
            else { error.text=if(gate.remainingLockSeconds()>0) "尝试次数较多，请 ${gate.remainingLockSeconds()} 秒后再试。" else "PIN 不正确。";pin.text.clear() }
        }
        button("返回 / Volver","back") { picker() }
    }
    private fun parentHome() {
        if(!parentAuthorized) return parentLogin()
        page="parent";frame("家庭今日概览","${today()} · 仅保存在本机",true)
        store.children().forEach { child ->
            val s=store.status(child.id,today())
            rule();label(child.name,25,bold=true)
            label(if(s.completed) "✓ 今天已完成" else if(s.cursor>0) "学习中 · ${s.cursor} / ${s.total} 步" else "今天尚未开始",22)
            label("已接触 ${s.learned} 字 · 学习／复习中 ${s.reviewing} · 稳定掌握 ${s.mastered}",18)
            label("日目标 ${child.target} 个新字 · ${if(child.reviewOnly) "只复习" else "新字＋复习"}\n日印章 ${store.stampCount(child.id)} · 到期复习 ${store.dueCount(child.id,today())}",18)
            button("调整 ${child.name}","settings-${child.id}") { childSettings(child.id) }
        }
        rule();label("内容与能力说明",22,bold=true)
        label("178 个字的字形、拼音、英文基础释义、部首与笔画来自 Unicode Unihan。中文／西语释义和例句仍待人工审核，未审内容不会给孩子展示。",17)
        label("朗读与写字目前为自评；未进行自动语音或笔迹判断。稳定掌握需要多日、多维度及家长测试证据。",17)
        button("字典与来源 / Diccionario","dictionary") { dictionary() }
        button("导出家长报告 / Exportar informe","export-report") {
            if(parentAuthorized) {
                pendingSnapshot=store.parentSnapshot(today()).toString(2)
                val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
                    putExtra(Intent.EXTRA_TITLE,"ChinaQuest-parent-${today()}.json")
                }
                try { startActivityForResult(intent,401) }
                catch(_:ActivityNotFoundException) { pendingSnapshot=null;Toast.makeText(this,"此设备没有可用的文件保存器。",Toast.LENGTH_LONG).show() }
            }
        }
        label("报告可手动带到电脑端家长看板。文件含孩子称呼与学习进度，请留在家庭内部；它不是完整备份。",17)
        label("云同步尚未启用。学习进度安全保存在本机。不要卸载或清除应用数据。",17)
        button("锁定并返回孩子","lock-parent",true) { picker() }
    }
    private fun childSettings(id:String) {
        if(!parentAuthorized) return parentLogin()
        val child=store.child(id);page="parent"
        frame("调整学习节奏",parent=true)
        val name=input("称呼","child-name",initial=child.name)
        label("每天新字数量：0–10。未开始的计划会更新。",18)
        val target=input("0–10","daily-target",initial=child.target.toString()).apply { inputType=InputType.TYPE_CLASS_NUMBER }
        val review=CheckBox(this).apply { text="只复习 / Solo repaso";textSize=22f;minHeight=dp(56);isChecked=child.reviewOnly;tag="review-only";setTextColor(Color.BLACK);body.addView(this) }
        val error=label("",18)
        button("保存 / Guardar","save-settings",true) {
            val amount=target.text.toString().toIntOrNull()
            if(amount==null || amount !in 0..10 || name.text.toString().trim().length !in 1..24) error.text="请填写 1–24 字的称呼和 0–10 的目标。"
            else { store.updateChild(id,name.text.toString(),amount,review.isChecked,today());parentHome() }
        }
        label("已开始的当天课程保持不变。可随时暂停，不必追赶。",17)
        button("返回","parent-back") { parentHome() }
    }
    private fun dictionary(index:Int=0) {
        if(!parentAuthorized) return parentLogin()
        page="parent";val all=store.cards();val c=all[index]
        frame("家长字典 ${index+1} / ${all.size}",parent=true)
        label("${c.character}   ${c.pinyin}",46,bold=true,center=true)
        label("部首 ${c.radical} · ${c.strokes} 画",22)
        label("Unicode 英文释义：\n${c.meaningEn}",20)
        label("请家长用孩子理解的语言解释，带孩子读词语或造句。词义需结合上下文；一字可能有多音。",18)
        label("来源：Unicode 17.0.0 · Unihan\nkMandarin / kDefinition / kRSUnicode / kTotalStrokes",16)
        if(index<all.lastIndex) button("下一个字","next-word") { dictionary(index+1) }
        if(index>0) button("上一个字","prev-word") { dictionary(index-1) }
        button("返回家长概览","dictionary-back") { parentHome() }
    }
}
