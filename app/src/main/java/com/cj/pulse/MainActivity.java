package com.cj.pulse;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import org.json.JSONObject;
import java.util.*;

/** One screen, monochrome. Only what a player touches while practicing. */
public class MainActivity extends Activity {
    private Config cfg;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private TextView tempo,meterButton,patternButton,play;
    private BeatView beats;
    private static final int BG=Color.BLACK, TEXT=Color.WHITE, MUTED=Color.rgb(120,120,120), LINE=Color.rgb(48,48,48), DIM=Color.rgb(34,34,34);
    private final String[] simple={"Quarter","Eighths","Triplets","Sixteenths","Dotted","Swing"};
    private final String[] compound={"Dotted quarter","Eighths","Swing"};
    private final ArrayList<Long> taps=new ArrayList<>();
    private final int[] keys={KeyEvent.KEYCODE_SPACE,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN};
    private int captureKey=-1, lastBeat=-2;
    private boolean lastBeatPlaying=false, lastPlaying=false;
    private AlertDialog keyDialog;
    private boolean resumed=false,keepScreen=true,wasPlaying=false;
    private String lastError="";
    private final Runnable pulse=new Runnable(){public void run(){if(resumed){refresh();handler.postDelayed(this,40);}}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        SharedPreferences p=getSharedPreferences("pulse",0);
        try{cfg=Config.fromJson(new JSONObject(p.getString("config","{}")));}catch(Exception e){cfg=new Config();}
        keepScreen=p.getBoolean("screen",true);
        for(int i=0;i<keys.length;i++)keys[i]=p.getInt("key"+i,keys[i]);
        disableAutomation();
        build();
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable outline(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);if(stroke!=0)d.setStroke(dp(1),stroke);d.setCornerRadius(dp(999));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);return t;}
    private TextView button(String s,Runnable r){TextView t=text(s,15,TEXT);t.setMinHeight(dp(52));t.setPadding(dp(12),0,dp(12),0);t.setBackground(outline(BG,LINE));t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->r.run());return t;}
    private TextView quiet(String s,Runnable r){TextView t=text(s,15,MUTED);t.setMinHeight(dp(48));t.setPadding(dp(12),0,dp(12),0);t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->r.run());return t;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void addWeighted(LinearLayout row,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);lp.setMargins(dp(5),0,dp(5),0);row.addView(v,lp);}
    private void gap(LinearLayout into,int h){Space s=new Space(this);into.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        if(keepScreen)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(8),dp(24),dp(28));scroll.addView(body);
        LinearLayout head=row();TextView name=text("mytempo",15,MUTED);name.setGravity(Gravity.CENTER_VERTICAL);head.addView(name,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView more=quiet("•••",()->settings());more.setContentDescription("Settings");head.addView(more);body.addView(head);
        Space top=new Space(this);body.addView(top,new LinearLayout.LayoutParams(1,0,1));
        tempo=text(Integer.toString(cfg.bpm),112,TEXT);tempo.setTypeface(Typeface.create("sans-serif-thin",0));tempo.setContentDescription("Tempo. Tap to type.");tempo.setOnClickListener(v->editTempo());body.addView(tempo,new LinearLayout.LayoutParams(-1,dp(136)));
        TextView unit=text("BPM",12,MUTED);unit.setLetterSpacing(.3f);body.addView(unit);gap(body,36);
        LinearLayout adjust=row();TextView minus=button("−",()->changeTempo(-1));minus.setTextSize(26);minus.setContentDescription("Slower");repeat(minus,-1);addWeighted(adjust,minus);
        TextView tap=button("Tap",()->tap());addWeighted(adjust,tap);
        TextView plus=button("+",()->changeTempo(1));plus.setTextSize(26);plus.setContentDescription("Faster");repeat(plus,1);addWeighted(adjust,plus);body.addView(adjust);gap(body,36);
        lastBeat=-2;beats=new BeatView();body.addView(beats,new LinearLayout.LayoutParams(-1,dp(cfg.beats()>7?96:52)));gap(body,12);
        LinearLayout timing=row();meterButton=quiet("",()->chooseMeter());patternButton=quiet("",()->choosePattern());addWeighted(timing,meterButton);addWeighted(timing,patternButton);body.addView(timing);
        Space bottom=new Space(this);body.addView(bottom,new LinearLayout.LayoutParams(1,0,1));gap(body,24);
        play=text("",17,BG);play.setLetterSpacing(.12f);play.setMinHeight(dp(64));play.setClickable(true);play.setFocusable(true);play.setOnClickListener(v->toggle());body.addView(play,new LinearLayout.LayoutParams(-1,-2));
        lastPlaying=!MetroService.state.playing;refresh();
    }
    private void disableAutomation(){cfg.stopMode=0;cfg.stopAfter=0;cfg.autoMode=0;cfg.coachMute=0;cfg.countIn=0;}
    private void label(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private void refresh(){
        MetroService.State s=MetroService.state;
        if(wasPlaying&&!s.playing&&s.bpm>=10){cfg.bpm=s.bpm;persist();}wasPlaying=s.playing;
        int bpm=s.playing?s.bpm:cfg.bpm;
        if(bpm<10)bpm=cfg.bpm;
        label(tempo,""+bpm);
        label(meterButton,cfg.meter());label(patternButton,(cfg.compound()?compound:simple)[cfg.pattern]);
        if(lastPlaying!=s.playing){lastPlaying=s.playing;play.setText(s.playing?"STOP":"START");play.setTextColor(s.playing?TEXT:BG);play.setBackground(s.playing?outline(BG,TEXT):outline(TEXT,0));}
        if(lastBeat!=s.beat||lastBeatPlaying!=s.playing){beats.invalidate();lastBeat=s.beat;lastBeatPlaying=s.playing;}
        if(s.error!=null&&!s.error.isEmpty()&&!s.error.equals(lastError)){lastError=s.error;toast(s.error);}
    }
    private void persist(){cfg.sanitize();getSharedPreferences("pulse",0).edit().putString("config",cfg.toJson().toString()).putBoolean("screen",keepScreen).apply();}
    private void apply(boolean explicitTempo){if(!explicitTempo&&MetroService.state.playing&&MetroService.state.bpm>=10)cfg.bpm=MetroService.state.bpm;persist();if(MetroService.instance!=null&&MetroService.state.playing){Intent i=new Intent(this,MetroService.class).setAction(MetroService.ACTION_UPDATE).putExtra("config",cfg.toJson().toString()).putExtra("explicitTempo",explicitTempo);startService(i);}beats.invalidate();refresh();}
    private void setTempo(int bpm){cfg.bpm=Config.clamp(bpm,10,400);apply(true);}
    private void changeTempo(int d){setTempo(cfg.bpm+d);}
    private void toggle(){
        if(MetroService.state.playing){cfg.bpm=MetroService.state.bpm;persist();startService(new Intent(this,MetroService.class).setAction(MetroService.ACTION_STOP));}
        else{lastError="";if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);persist();startForegroundService(new Intent(this,MetroService.class).setAction(MetroService.ACTION_START).putExtra("config",cfg.toJson().toString()));}
        handler.postDelayed(()->refresh(),100);
    }
    private void repeat(TextView v,int delta){final Runnable[] repeater=new Runnable[1];repeater[0]=new Runnable(){public void run(){changeTempo(delta*5);handler.postDelayed(this,85);}};v.setOnLongClickListener(w->{handler.post(repeater[0]);return true;});v.setOnTouchListener((w,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)handler.removeCallbacks(repeater[0]);return false;});}
    private void tap(){long now=SystemClock.elapsedRealtime();if(!taps.isEmpty()&&now-taps.get(taps.size()-1)>8000)taps.clear();taps.add(now);while(taps.size()>7)taps.remove(0);if(taps.size()>1){ArrayList<Long> ds=new ArrayList<>();for(int i=1;i<taps.size();i++)ds.add(taps.get(i)-taps.get(i-1));Collections.sort(ds);long interval=ds.get(ds.size()/2);if(interval>0)setTempo((int)Math.round(60000.0/interval));}}
    private EditText input(String value){EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setTextSize(28);e.setGravity(Gravity.CENTER);e.setSingleLine(true);e.setSelectAllOnFocus(true);e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private void editTempo(){EditText e=input(""+(MetroService.state.playing?MetroService.state.bpm:cfg.bpm));FrameLayout f=new FrameLayout(this);f.setPadding(dp(24),dp(8),dp(24),0);f.addView(e);AlertDialog d=new AlertDialog.Builder(this).setTitle("BPM  10–400").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Set",(x,w)->{try{setTempo(Integer.parseInt(e.getText().toString()));}catch(Exception ignored){}}).create();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);d.show();}
    private void chooseMeter(){ArrayList<String> names=new ArrayList<>();for(int d:new int[]{4,2})for(int n=1;n<=13;n++)names.add(n+"/"+d);for(int n:new int[]{3,6,9,12})names.add(n+"/8");new AlertDialog.Builder(this).setTitle("Time signature").setSingleChoiceItems(names.toArray(new String[0]),names.indexOf(cfg.meter()),(d,w)->{String[] a=names.get(w).split("/");cfg.numerator=Integer.parseInt(a[0]);cfg.denominator=Integer.parseInt(a[1]);cfg.pattern=0;Arrays.fill(cfg.accents,1);cfg.accents[0]=2;apply(false);d.dismiss();build();}).show();}
    private void choosePattern(){new AlertDialog.Builder(this).setTitle("Subdivision").setSingleChoiceItems(cfg.compound()?compound:simple,cfg.pattern,(d,w)->{cfg.pattern=w;apply(false);d.dismiss();}).show();}
    private void settings(){
        String[] items={"Sound  ·  "+Config.SOUND_NAMES[cfg.sound],"Volume  ·  "+cfg.volume+"%","Keep screen on  ·  "+(keepScreen?"On":"Off"),"Pedal / keyboard"};
        new AlertDialog.Builder(this).setTitle("Settings").setItems(items,(d,w)->{switch(w){case 0:chooseSound();break;case 1:chooseVolume();break;case 2:keepScreen=!keepScreen;persist();build();settings();break;default:keySettings();}}).setNegativeButton("Close",null).show();
    }
    private void chooseSound(){new AlertDialog.Builder(this).setTitle("Sound").setSingleChoiceItems(Config.SOUND_NAMES,cfg.sound,(d,w)->{cfg.sound=w;apply(false);}).setPositiveButton("Done",(d,w)->settings()).show();}
    private void chooseVolume(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(20),dp(16),dp(20),dp(8));
        TextView value=text(cfg.volume+"%",28,TEXT);value.setTypeface(Typeface.create("sans-serif-light",0));panel.addView(value);gap(panel,16);
        SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(cfg.volume);panel.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user){cfg.volume=p;value.setText(p+"%");apply(false);}}});
        new AlertDialog.Builder(this).setTitle("Volume").setView(panel).setPositiveButton("Done",(d,w)->settings()).show();
    }
    private void keySettings(){String[] names={"Start / stop","BPM +1","BPM −1"};int[] indices={0,3,4};String[] labels=new String[4];for(int i=0;i<3;i++)labels[i]=names[i]+"  ·  "+(keys[indices[i]]<0?"none":KeyEvent.keyCodeToString(keys[indices[i]]).replace("KEYCODE_",""));labels[3]="Reset to defaults";new AlertDialog.Builder(this).setTitle("Pedal / keyboard").setItems(labels,(d,w)->{if(w==3){int[] defaults={62,-1,-1,19,20};System.arraycopy(defaults,0,keys,0,5);saveKeys();return;}captureKey=indices[w];keyDialog=new AlertDialog.Builder(this).setTitle(names[w]).setMessage("Press a key on your pedal or keyboard.").setNegativeButton("Cancel",(x,y)->captureKey=-1).create();keyDialog.setOnKeyListener((dialog,key,event)->capture(key,event));keyDialog.setOnDismissListener(x->captureKey=-1);keyDialog.show();}).setNegativeButton("Close",null).show();}
    private boolean capture(int code,KeyEvent event){if(captureKey<0||code==KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){for(int i=0;i<keys.length;i++)if(i!=captureKey&&keys[i]==code)keys[i]=-1;keys[captureKey]=code;saveKeys();captureKey=-1;if(keyDialog!=null)keyDialog.dismiss();toast("Saved");}return true;}
    private void saveKeys(){SharedPreferences.Editor e=getSharedPreferences("pulse",0).edit();for(int i=0;i<5;i++)e.putInt("key"+i,keys[i]);e.apply();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(captureKey>=0&&capture(e.getKeyCode(),e))return true;if(getCurrentFocus() instanceof EditText)return super.dispatchKeyEvent(e);int code=e.getKeyCode();for(int i=0;i<keys.length;i++)if(i!=1&&i!=2&&keys[i]==code){if(e.getAction()==KeyEvent.ACTION_DOWN&&(e.getRepeatCount()==0||i>=3)){switch(i){case 0:toggle();break;case 3:changeTempo(1);break;case 4:changeTempo(-1);break;}}return true;}return super.dispatchKeyEvent(e);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void reloadSaved(){try{cfg=Config.fromJson(new JSONObject(getSharedPreferences("pulse",0).getString("config","{}")));}catch(Exception ignored){}disableAutomation();}
    @Override protected void onResume(){super.onResume();reloadSaved();build();resumed=true;handler.post(pulse);}
    @Override protected void onPause(){super.onPause();resumed=false;handler.removeCallbacksAndMessages(null);if(MetroService.state.playing)cfg.bpm=MetroService.state.bpm;persist();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    /** Beats as dots. Tap one to cycle accent, normal, silent. */
    private class BeatView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        BeatView(){super(MainActivity.this);setContentDescription("Beats. Tap a beat to cycle accent, normal, silent.");}
        private int columns(){return Math.min(cfg.beats(),7);}
        private float cx(int index){int cols=columns();int row=index/cols;int count=Math.min(cols,cfg.beats()-row*cols);float step=Math.min(dp(44),getWidth()/(float)Math.max(4,cols));return getWidth()*.5f+(index%cols-(count-1)*.5f)*step;}
        private float cy(int index){return dp(26)+(index/columns())*dp(44);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);MetroService.State s=MetroService.state;
            for(int i=0;i<cfg.beats();i++){
                boolean active=s.playing&&s.beat==i;float x=cx(i),y=cy(i);int a=cfg.accents[i];
                if(a==0){p.setStyle(Paint.Style.FILL);p.setColor(active?TEXT:MUTED);canvas.drawRect(x-dp(6),y-dp(1),x+dp(6),y+dp(1),p);continue;}
                float r=dp(a==2?11:7);
                p.setStyle(Paint.Style.FILL);p.setColor(active?TEXT:a==2?DIM:BG);canvas.drawCircle(x,y,r,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(a==2?1.5f:1));p.setColor(active||a==2?TEXT:MUTED);canvas.drawCircle(x,y,r,p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){for(int i=0;i<cfg.beats();i++){if(Math.abs(e.getX()-cx(i))<dp(22)&&Math.abs(e.getY()-cy(i))<dp(22)){cfg.accents[i]=(cfg.accents[i]+2)%3;apply(false);performClick();break;}}}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
