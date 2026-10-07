package com.example.candlealert

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("prefs", 0) }
    private val bg=Color.rgb(5,18,36);private val card=Color.rgb(9,29,55);private val card2=Color.rgb(13,38,67)
    private val green=Color.rgb(37,223,160);private val red=Color.rgb(255,70,84);private val textColor=Color.WHITE;private val muted=Color.rgb(155,174,198)
    private val handler=Handler(Looper.getMainLooper())
    private var countdownView:TextView?=null
    private var nextDetailsView:TextView?=null
    private var ticker:Runnable?=null

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=bg;window.navigationBarColor=bg;showHome();Scheduler.scheduleNext(this);if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),9)}
    override fun onDestroy(){ticker?.let{handler.removeCallbacks(it)};super.onDestroy()}

    private fun showSplash(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setBackgroundColor(bg)}
        root.addView(ImageView(this).apply{setImageResource(R.drawable.app_icon)},LinearLayout.LayoutParams(150,150))
        root.addView(label("CandleAlert",34f,textColor).apply{gravity=Gravity.CENTER;setPadding(0,18,0,0)})
        root.addView(label("Never miss the right moment",15f,muted).apply{gravity=Gravity.CENTER;setPadding(0,8,0,0)})
        setContentView(root);root.postDelayed({showHome()},700)}

    private fun base(): LinearLayout {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);setPadding(18,14,18,8)}
        ViewCompat.setOnApplyWindowInsetsListener(root){v,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(18,14+bars.top,18,8+bars.bottom)
            insets
        }
        return root
    }
    private fun label(s:String,size:Float,color:Int)=TextView(this).apply{text=s;textSize=size;setTextColor(color)}
    private fun rounded(c:Int,r:Float=16f)=android.graphics.drawable.GradientDrawable().apply{setColor(c);cornerRadius=r}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=rounded(card2,18f)}
    private fun button(s:String,selected:Boolean=false)=TextView(this).apply{text=s;textSize=14f;setTextColor(if(selected)bg else textColor);gravity=Gravity.CENTER;setPadding(10,8,10,8);background=rounded(if(selected)green else card,28f)}

    private fun showHome(){
        val root=base();val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        head.addView(ImageView(this).apply{setImageResource(android.R.drawable.ic_popup_reminder);setPadding(8,8,8,8)},LinearLayout.LayoutParams(46,46));head.addView(label("Candle",24f,textColor));head.addView(label("Alert",24f,green))
        val gear=label("⚙",25f,textColor).apply{gravity=Gravity.CENTER};head.addView(gear,LinearLayout.LayoutParams(0,52).apply{weight=1f});gear.setOnClickListener{showSettings()};root.addView(head)
        val status=card();val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        row.addView(label("●",25f,if(prefs.getBoolean("enabled",true))green else red));row.addView(label(if(prefs.getBoolean("enabled",true))"  Monitoring" else "  Alerts paused",19f,textColor),LinearLayout.LayoutParams(0,50).apply{weight=1f})
        val sw=Switch(this).apply{isChecked=prefs.getBoolean("enabled",true)};row.addView(sw);status.addView(row);status.addView(label(if(sw.isChecked)"Alerts are active" else "Turn on to receive alerts",13f,muted))
        sw.setOnCheckedChangeListener{_,v->prefs.edit().putBoolean("enabled",v).apply();Scheduler.scheduleNext(this)};root.addView(status,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,16,0,12)})
        val mr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};listOf("Forex","Crypto","Both").forEachIndexed{idx,s->val b=button(s,prefs.getInt("market",0)==idx);b.setOnClickListener{prefs.edit().putInt("market",idx).apply();Scheduler.scheduleNext(this);showHome()};mr.addView(b,LinearLayout.LayoutParams(0,52).apply{weight=1f;setMargins(3,0,3,0)})};root.addView(mr)
        val sessions=card();sessions.addView(label("Active Sessions",16f,textColor));val sr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val active=prefs.getStringSet("sessions",setOf("Sydney","Tokyo","Frankfurt","London","New York"))?:emptySet()
        listOf("Sydney","Tokyo","Frankfurt","London","New York").forEach{session->val b=button(session,active.contains(session));b.textSize=10f;b.setOnClickListener{val n=active.toMutableSet();if(!n.add(session))n.remove(session);prefs.edit().putStringSet("sessions",n).apply();showHome()};sr.addView(b,LinearLayout.LayoutParams(0,44).apply{weight=1f;setMargins(2,0,2,0)})}
        sessions.addView(sr,LinearLayout.LayoutParams(-1,50));root.addView(sessions,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,12,0,12)})
        val next=card();next.addView(label("NEXT ALERT",13f,muted))
        countdownView=label("Calculating…",28f,green).apply{setPadding(0,6,0,0)};next.addView(countdownView)
        nextDetailsView=label("Checking schedule…",13f,muted).apply{setPadding(0,4,0,0)};next.addView(nextDetailsView)
        val tf=prefs.getInt("tf",5);val tfText=if(tf>=60)(tf/60).toString()+"H" else tf.toString()+"M"
        next.addView(label("Timeframe  "+tfText+"   •   "+timingSummary(),13f,textColor).apply{setPadding(0,12,0,0)})
        next.addView(label("▂▃▅▄▆▃▇▅▆▇",24f,green).apply{gravity=Gravity.CENTER;setPadding(0,12,0,2)})
        root.addView(next,LinearLayout.LayoutParams(-1,0).apply{weight=1f;setMargins(0,0,0,8)});addBottom(root,"home");setContentView(root);updateCountdown()}

    private fun updateCountdown(){
        val run=object:Runnable{override fun run(){
            if(isFinishing)return
            val trigger=Scheduler.nextTrigger(this@MainActivity);val now=System.currentTimeMillis()/1000
            if(trigger==null){countdownView?.text=if(!prefs.getBoolean("enabled",true))"PAUSED" else "No alert scheduled";nextDetailsView?.text=Scheduler.nextStatus(this@MainActivity)}
            else{val left=(trigger-now).coerceAtLeast(0);countdownView?.text=formatCountdown(left);nextDetailsView?.text=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date(trigger*1000))+"  •  "+Scheduler.nextStatus(this@MainActivity)}
            handler.postDelayed(this,1000)
        }};ticker=run;handler.post(run)
    }
    private fun formatCountdown(s:Long):String{val h=s/3600;val m=(s%3600)/60;val sec=s%60;return if(h>0)String.format(Locale.getDefault(),"%02d:%02d:%02d",h,m,sec) else String.format(Locale.getDefault(),"%02d:%02d",m,sec)}
    private fun timingSummary():String{val mode=prefs.getInt("mode",0);val off=prefs.getInt("offset",120);if(mode==1||off==0)return "At close";return (if(mode==0)"Before " else "After ")+formatOffset(off)}
    private fun formatOffset(s:Int):String=when(s){10->"10s";30->"30s";45->"45s";60->"1m";120->"2m";180->"3m";300->"5m";else->s.toString()+"s"}

    private fun addBottom(root:LinearLayout,active:String){
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(0,8,0,2)}
        val icons=mapOf("Home" to android.R.drawable.ic_menu_view,"Alerts" to android.R.drawable.ic_popup_reminder,"Journal" to android.R.drawable.ic_menu_edit,"Settings" to android.R.drawable.ic_menu_preferences)
        listOf("Home","Alerts","Journal","Settings").forEach{n->
            val item=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=rounded(if(active==n.lowercase()) card2 else card,18f);setPadding(4,3,4,3)}
            val icon=ImageView(this).apply{setImageResource(icons[n]!!);setColorFilter(if(active==n.lowercase()) green else textColor);setPadding(5,4,5,1)}
            item.addView(icon,LinearLayout.LayoutParams(42,34))
            item.addView(label(n,10f,if(active==n.lowercase()) green else muted).apply{gravity=Gravity.CENTER})
            item.setOnClickListener{when(n){"Home"->showHome();"Alerts"->showAlerts();"Journal"->showJournal();"Settings"->showSettings()}}
            nav.addView(item,LinearLayout.LayoutParams(0,68).apply{weight=1f;setMargins(3,0,3,0)})
        }
        root.addView(nav,LinearLayout.LayoutParams(-1,74))
    }

    private fun showAlerts(){val root=base();root.addView(label("Alerts",28f,textColor));root.addView(label("Recent candle notifications",14f,muted));val box=card();val list=prefs.getStringSet("history",emptySet())?.toList()?.sortedDescending()?:emptyList()
        if(list.isEmpty())box.addView(label("No alerts yet.\nYour first notification will appear here.",16f,muted))else list.take(12).forEach{box.addView(label("🔔  "+it,15f,textColor).apply{setPadding(0,8,0,8)})}
        root.addView(box,LinearLayout.LayoutParams(-1,0).apply{weight=1f;setMargins(0,16,0,16)});addBottom(root,"alerts");setContentView(root)}

    private fun showJournal(){val root=base();root.addView(label("Journal",28f,textColor));root.addView(label("Your CandleAlert activity",14f,muted));val b=card();b.addView(label("Trading journal",20f,textColor));b.addView(label("Trade notes and performance tracking can be added here.",14f,muted).apply{setPadding(0,10,0,0)});root.addView(b,LinearLayout.LayoutParams(-1,0).apply{weight=1f;setMargins(0,16,0,16)});addBottom(root,"journal");setContentView(root)}

    private fun showSettings(){val root=base();root.addView(label("Settings",28f,textColor));root.addView(label("Customize your alerts",14f,muted));val options=listOf("Timeframe","Alert Timing","Market & Sessions","Sleep Hours","Exact Alarm Permission")
        options.forEach{name->val c=card();val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};r.addView(label(name,17f,textColor),LinearLayout.LayoutParams(0,52).apply{weight=1f});r.addView(label("›",28f,muted));c.addView(r);c.setOnClickListener{when(name){"Timeframe"->chooseTf();"Alert Timing"->chooseTiming();"Market & Sessions"->chooseMarket();"Sleep Hours"->editQuiet();"Exact Alarm Permission"->if(android.os.Build.VERSION.SDK_INT>=31)startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = android.net.Uri.parse("package:$packageName") })}};root.addView(c,LinearLayout.LayoutParams(-1,70).apply{setMargins(0,8,0,0)})}
        root.addView(Space(this),LinearLayout.LayoutParams(1,0).apply{weight=1f});addBottom(root,"settings");setContentView(root)}

    private fun chooseTf(){val vals=arrayOf("1 Minute (M1)","3 Minutes (M3)","5 Minutes (M5)","15 Minutes (M15)","30 Minutes (M30)","1 Hour (H1)","4 Hours (H4)");val nums=listOf(1,3,5,15,30,60,240);AlertDialog.Builder(this).setTitle("Timeframe").setSingleChoiceItems(vals,nums.indexOf(prefs.getInt("tf",5))){d,w->prefs.edit().putInt("tf",nums[w]).apply();d.dismiss();Scheduler.scheduleNext(this);showSettings()}.show()}
    private fun chooseTiming(){
        val root=base();root.addView(label("Alert Timing",28f,textColor));root.addView(label("Select the alert position relative to candle close.",14f,muted).apply{setPadding(0,4,0,10)})
        addTimingRow(root,"BEFORE CLOSE",0,listOf(10,30,45,60,120,180,300))
        addTimingRow(root,"AT CLOSE",1,listOf(0))
        addTimingRow(root,"AFTER CLOSE",2,listOf(10,30,45,60,120,180,300))
        val custom=card();val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};r.addView(label("Custom seconds",16f,textColor),LinearLayout.LayoutParams(0,52).apply{weight=1f});r.addView(label("›",26f,muted));custom.addView(r);custom.setOnClickListener{customTiming()}
        root.addView(custom,LinearLayout.LayoutParams(-1,66).apply{setMargins(0,8,0,0)});root.addView(Space(this),LinearLayout.LayoutParams(1,0).apply{weight=1f});addBottom(root,"settings");setContentView(root)
    }
    private fun addTimingRow(root:LinearLayout,title:String,mode:Int,offsets:List<Int>){
        root.addView(label(title,12f,muted).apply{setPadding(4,8,0,4)});val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        offsets.forEach{off->val selected=prefs.getInt("mode",0)==mode&&prefs.getInt("offset",120)==off;val txt=if(mode==1)"AT CLOSE" else if(off<60)off.toString()+"s" else (off/60).toString()+"m";val b=button(txt,selected);b.textSize=10f;b.setOnClickListener{prefs.edit().putInt("mode",mode).putInt("offset",off).apply();Scheduler.scheduleNext(this);showSettings()};row.addView(b,LinearLayout.LayoutParams(0,46).apply{weight=1f;setMargins(2,0,2,0)})};root.addView(row)
    }
    private fun customTiming(){
        val e=EditText(this).apply{inputType=2;setText(prefs.getInt("offset",120).toString());hint="Seconds"}
        AlertDialog.Builder(this).setTitle("Custom seconds").setMessage("Enter seconds, then choose Before or After.").setView(e)
            .setPositiveButton("Before"){_,_->saveCustomTiming(e.text.toString(),0)}
            .setNeutralButton("After"){_,_->saveCustomTiming(e.text.toString(),2)}.setNegativeButton("Cancel",null).show()
    }
    private fun saveCustomTiming(v:String,mode:Int){val sec=v.toIntOrNull()?.coerceIn(1,86400)?:return;prefs.edit().putInt("mode",mode).putInt("offset",sec).apply();Scheduler.scheduleNext(this);showSettings()}
    
private fun chooseMarket(){val vals=arrayOf("Forex","Crypto (24/7)","Forex + Crypto");AlertDialog.Builder(this).setTitle("Market").setSingleChoiceItems(vals,prefs.getInt("market",0)){d,w->prefs.edit().putInt("market",w).apply();d.dismiss();Scheduler.scheduleNext(this)}.show()}
    private fun editQuiet(){val e=EditText(this).apply{setText(prefs.getString("quiet","00:00-07:30"));hint="00:00-07:30"};AlertDialog.Builder(this).setTitle("Sleep Hours").setMessage("No notifications during this period (phone local time).").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("quiet",e.text.toString()).apply();Scheduler.scheduleNext(this)}.setNegativeButton("Cancel",null).show()}
}