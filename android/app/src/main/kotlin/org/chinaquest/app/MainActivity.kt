package org.chinaquest.app

import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.media.AudioManager
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
    private lateinit var speech: OfflineChineseSpeech
    private lateinit var creative: CreativeStore
    private var audioMessage: TextView? = null
    private lateinit var body: LinearLayout
    private var selected: String? = null
    private var page = "picker"
    private var parentAuthorized = false
    private var relock = false
    private var pendingSnapshot:String? = null
    private var creativeCharacter:String? = null
    private var creativeReturnPage="workshop"
    private var paintPickerIndex=0
    private var paintColor=Color.rgb(207,89,62)
    private var paintTool=ColoringTool.FILL
    private data class PendingArtwork(val childId:String,val characterId:String,val json:String)
    private var pendingArtwork:PendingArtwork?=null
    private val palette=listOf("红" to Color.rgb(207,89,62),"黄" to Color.rgb(228,186,69),"蓝" to Color.rgb(80,146,198),
        "绿" to Color.rgb(105,163,120),"紫" to Color.rgb(146,115,181),"黑" to Color.rgb(48,48,48))
    private fun today() = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream=AudioManager.STREAM_MUSIC
        store=QuestStore(applicationContext); gate=ParentGate(applicationContext)
        speech=OfflineChineseSpeech(this, onState={}, onMessage={ message -> runOnUiThread {
            audioMessage?.apply { text=message;visibility=View.VISIBLE }
        } })
        val cards=store.cards()
        creative=CreativeStore(applicationContext,store.children().map { it.id }.toSet(),cards.map { it.id }.toSet(),
            cards.mapNotNull { SceneCatalog.forCharacter(it.id)?.id }.toSet())
        selected=savedInstanceState?.getString("child")
        creativeCharacter=savedInstanceState?.getString("creative-character")?.takeIf { id -> cards.any { it.id==id } }
        creativeReturnPage=savedInstanceState?.getString("creative-return") ?: "workshop"
        paintPickerIndex=(savedInstanceState?.getInt("paint-picker") ?: 0).coerceIn(0,(cards.size-1)/12)
        paintColor=(savedInstanceState?.getInt("paint-color") ?: palette[0].second).takeIf { color -> palette.any { it.second==color } } ?: palette[0].second
        paintTool=if(savedInstanceState?.getString("paint-tool")=="BRUSH") ColoringTool.BRUSH else ColoringTool.FILL
        savedInstanceState?.getString("unsaved-artwork")?.let { json ->
            if(selected!=null && creativeCharacter!=null && json.toByteArray(Charsets.UTF_8).size<=CreativeStore.MAX_ART_BYTES)
                pendingArtwork=PendingArtwork(selected!!,creativeCharacter!!,json)
        }
        if(!gate.isConfigured) setup() else when(savedInstanceState?.getString("page")) {
            "lesson" -> if(selected!=null) lesson() else picker()
            "today" -> if(selected!=null) todayPage() else picker()
            "workshop" -> if(selected!=null) workshop() else picker()
            "paint-picker" -> if(selected!=null) paintPicker() else picker()
            "painting" -> if(selected!=null && creativeCharacter!=null) painting() else picker()
            "scene" -> if(selected!=null && creativeCharacter!=null) scenePage() else picker()
            else -> picker()
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("child",selected);outState.putString("page",page)
        outState.putString("creative-character",creativeCharacter);outState.putString("creative-return",creativeReturnPage)
        outState.putInt("paint-picker",paintPickerIndex);outState.putInt("paint-color",paintColor);outState.putString("paint-tool",paintTool.name)
        pendingArtwork?.let { outState.putString("unsaved-artwork",it.json) }
        super.onSaveInstanceState(outState)
    }
    override fun onPause() { speech.stop();if(parentAuthorized) { parentAuthorized=false;relock=true };super.onPause() }
    override fun onResume() { super.onResume();if(relock) { relock=false;parentLogin() } }
    override fun onDestroy() { speech.close();creative.close();store.close();super.onDestroy() }
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
    override fun onBackPressed() {
        if(!gate.isConfigured) setup() else when(page) {
            "painting","scene" -> creativeBack()
            "paint-picker" -> workshop()
            "workshop","lesson" -> todayPage()
            else -> picker()
        }
    }

    private fun frame(title:String,kicker:String="我的中国远征 · Misión China",parent:Boolean=false) {
        speech.stop();audioMessage=null
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

    private fun actionRow(items:List<Triple<String,String,()->Unit>>):List<Button> {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutTransition=null }
        val buttons=items.map { (text,tag,action) ->
            button(text,tag,action=action).also { view ->
                body.removeView(view)
                row.addView(view,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(6),dp(3),dp(2)) })
            }
        }
        body.addView(row,LinearLayout.LayoutParams(-1,-2))
        return buttons
    }

    private fun listen(text:String, tag:String, caption:String="听这个字 / Escuchar") {
        button(caption,tag) { audioMessage?.visibility=View.GONE;speech.speak(text) }
        if(audioMessage==null) audioMessage=label("",16).apply { visibility=View.GONE }
    }
    private fun wordExamples(card:CharacterCard) {
        val words=store.words(card.id)
        if(words.isEmpty()) return
        label("常用词 · 点按听读",18,bold=true)
        words.take(2).forEachIndexed { index, word ->
            listen(word.text,"listen-word-$index","${word.text}   ${word.pinyin}  · 听词")
        }
        if(card.pinyin.contains(' ') || words.any { word ->
            word.pinyin.split(' ').getOrNull(word.text.indexOf(card.character))?.let { it!=card.pinyin }==true
        }) label("字在词里的读音可能不同，跟着词语听。",16)
    }

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
        val known=store.knownCharacterIds(id).size
        if(known>0) label("已按你的基础跳过 $known 个认识的字。之后会少量抽查。",17)
        button("创作小工坊 · 涂色与情景 / Crear","workshop") { workshop() }
        button("这些字太容易？请家长调整起点","adjust-start") { parentLogin(id) }
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
        val title=when(t.kind) { "LEARN"->"认识一个字";"REVIEW"->"让记忆更清楚";"FIND"->"想一想，找一找";"READ"->"大声说出来";"WORD"->"把字用在词语里";else->"在纸上试一试" }
        frame(title,"${store.child(id).name} · ${l.cursor+1} / ${l.tasks.size}")
        when(t.kind) {
            "LEARN" -> {
                label(c.character,88,bold=true,center=true).apply {
                    contentDescription="${c.character}，点按听读";setOnClickListener { audioMessage?.visibility=View.GONE;speech.speak(c.character) }
                }
                label(c.pinyin,32,center=true)
                listen(c.character,"listen-character")
                label("部首 ${c.radical}    ·    ${c.strokes} 画",18,center=true)
                if(c.approvedTeaching) {
                    c.meaningZh?.let { label(it,22) };c.meaningEs?.let { label(it,18) }
                    if(c.words.isNotEmpty()) label(c.words.joinToString(" · "),23)
                    c.sentence?.let { label(it,20) }
                }
                wordExamples(c)
                val creativeActions=mutableListOf(Triple("涂这个字","paint-this") { openPainting(c.id,"lesson") })
                if(SceneCatalog.forCharacter(c.id)!=null) creativeActions.add(Triple("玩一段情景","scene-this") { openScene(c.id,"lesson") })
                actionRow(creativeActions)
                label("① 听一听，再自己读两遍。\n② 选一个词，说说它在哪里会用到。\n③ 在纸上练写，再盖住屏幕写一次。",20)
                label("Lee con tu familia. Mira, traza y escribe en papel.",17)
                if(store.child(id).ageGroup=="AGE_UNDER_6") label("小探索家可以请家长帮忙，先描一遍也很好。",17)
                button("我练习好了 / Listo","learn-done",true) { store.answer(id,l.date,l.cursor,null);lesson() }
            }
            "FIND", "REVIEW" -> {
                label("找出读作这个音的字：",22);label(c.pinyin,42,bold=true,center=true)
                listen(c.character,"listen-question","听一遍题目 / Escuchar")
                label("Busca el carácter. Puedes pedir ayuda para leer.",17)
                val options=QuizChoices.forCard(c,store.cards(),l.tasks.map { it.characterId }.toSet(),
                    "${l.date}:${l.cursor}",if(store.child(id).ageGroup=="AGE_UNDER_6") 3 else 4)
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
                    listen(c.character,"listen-reveal")
                    label("这是自己的判断，家长之后会帮你检查。",17)
                    button("我会 / Ya puedo","self-correct",true) { store.answer(id,l.date,l.cursor,ReviewOutcome.CORRECT);lesson() }
                    button("还在学 / Estoy aprendiendo","self-almost") { store.answer(id,l.date,l.cursor,ReviewOutcome.ALMOST);lesson() }
                    button("还不会 / Todavía no","self-wrong") { store.answer(id,l.date,l.cursor,ReviewOutcome.WRONG);lesson() }
                    body.findViewWithTag<Button>("reveal").visibility=View.GONE
                }
            }
            "WORD" -> {
                val word=store.words(c.id).first()
                label(word.text,52,bold=true,center=true)
                label("找一找：词里哪个是「${c.character}」？\n先自己读，再用这个词说一句话。",22)
                if(store.child(id).ageGroup=="AGE_UNDER_6") label("可以指一指、说一个词，也可以和家长一起说。",18)
                else label("试着说说家里、学校或旅行时会怎么用它。",18)
                label("Busca el carácter, lee la palabra y úsala al hablar.",17)
                button("看看拼音和听读提示 / Ver pista","reveal") {
                    label(word.pinyin,32,center=true)
                    listen(word.text,"listen-word-task","听这个词 / Escuchar")
                    label("和家长说完后，记录自己的感觉；这不是自动评分。",18)
                    button("我能读，也试着用了 / Ya puedo","self-correct",true) { store.answer(id,l.date,l.cursor,ReviewOutcome.CORRECT);lesson() }
                    button("需要帮忙 / Con ayuda","self-almost") { store.answer(id,l.date,l.cursor,ReviewOutcome.ALMOST);lesson() }
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
        listen(c.character,"listen-feedback");wordExamples(c)
        label(if(outcome==ReviewOutcome.CORRECT) "记忆会慢慢变稳。\nLo recordaremos otro día." else "没关系。看看它的样子，再读一遍。\nEstá bien. Mira y lee otra vez.")
        if(outcome!=ReviewOutcome.CORRECT) label("已记入待复习，不会清除以前的努力。",18)
        button("继续 / Continuar","continue",true) { lesson() }
    }

    private fun workshop() {
        val id=selected ?: return picker();page="workshop"
        frame("创作小工坊","${store.child(id).name} · 学习 · 创造 · 探索")
        label("挑一个玩一会儿，再去纸上或生活里试试。\nElige una actividad y crea a tu ritmo.",19)
        button("给汉字涂颜色 / Colorear","choose-coloring",true) { paintPicker() }
        label("手指或笔都可以。已有 ${creative.artworkCount(id)} 幅本机作品。",17)
        rule();label("点一下，情景变一变",24,bold=true)
        listOf("口" to "吃","开" to "关","水" to "喝").forEach { (left,right) ->
            val pair=listOf(left,right).map { glyph ->
                val card=store.cards().first { it.character==glyph }
                Triple(SceneCatalog.forCharacter(card.id)!!.title,"scene-${card.id}") { openScene(card.id,"workshop") }
            }
            actionRow(pair)
        }
        label("画面等你点才会变化。涂色和情景不计分，也不用每个字都做。",17)
        label("这是根据小朋友的建议做的试用版本。可以喜欢，也可以说不喜欢。",17)
        button("回到今天 / Volver","workshop-back") { todayPage() }
    }
    private fun paintPicker() {
        val id=selected ?: return picker();page="paint-picker"
        val all=store.cards();val part=all.drop(paintPickerIndex*12).take(12)
        frame("选一个字来涂","${store.child(id).name} · 第 ${paintPickerIndex+1} / ${(all.size+11)/12} 页")
        for(row in part.chunked(3)) actionRow(row.map { card ->
            Triple(card.character,"paint-${card.id}") { openPainting(card.id,"paint-picker") }
        }).forEach { it.textSize=34f;it.minHeight=dp(72) }
        if((paintPickerIndex+1)*12<all.size) button("下一页","paint-next") { paintPickerIndex++;paintPicker() }
        if(paintPickerIndex>0) button("上一页","paint-prev") { paintPickerIndex--;paintPicker() }
        label("同一个字会接着上次的作品。每个孩子分别保存。",17)
        button("返回工坊","paint-picker-back") { workshop() }
    }
    private fun openPainting(characterId:String,returnTo:String) {
        creativeCharacter=characterId;creativeReturnPage=returnTo;painting()
    }
    private fun creativeBack() {
        if(page=="painting" && !savePendingArtwork()) {
            body.findViewWithTag<TextView>("artwork-status")?.text="作品还没存好，先留在这里。请家长检查剩余空间，再点返回重试。"
            return
        }
        when(creativeReturnPage) {
            "lesson" -> lesson()
            "today" -> todayPage()
            "paint-picker" -> paintPicker()
            else -> workshop()
        }
    }
    private fun savePendingArtwork():Boolean {
        val pending=pendingArtwork ?: return true
        return try {
            creative.saveArtwork(pending.childId,pending.characterId,pending.json)
            pendingArtwork=null;true
        } catch(_:Exception) { false }
    }
    private fun painting() {
        val id=selected ?: return picker();val characterId=creativeCharacter ?: return workshop()
        val card=store.card(characterId);page="painting"
        frame("给「${card.character}」涂颜色","${store.child(id).name} · 我的作品 / Mi dibujo")
        val drawing=ColoringCanvasView(this).apply {
            character=card.character;selectedColor=paintColor;tool=paintTool;tag="coloring-canvas"
            loadArtwork(pendingArtwork?.takeIf { it.childId==id && it.characterId==characterId }?.json ?: creative.artwork(id,characterId))
        }
        var tools:List<Button> = emptyList()
        fun updateTools() { tools.forEachIndexed { index, button -> button.text=(if((index==0)==(paintTool==ColoringTool.FILL)) "✓ " else "")+if(index==0) "点按填色" else "画笔涂色" } }
        tools=actionRow(listOf(
            Triple("点按填色","tool-fill") { paintTool=ColoringTool.FILL;drawing.tool=paintTool;updateTools() },
            Triple("画笔涂色","tool-brush") { paintTool=ColoringTool.BRUSH;drawing.tool=paintTool;updateTools() }
        ));updateTools()
        val swatches=mutableListOf<Button>()
        fun updatePalette() {
            swatches.forEachIndexed { index, view ->
                val (name,color)=palette[index]
                view.text=(if(color==paintColor) "✓ " else "")+name
                view.setTextColor(if(index==5) Color.WHITE else Color.BLACK)
                view.background=GradientDrawable().apply { setColor(color);setStroke(dp(if(color==paintColor) 4 else 1),Color.BLACK);cornerRadius=dp(4).toFloat() }
                view.contentDescription="$name，${if(color==paintColor) "已选颜色" else "选择颜色"}"
            }
        }
        for(chunk in palette.indices.chunked(3)) swatches.addAll(actionRow(chunk.map { index ->
            Triple(palette[index].first,"color-$index") { paintColor=palette[index].second;drawing.selectedColor=paintColor;updatePalette() }
        }));updatePalette()
        body.addView(drawing,LinearLayout.LayoutParams(-1,dp(310)))
        val status=label("先选颜色，再点字的笔画填色，或换画笔慢慢涂。",17).apply { tag="artwork-status" }
        if(pendingArtwork!=null) status.text="作品还没存好，请家长检查设备空间。画面暂时保留在这里。"
        drawing.onStatusMessage={ status.text=it }
        drawing.onArtworkChanged={ json ->
            pendingArtwork=PendingArtwork(id,characterId,json)
            status.text=if(savePendingArtwork()) "作品已保存在这台设备。/ Guardado." else "暂时没存好，请先留在这里，找家长帮忙。"
        }
        actionRow(listOf(
            Triple("撤回一笔","art-undo") { if(!drawing.undo()) status.text="还没有需要撤回的笔画。" },
            Triple("重新涂","art-clear") { if(drawing.clear()) status.text="已清空画面。可以用「撤回一笔」找回来。" },
            Triple("听这个字","art-listen") { speech.speak(card.character) }
        ))
        audioMessage=status
        label("字的黑色轮廓会保留。这是涂色，不是写字评分；还可以去纸上画。",16)
        button("我画好了 / Listo","art-done",true) { creativeBack() }
        creativeFeedback("coloring")
        button("返回，不必画完","art-back") { creativeBack() }
    }
    private fun openScene(characterId:String,returnTo:String) {
        creativeCharacter=characterId;creativeReturnPage=returnTo;scenePage()
    }
    private fun scenePage() {
        val id=selected ?: return picker();val characterId=creativeCharacter ?: return workshop()
        val definition=SceneCatalog.forCharacter(characterId) ?: return workshop();page="scene"
        var state=runCatching {
            val stored=SceneState(definition.id,creative.sceneStep(id,definition.id));SceneCatalog.stage(stored);stored
        }.getOrElse { SceneCatalog.initial(definition.id) }
        frame(definition.title,"${store.child(id).name} · 我来参与 / Participar")
        val prompt=label("",22,bold=true).apply { tag="scene-prompt" }
        val picture=CharacterSceneView(this).apply { tag="scene-canvas" }
        body.addView(picture,LinearLayout.LayoutParams(-1,dp(310)))
        val status=label("",18).apply { tag="scene-status" }
        lateinit var update:()->Unit
        fun saveState(next:SceneState) {
            try { creative.saveSceneStep(id,definition.id,next.step);state=next;update() }
            catch(_:Exception) { status.text="暂时没存好，请家长帮忙检查。" }
        }
        fun advance() { if(!SceneCatalog.stage(state).complete) saveState(SceneCatalog.advance(state)) }
        val action=button("下一步","scene-action",true) { advance() }
        val replay=button("再试一遍 / Otra vez","scene-replay") { saveState(SceneCatalog.replay(state)) }
        update={
            val stage=SceneCatalog.stage(state)
            prompt.text=stage.prompt
            status.text=if(stage.complete) stage.completionText else "点画面里的物品，或按下面的按钮。"
            action.text=stage.actionLabel;action.visibility=if(stage.complete) View.GONE else View.VISIBLE
            replay.visibility=if(stage.complete) View.VISIBLE else View.GONE
            picture.render(state) { advance() }
        };update()
        listen(definition.character,"scene-listen","听「${definition.character}」 / Escuchar")
        label("这是字的一种生活用法。和家长说说：家里哪里会用到它？",17)
        button("回去休息，或选另一项","scene-done",true) { creativeBack() }
        creativeFeedback("scenes")
    }
    private fun creativeFeedback(featureId:String) {
        val id=selected ?: return
        rule();label("你来做小设计师",21,bold=true)
        val response=label("喜欢也好，不喜欢也可以告诉我们。",17).apply { tag="creative-feedback" }
        fun showChoice() {
            creative.feedback(id).firstOrNull { it.featureId==featureId }?.let {
                response.text="你的选择：${opinionLabel(it.decision)}。想法可以改变，随时再选。"
            }
        }
        showChoice()
        actionRow(CreativeFeedback.entries.map { decision ->
            Triple(opinionLabel(decision),"feedback-${decision.name}") {
                try { creative.saveFeedback(id,featureId,decision);showChoice() }
                catch(_:Exception) { response.text="这次想法没存好，可以先直接告诉家长。" }
            }
        })
        label("想法保存在家庭设备上，不会自动改软件，也不会影响奖励。",16)
    }
    private fun opinionLabel(value:CreativeFeedback)=when(value) {
        CreativeFeedback.KEEP -> "喜欢，保留"
        CreativeFeedback.CHANGE -> "想改一改"
        CreativeFeedback.NO -> "不喜欢"
    }
    private fun parentLogin(placementChild:String?=null) {
        page="parent-login";parentAuthorized=false
        frame("家长空间",parent=true);label("请输入家长 PIN / PIN familiar",20)
        val pin=input("6–12 位 PIN","parent-pin",true);val error=label("",18)
        button("进入 / Entrar","unlock-parent",true) {
            if(gate.verify(pin.text.toString())) { parentAuthorized=true;if(placementChild!=null) placement(placementChild) else parentHome() }
            else { error.text=if(gate.remainingLockSeconds()>0) "尝试次数较多，请 ${gate.remainingLockSeconds()} 秒后再试。" else "PIN 不正确。";pin.text.clear() }
        }
        button("返回 / Volver","back") { picker() }
    }
    private fun parentHome(notice:String?=null) {
        if(!parentAuthorized) return parentLogin()
        page="parent";frame("家庭今日概览","${today()} · 仅保存在本机",true)
        notice?.let { label(it,20,bold=true) }
        store.children().forEach { child ->
            val s=store.status(child.id,today())
            rule();label(child.name,25,bold=true)
            label(if(s.completed) "✓ 今天已完成" else if(s.cursor>0) "学习中 · ${s.cursor} / ${s.total} 步" else "今天尚未开始",22)
            label("已接触 ${s.learned} 字 · 学习／复习中 ${s.reviewing} · 稳定掌握 ${s.mastered}",18)
            label("日目标 ${child.target} 个新字 · ${if(child.reviewOnly) "只复习" else "新字＋复习"}\n日印章 ${store.stampCount(child.id)} · 到期复习 ${store.dueCount(child.id,today())}",18)
            label("原有基础 ${store.knownCharacterIds(child.id).size} 字 · ${if(child.wordPractice) "字词表达练习已开" else "单字练习"}",18)
            button("${child.name} 已经会哪些字？","placement-${child.id}") { placement(child.id) }
            button("调整 ${child.name}","settings-${child.id}") { childSettings(child.id) }
            label("本机涂色作品 ${creative.artworkCount(child.id)} 幅",17)
            creative.feedback(child.id).forEach { opinion ->
                label("${if(opinion.featureId=="coloring") "涂色" else "互动情景"}试用反馈：${opinionLabel(opinion.decision)}",17)
            }
        }
        rule();label("内容与能力说明",22,bold=true)
        label("178 个字的字形、拼音、英文基础释义、部首与笔画来自 Unicode Unihan。中文／西语释义和例句仍待人工审核，未审内容不会给孩子展示。",17)
        label("朗读与写字目前为自评；未进行自动语音或笔迹判断。稳定掌握需要多日、多维度及家长测试证据。",17)
        label("常用词与拼音来自已核对的词典条目。声音为打包的离线中文合成语音；多音字要结合词语听。",17)
        button("字典与来源 / Diccionario","dictionary") { dictionary() }
        button("发音说明与设备音量","audio-help") { audioHelp() }
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
        @Suppress("DEPRECATION")
        val version=packageManager.getPackageInfo(packageName,0).versionName
        label("China Quest · $version",16)
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
        val wordPractice=CheckBox(this).apply { text="增加读词、用词挑战";textSize=22f;minHeight=dp(56);isChecked=child.wordPractice;tag="word-practice";setTextColor(Color.BLACK);body.addView(this) }
        val error=label("",18)
        button("保存 / Guardar","save-settings",true) {
            val amount=target.text.toString().toIntOrNull()
            if(amount==null || amount !in 0..10 || name.text.toString().trim().length !in 1..24) error.text="请填写 1–24 字的称呼和 0–10 的目标。"
            else { store.updateChild(id,name.text.toString(),amount,review.isChecked,today(),wordPractice.isChecked);parentHome() }
        }
        button("先检查已经认识的字","placement-settings") { placement(id) }
        label("已开始的当天课程保持不变。可随时暂停，不必追赶。",17)
        button("返回","parent-back") { parentHome() }
    }
    private fun placement(id:String,index:Int=0,choices:MutableSet<String> = store.knownCharacterIds(id).toMutableSet()) {
        if(!parentAuthorized) return parentLogin()
        page="parent"
        val all=store.cards();val pageSize=12;val group=all.drop(index*pageSize).take(pageSize)
        frame("${store.child(id).name} 的起点","已有基础 · 第 ${index+1} / ${(all.size+pageSize-1)/pageSize} 页",true)
        label("请孩子不看拼音读一读。能认出并读出的字才勾选；不确定的先留着学。",19)
        val count=label("已选择 ${choices.size} 个认识的字",21,bold=true).apply { tag="placement-count" }
        for(row in group.chunked(3)) {
            val layout=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutTransition=null }
            for(card in row) layout.addView(CheckBox(this).apply {
                text=card.character;textSize=34f;setTextColor(Color.BLACK);minHeight=dp(76)
                tag="known-${card.id}";contentDescription="已认识 ${card.character}";isChecked=card.id in choices
                setOnCheckedChangeListener { _, checked ->
                    if(checked) choices.add(card.id) else choices.remove(card.id)
                    count.text="已选择 ${choices.size} 个认识的字"
                }
            },LinearLayout.LayoutParams(0,-2,1f))
            body.addView(layout,LinearLayout.LayoutParams(-1,-2))
        }
        button("这一页都认识","known-page") { choices.addAll(group.map { it.id });placement(id,index,choices) }
        button("清除此页标记","clear-known-page") { choices.removeAll(group.map { it.id }.toSet());placement(id,index,choices) }
        if((index+1)*pageSize<all.size) button("下一页","placement-next") { placement(id,index+1,choices) }
        if(index>0) button("上一页","placement-prev") { placement(id,index-1,choices) }
        label("这些字会跳过新学，一周后开始少量抽查。勾选不等于稳定掌握，不增加印章。",17)
        button("保存起点","save-placement",true) {
            store.setPriorKnowledge(id,choices,today())
            parentHome("起点已保存。未开始的课程已调整；进行中或已完成的课程保留，下次接着新的起点学。")
        }
        button("取消，暂不改变","cancel-placement") { parentHome() }
    }
    private fun audioHelp() {
        if(!parentAuthorized) return parentLogin()
        page="parent";frame("点按听读",parent=true)
        label("字和常用词的声音已放进安装包，断网也能听。没有自动播放，也不会录音。",21)
        listen("你好","test-audio","试听：你好")
        label("听不到时先检查 BOOX 的媒体音量、扬声器或耳机连接。语音为普通话合成声音，多音字请结合下方词语听。",19)
        label("只有未包含的语音才尝试设备本地中文引擎；不会自动下载语音数据。",17)
        button("设备语音设置（可选）","voice-settings") {
            try { startActivity(speech.voiceSetupIntent()) }
            catch(_:ActivityNotFoundException) { Toast.makeText(this,"此设备没有可用的语音设置页。已打包声音仍可离线使用。",Toast.LENGTH_LONG).show() }
        }
        button("返回家长空间","audio-back") { parentHome() }
    }
    private fun dictionary(index:Int=0) {
        if(!parentAuthorized) return parentLogin()
        page="parent";val all=store.cards();val c=all[index]
        frame("家长字典 ${index+1} / ${all.size}",parent=true)
        label("${c.character}   ${c.pinyin}",46,bold=true,center=true)
        listen(c.character,"listen-dictionary");wordExamples(c)
        label("部首 ${c.radical} · ${c.strokes} 画",22)
        label("Unicode 英文释义：\n${c.meaningEn}",20)
        label("请家长用孩子理解的语言解释，带孩子读词语或造句。词义需结合上下文；一字可能有多音。",18)
        label("来源：Unicode 17.0.0 · Unihan\nkMandarin / kDefinition / kRSUnicode / kTotalStrokes",16)
        label("词语与词语拼音：CC-CEDICT · MDBG 与社区贡献者\n词典子集按 CC BY-SA 4.0 提供；完整署名随安装包保存。",16)
        if(index<all.lastIndex) button("下一个字","next-word") { dictionary(index+1) }
        if(index>0) button("上一个字","prev-word") { dictionary(index-1) }
        button("返回家长概览","dictionary-back") { parentHome() }
    }
}
