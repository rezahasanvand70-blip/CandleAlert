package com.example.candlealert

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class JournalActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("prefs", 0) }
    private val dark get() = prefs.getString("theme_mode", "light") == "dark"
    private val bg get() = if (dark) Color.rgb(18,22,28) else Color.rgb(247,249,252)
    private val card get() = if (dark) Color.rgb(28,34,42) else Color.WHITE
    private val soft get() = if (dark) Color.rgb(36,43,53) else Color.rgb(242,246,250)
    private val textColor get() = if (dark) Color.rgb(241,245,249) else Color.rgb(24,32,43)
    private val muted get() = if (dark) Color.rgb(166,177,190) else Color.rgb(105,116,130)
    private val line get() = if (dark) Color.rgb(55,64,76) else Color.rgb(224,229,236)
    private val accent = Color.rgb(22,119,255)
    private val green = Color.rgb(25,171,111)
    private val red = Color.rgb(220,75,88)

    data class Trade(
        val id: Long, var symbol: String, var side: String, var entryTime: Long,
        var entry: String, var sl: String, var tp: String, var volume: String,
        var exitTime: Long?, var exit: String, var pnl: String, var exitReason: String, var notes: String
    )

    override fun onCreate(state: Bundle?) { super.onCreate(state); showJournal() }

    private fun text(s:String,size:Float,color:Int=textColor)=TextView(this).apply{ text=s;textSize=size;setTextColor(color);includeFontPadding=false }
    private fun panel()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,14,16,14);background=android.graphics.drawable.GradientDrawable().apply{setColor(card);cornerRadius=18f;setStroke(1,line)};elevation=1f}
    private fun button(s:String,primary:Boolean=false)=TextView(this).apply{text=s;textSize=13f;gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;setPadding(14,10,14,10);minimumHeight=44;setTextColor(if(primary)Color.WHITE else textColor);background=android.graphics.drawable.GradientDrawable().apply{setColor(if(primary)accent else soft);cornerRadius=22f}}

    private fun load():MutableList<Trade>{
        val out=mutableListOf<Trade>()
        try{
            val a=org.json.JSONArray(prefs.getString("journal_trades_v2","[]")?:"[]")
            for(i in 0 until a.length()){val o=a.getJSONObject(i);out.add(Trade(
                o.optLong("id"),o.optString("symbol","XAUUSD"),o.optString("side","BUY"),o.optLong("entryTime",System.currentTimeMillis()),
                o.optString("entry"),o.optString("sl"),o.optString("tp"),o.optString("volume"),
                if(o.isNull("exitTime"))null else o.optLong("exitTime"),o.optString("exit"),o.optString("pnl"),o.optString("exitReason"),o.optString("notes")
            ))}
        }catch(_:Exception){}
        return out.sortedByDescending{it.entryTime}.toMutableList()
    }

    private fun save(list:List<Trade>){
        val a=org.json.JSONArray()
        list.forEach{t->a.put(org.json.JSONObject().apply{
            put("id",t.id);put("symbol",t.symbol);put("side",t.side);put("entryTime",t.entryTime);put("entry",t.entry);put("sl",t.sl);put("tp",t.tp);put("volume",t.volume)
            if(t.exitTime==null)put("exitTime",org.json.JSONObject.NULL)else put("exitTime",t.exitTime)
            put("exit",t.exit);put("pnl",t.pnl);put("exitReason",t.exitReason);put("notes",t.notes)
        })}
        prefs.edit().putString("journal_trades_v2",a.toString()).apply()
    }

    private fun fmt(ms:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date(ms))

    private fun showJournal(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);setPadding(18,18,18,8)}
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        titles.addView(text("Journal",28f));titles.addView(text("Track every trade from entry to exit",13f,muted).apply{setPadding(0,4,0,0)})
        header.addView(titles,LinearLayout.LayoutParams(0,64).apply{weight=1f})
        header.addView(button("+ Add Trade",true).apply{setOnClickListener{addTrade()}})
        root.addView(header)
        val scroll=ScrollView(this);val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,10,0,16)}
        val trades=load();val open=trades.filter{it.exitTime==null};val closed=trades.filter{it.exitTime!=null}
        if(open.isNotEmpty()){content.addView(text("OPEN TRADES",11f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(2,8,0,6)});open.forEach{addTradeCard(content,it)}}
        if(closed.isNotEmpty()){content.addView(text("CLOSED TRADES",11f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(2,18,0,6)});closed.forEach{addTradeCard(content,it)}}
        if(trades.isEmpty())content.addView(panel().apply{gravity=Gravity.CENTER;addView(text("No trades yet",18f).apply{gravity=Gravity.CENTER});addView(text("Tap + Add Trade to record your first setup.",13f,muted).apply{gravity=Gravity.CENTER;setPadding(0,8,0,0)})},LinearLayout.LayoutParams(-1,150).apply{setMargins(0,20,0,0)})
        scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0).apply{weight=1f})
        root.addView(LinearLayout(this).apply{gravity=Gravity.CENTER;addView(button("← Home").apply{setOnClickListener{finish()}})},LinearLayout.LayoutParams(-1,58))
        setContentView(root)
    }

    private fun addTradeCard(parent:LinearLayout,t:Trade){
        val isOpen=t.exitTime==null
        val c=panel().apply{setBackgroundColor(if(isOpen)(if(dark)Color.rgb(31,42,48)else Color.rgb(244,249,253))else card)}
        val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        title.addView(text(t.symbol+"  •  "+t.side,18f).apply{typeface=Typeface.DEFAULT_BOLD})
        title.addView(text("Entry "+fmt(t.entryTime),12f,muted).apply{setPadding(0,4,0,0)})
        top.addView(title,LinearLayout.LayoutParams(0,55).apply{weight=1f})
        top.addView(text(if(isOpen)"OPEN"else"CLOSED",11f,if(isOpen)accent else muted).apply{typeface=Typeface.DEFAULT_BOLD})
        c.addView(top)
        c.addView(text("Entry "+t.entry.ifBlank{"—"}+"   SL "+t.sl.ifBlank{"—"}+"   TP "+t.tp.ifBlank{"—"},12f,muted).apply{setPadding(0,8,0,0)})
        if(!isOpen)c.addView(text("Exit "+t.exit.ifBlank{"—"}+"   •   "+t.pnl.ifBlank{"P/L not entered"},13f,if((t.pnl.toDoubleOrNull()?:0.0)>=0)green else red).apply{setPadding(0,5,0,0)})
        val rr=calculateRR(t);if(isOpen&&rr!=null)c.addView(text("R:R  1 : "+String.format(Locale.US,"%.2f",rr),12f,accent).apply{setPadding(0,5,0,0)})
        val actions=LinearLayout(this).apply{gravity=Gravity.END;setPadding(0,10,0,0)}
        actions.addView(button("Edit").apply{setOnClickListener{editTrade(t)}})
        if(isOpen)actions.addView(button("Close").apply{setOnClickListener{closeTrade(t)}})
        actions.addView(button("Delete").apply{setOnClickListener{deleteTrade(t)}})
        c.addView(actions);parent.addView(c,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,6)})
    }

    private fun calculateRR(t:Trade):Double?{val e=t.entry.toDoubleOrNull()?:return null;val s=t.sl.toDoubleOrNull()?:return null;val p=t.tp.toDoubleOrNull()?:return null;val risk=abs(e-s);if(risk==0.0)return null;return abs(p-e)/risk}

    private fun ask(title:String,hint:String,initial:String="",optional:Boolean=true,onDone:(String)->Unit){
        val e=EditText(this).apply{setText(initial);this.hint=hint;textSize=18f}
        val b=AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel",null)
        if(optional)b.setNeutralButton("Skip"){_,_->onDone("")}
        b.setPositiveButton("Next"){_,_->onDone(e.text.toString().trim())}.show()
    }

    private fun addTrade(){stepSymbol(Trade(System.currentTimeMillis(),"XAUUSD","BUY",System.currentTimeMillis(),"","","","","", "", "", "", ""))}
    private fun stepSymbol(t:Trade){ask("1 / 8  •  Symbol","e.g. XAUUSD",t.symbol,false){v->t.symbol=v.uppercase(Locale.getDefault());stepSide(t)}}
    private fun stepSide(t:Trade){AlertDialog.Builder(this).setTitle("2 / 8  •  Direction").setItems(arrayOf("BUY","SELL")){_,i->t.side=if(i==0)"BUY"else"SELL";stepEntryTime(t)}.setNegativeButton("Cancel",null).show()}
    private fun stepEntryTime(t:Trade){pickDateTime("3 / 8  •  Entry Time",t.entryTime){t.entryTime=it;stepEntry(t)}}
    private fun stepEntry(t:Trade){ask("4 / 8  •  Entry Price","Entry price","",false){v->if(v.toDoubleOrNull()==null){toast("Enter a valid price");stepEntry(t)}else{t.entry=v;stepSL(t)}}}
    private fun stepSL(t:Trade){ask("5 / 8  •  Stop Loss","Optional — leave empty if you did not use SL","",true){v->t.sl=v;stepTP(t)}}

    private fun stepTP(t:Trade){
        val e=t.entry.toDoubleOrNull();val s=t.sl.toDoubleOrNull()
        if(e!=null&&s!=null&&e!=s){
            val suggested=if(t.side=="BUY")e+2*abs(e-s)else e-2*abs(e-s)
            val input=EditText(this).apply{setText(String.format(Locale.US,"%.5f",suggested));selectAll();hint="Optional TP";textSize=18f}
            AlertDialog.Builder(this).setTitle("6 / 8  •  Take Profit")
                .setMessage("Suggested TP for RR 2:1: "+String.format(Locale.US,"%.5f",suggested)+"\nEdit it if you want another target, or skip.")
                .setView(input).setNegativeButton("Cancel",null).setNeutralButton("Skip"){_,_->t.tp="";stepVolume(t)}
                .setPositiveButton("Next"){_,_->t.tp=input.text.toString().trim();stepVolume(t)}.show()
        }else ask("6 / 8  •  Take Profit","Optional — leave empty if no TP","",true){v->t.tp=v;stepVolume(t)}
    }

    private fun stepVolume(t:Trade){ask("7 / 8  •  Position Size","Optional — e.g. 0.10 lot","",true){v->t.volume=v;stepNotes(t)}}
    private fun stepNotes(t:Trade){ask("8 / 8  •  Notes","Optional setup / reason / review","",true){v->t.notes=v;confirmNew(t)}}

    private fun confirmNew(t:Trade){
        val rr=calculateRR(t);var msg=t.symbol+"  "+t.side+"\nEntry: "+t.entry+"\nSL: "+t.sl.ifBlank{"—"}+"\nTP: "+t.tp.ifBlank{"—"}
        if(rr!=null)msg+="\nRR: 1 : "+String.format(Locale.US,"%.2f",rr)
        msg+="\n\nSave as OPEN trade?"
        AlertDialog.Builder(this).setTitle("Review Trade").setMessage(msg).setNegativeButton("Back",null).setPositiveButton("Save"){_,_->val l=load();l.add(t);save(l);showJournal()}.show()
    }

    private fun closeTrade(t:Trade){stepExitPrice(t)}
    private fun stepExitPrice(t:Trade){ask("1 / 5  •  Exit Price","Exit price",t.exit,false){v->if(v.toDoubleOrNull()==null){toast("Enter a valid price");stepExitPrice(t)}else{t.exit=v;stepExitTime(t)}}}
    private fun stepExitTime(t:Trade){pickDateTime("2 / 5  •  Exit Time",System.currentTimeMillis()){t.exitTime=it;stepPnl(t)}}
    private fun stepPnl(t:Trade){
        val e=t.entry.toDoubleOrNull();val x=t.exit.toDoubleOrNull();val vol=t.volume.toDoubleOrNull()?:1.0
        val calculated=if(e!=null&&x!=null){(if(t.side=="BUY")x-e else e-x)*vol}else null
        ask("3 / 5  •  Profit / Loss",calculated?.let{"Suggested P/L: "+String.format(Locale.US,"%.5f",it)}?:"Enter P/L","",true){v->t.pnl=v.ifBlank{calculated?.toString()?:""};stepExitReason(t)}
    }
    private fun stepExitReason(t:Trade){val reasons=arrayOf("Take Profit","Stop Loss","Manual Close","Signal Reversal","Session End","Other");AlertDialog.Builder(this).setTitle("4 / 5  •  Exit Reason").setItems(reasons){_,i->t.exitReason=reasons[i];stepCloseNotes(t)}.setNeutralButton("Skip"){_,_->t.exitReason="";stepCloseNotes(t)}.setNegativeButton("Cancel",null).show()}
    private fun stepCloseNotes(t:Trade){ask("5 / 5  •  Closing Notes","Optional — what went well / what to improve","",true){v->t.notes=if(v.isBlank())t.notes else if(t.notes.isBlank())v else t.notes+"\n"+v;confirmClose(t)}}
    private fun confirmClose(t:Trade){val rr=calculateRR(t);var msg=t.symbol+"  "+t.side+"\nEntry: "+t.entry+"\nExit: "+t.exit+"\nP/L: "+t.pnl.ifBlank{"—"}+"\nExit time: "+(t.exitTime?.let{fmt(it)}?:"—");if(rr!=null)msg+="\nR:R planned: 1 : "+String.format(Locale.US,"%.2f",rr);AlertDialog.Builder(this).setTitle("Close Trade").setMessage(msg).setNegativeButton("Back",null).setPositiveButton("Save & Close"){_,_->val l=load();val i=l.indexOfFirst{it.id==t.id};if(i>=0)l[i]=t;save(l);showJournal()}.show()}

    private fun editTrade(t:Trade){stepEditSymbol(t)}
    private fun stepEditSymbol(t:Trade){ask("Edit • Symbol","e.g. XAUUSD",t.symbol,false){v->t.symbol=v.uppercase(Locale.getDefault());stepEditSide(t)}}
    private fun stepEditSide(t:Trade){AlertDialog.Builder(this).setTitle("Edit • Direction").setItems(arrayOf("BUY","SELL")){_,i->t.side=if(i==0)"BUY"else"SELL";stepEditEntry(t)}.setNegativeButton("Cancel",null).show()}
    private fun stepEditEntry(t:Trade){ask("Edit • Entry Price","Entry price",t.entry,false){v->if(v.toDoubleOrNull()==null)toast("Invalid price")else{t.entry=v;stepEditSL(t)}}}
    private fun stepEditSL(t:Trade){ask("Edit • Stop Loss","Optional",t.sl,true){v->t.sl=v;stepEditTP(t)}}
    private fun stepEditTP(t:Trade){
        val e=t.entry.toDoubleOrNull();val s=t.sl.toDoubleOrNull()
        if(e!=null&&s!=null&&e!=s){val suggested=if(t.side=="BUY")e+2*abs(e-s)else e-2*abs(e-s);val input=EditText(this).apply{setText(t.tp.ifBlank{String.format(Locale.US,"%.5f",suggested)});textSize=18f};AlertDialog.Builder(this).setTitle("Edit • Take Profit").setMessage("Suggested RR 2:1 TP: "+String.format(Locale.US,"%.5f",suggested)).setView(input).setNegativeButton("Cancel",null).setNeutralButton("Skip"){_,_->t.tp="";stepEditVolume(t)}.setPositiveButton("Save TP"){_,_->t.tp=input.text.toString().trim();stepEditVolume(t)}.show();return}
        ask("Edit • Take Profit","Optional",t.tp,true){v->t.tp=v;stepEditVolume(t)}
    }
    private fun stepEditVolume(t:Trade){ask("Edit • Position Size","Optional",t.volume,true){v->t.volume=v;stepEditNotes(t)}}
    private fun stepEditNotes(t:Trade){ask("Edit • Notes","Optional",t.notes,true){v->t.notes=v;val l=load();val i=l.indexOfFirst{it.id==t.id};if(i>=0){l[i]=t;save(l)};showJournal()}}
    private fun deleteTrade(t:Trade){AlertDialog.Builder(this).setTitle("Delete trade?").setMessage(t.symbol+" "+t.side+"\nThis cannot be undone.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->save(load().filterNot{it.id==t.id});showJournal()}.show()}

    private fun pickDateTime(title:String,initial:Long,onDone:(Long)->Unit){
        val d=Calendar.getInstance().apply{timeInMillis=initial}
        DatePickerDialog(this,{_,y,m,day->TimePickerDialog(this,{_,h,min->val c=Calendar.getInstance().apply{set(y,m,day,h,min,0);set(Calendar.MILLISECOND,0)};onDone(c.timeInMillis)},d.get(Calendar.HOUR_OF_DAY),d.get(Calendar.MINUTE),true).show()},d.get(Calendar.YEAR),d.get(Calendar.MONTH),d.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
}
