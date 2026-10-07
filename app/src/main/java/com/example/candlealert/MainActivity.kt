package com.example.candlealert

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("prefs", 0) }
    private val bg=Color.rgb(5,18,36);private val card=Color.rgb(9,29,55);private val card2=Color.rgb(13,38,67)
    private val green=Color.rgb(37,223,160);private val red=Color.rgb(255,70,84);private val textColor=Color.WHITE;private val muted=Color.rgb(155,174,198)

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=bg;window.navigationBarColor=bg;showHome();if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),9)}

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
        head.addView(label("🔔",28f,textColor),LinearLayout.LayoutParams(46,46));head.addView(label("Candle",24f,textColor));head.addView(label("Alert",24f,green))
        val gear=label("⚙",25f,textColor).apply{gravity=Gravity.CENTER};head.addView(gear,LinearLayout.LayoutParams(0,52).apply{weight=1f});gear.setOnClickListener{showSettings()};root.addView(head)
        val status=card();val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        row.addView(label("●",25f,if(prefs.getBoolean("enabled",true))green else red));row.addView(label(if(prefs.getBoolean("enabled",true))"  Monitoring" else "  Alerts paused",19f,textColor),LinearLayout.LayoutParams(0,50).apply{weight=1f})
        val sw=Switch(this).apply{isChecked=prefs.getBoolean("enabled",true)};row.addView(sw);status.addView(row);status.addView(label(if(sw.isChecked)"Alerts are active" else "Turn on to receive alerts",13f,muted))
        sw.setOnCheckedChangeListener{_,v->prefs.edit().putBoolean("enabled",v).apply();Scheduler.scheduleNext(this)};root.addView(status,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,16,0,12)})
        val mr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};listOf("Forex","Crypto","Both").forEachIndexed{idx,s->val b=button(s,prefs.getInt("market",0)==idx);b.setOnClickListener{prefs.edit().putInt("market",idx).apply();Scheduler.scheduleNext(this);showHome()};mr.addView(b,LinearLayout.LayoutParams(0,52).apply{weight=1f;setMargins(3,0,3,0)})};root.addView(mr)
        val sessions=card();sessions.addView(label("Active Sessions",16f,textColor));val sr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val active=prefs.getStringSet("sessions",setOf("Sydney","Tokyo","Frankfurt","London","New York"))?:emptySet()
        listOf("Sydney","Tokyo","Frankfurt","London","New York").forEach{session->val b=button(session,active.contains(session));b.textSize=10f;b.setOnClickListener{val n=active.toMutableSet();if(!n.add(session))n.remove(session);prefs.edit().putStringSet("sessions",n).apply();showHome()};sr.addView(b,LinearLayout.LayoutParams(0,44).apply{weight=1f;setMargins(2,0,2,0)})}
        sessions.addView(sr,LinearLayout.LayoutParams(-1,50));root.addView(sessions,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,12,0,12)})
        val next=card();next.addView(label("Next Candle",15f,muted));val tf=prefs.getInt("tf",5);val tfText=if(tf>=60)(tf/60).toString()+"H" else tf.toString()+"M"
        next.addView(label("EURUSD  •  "+tfText,20f,textColor).apply{setPadding(0,8,0,0)});next.addView(label("Candle timing is scheduled automatically",13f,muted));next.addView(label("▂▃▅▄▆▃▇▅▆▇",28f,green).apply{gravity=Gravity.CENTER;setPadding(0,18,0,8)})
        root.addView(next,LinearLayout.LayoutParams(-1,0).apply{weight=1f});addBottom(root,"home");setContentView(root)}

    private fun addBottom(root:LinearLayout,active:String){
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(0,8,0,0)}
        listOf("⌂" to "Home","🔔" to "Alerts","▤" to "Journal","⚙" to "Settings").forEach{(ic,n)->
            val item=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
                background=rounded(if(active==n.lowercase()) card2 else card,18f);setPadding(2,4,2,3)
            }
            item.addView(label(ic,22f,if(active==n.lowercase()) green else textColor),LinearLayout.LayoutParams(-1,28))
            item.addView(label(n,10f,if(active==n.lowercase()) green else muted).apply{gravity=Gravity.CENTER})
            item.setOnClickListener{when(n){"Home"->showHome();"Alerts"->showAlerts();"Journal"->showJournal();"Settings"->showSettings()}}
            nav.addView(item,LinearLayout.LayoutParams(0,62).apply{weight=1f;setMargins(3,0,3,0)})
        }
        root.addView(nav,LinearLayout.LayoutParams(-1,70))
    }

    private fun showAlerts(){val root=base();root.addView(label("Alerts",28f,textColor));root.addView(label("Recent candle notifications",14f,muted));val box=card();val list=prefs.getStringSet("history",emptySet())?.toList()?.sortedDescending()?:emptyList()
        if(list.isEmpty())box.addView(label("No alerts yet.\nYour first notification will appear here.",16f,muted))else list.take(12).forEach{box.addView(label("🔔  "+it,15f,textColor).apply{setPadding(0,8,0,8)})}
        root.addView(box,LinearLayout.LayoutParams(-1,0).apply{weight=1f;setMargins(0,16,0,16)});addBottom(root,"alerts");setContentView(root)}

    private fun showJournal(){val root=base();root.addView(label("Journal",28f,textColor));root.addView(label("Your CandleAlert activity",14f,muted));val b=card();b.addView(label("Trading journal",20f,textColor));b.addView(label("Trade notes and performance tracking can be added here.",14f,muted).apply{setPadding(0,10,0,0)});root.addView(b,LinearLayout.LayoutParams(-1,0).apply{weight=1f;setMargins(0,16,0,16)});addBottom(root,"journal");setContentView(root)}

    private fun showSettings(){val root=base();root.addView(label("Settings",28f,textColor));root.addView(label("Customize your alerts",14f,muted));val options=listOf("Timeframe","Alert Timing","Market & Sessions","Sleep Hours","Exact Alarm Permission")
        options.forEach{name->val c=card();val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};r.addView(label(name,17f,textColor),LinearLayout.LayoutParams(0,52).apply{weight=1f});r.addView(label("›",28f,muted));c.addView(r);c.setOnClickListener{when(name){"Timeframe"->chooseTf();"Alert Timing"->chooseTiming();"Market & Sessions"->chooseMarket();"Sleep Hours"->editQuiet();"Exact Alarm Permission"->if(android.os.Build.VERSION.SDK_INT>=31)startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))}};root.addView(c,LinearLayout.LayoutParams(-1,70).apply{setMargins(0,8,0,0)})}
        root.addView(Space(this),LinearLayout.LayoutParams(1,0).apply{weight=1f});addBottom(root,"settings");setContentView(root)}

    private fun chooseTf(){val vals=arrayOf("1 Minute (M1)","3 Minutes (M3)","5 Minutes (M5)","15 Minutes (M15)","30 Minutes (M30)","1 Hour (H1)","4 Hours (H4)");val nums=listOf(1,3,5,15,30,60,240);AlertDialog.Builder(this).setTitle("Timeframe").setSingleChoiceItems(vals,nums.indexOf(prefs.getInt("tf",5))){d,w->prefs.edit().putInt("tf",nums[w]).apply();d.dismiss();Scheduler.scheduleNext(this);showSettings()}.show()}
    private fun chooseTiming(){val vals=arrayOf("Exactly at candle close","+ 30 seconds","+ 1 minute","+ 2 minutes","- 30 seconds","- 1 minute","- 2 minutes");AlertDialog.Builder(this).setTitle("Alert Timing").setItems(vals){_,w->val mode=if(w==0)1 else if(w<4)2 else 0;val off=when(w){0->0;1,4->30;2,5->60;3,6->120;else->120};prefs.edit().putInt("mode",mode).putInt("offset",off).apply();Scheduler.scheduleNext(this)}.show()}
    private fun chooseMarket(){val vals=arrayOf("Forex","Crypto (24/7)","Forex + Crypto");AlertDialog.Builder(this).setTitle("Market").setSingleChoiceItems(vals,prefs.getInt("market",0)){d,w->prefs.edit().putInt("market",w).apply();d.dismiss();Scheduler.scheduleNext(this)}.show()}
    private fun editQuiet(){val e=EditText(this).apply{setText(prefs.getString("quiet","00:00-07:30"));hint="00:00-07:30"};AlertDialog.Builder(this).setTitle("Sleep Hours").setMessage("No notifications during this period (phone local time).").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("quiet",e.text.toString()).apply();Scheduler.scheduleNext(this)}.setNegativeButton("Cancel",null).show()}
}