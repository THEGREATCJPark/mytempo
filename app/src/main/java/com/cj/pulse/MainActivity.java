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
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.util.Rational;
import android.util.TypedValue;
import org.json.JSONObject;
import java.util.*;

/** One screen, monochrome. Only what a player touches while practicing. */
public class MainActivity extends Activity {
    private Config cfg;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private TextView tempo,meterButton,patternButton,play,scaleLine;
    private int scaleIndex=7;
    private boolean showScale=false, autoPip=false, receiverRegistered=false, started=false;
    private int renderedWidth=-1,renderedHeight=-1,pipBpm=-1;
    private boolean renderedPip=false,pipPlaying=false;
    private FrameLayout surface;
    private TextView pipBeat;
    private ArrayList<RemoteAction> pipActions=new ArrayList<>();
    private final Runnable resize=()->renderWindow();
    private final BroadcastReceiver pipReceiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        String command=intent.getStringExtra("command");
        if("toggle".equals(command))toggle();else if("slower".equals(command))changeTempo(-5);else if("faster".equals(command))changeTempo(5);
    }};
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
    private final Runnable pulse=new Runnable(){public void run(){if(started||isInPictureInPictureMode()){refresh();handler.postDelayed(this,isInPictureInPictureMode()?80:40);}}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        SharedPreferences p=getSharedPreferences("pulse",0);
        try{cfg=Config.fromJson(new JSONObject(p.getString("config","{}")));}catch(Exception e){cfg=new Config();}
        autoPip=p.getBoolean("autoPip",false);keepScreen=p.getBoolean("screen",true);scaleIndex=Config.clamp(p.getInt("scaleIndex",7),0,JazzReference.NAMES.length-1);showScale=p.getBoolean("showScale",false);
        for(int i=0;i<keys.length;i++)keys[i]=p.getInt("key"+i,keys[i]);
        disableAutomation();
        IntentFilter filter=new IntentFilter("com.cj.pulse.PIP_CONTROL");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(pipReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(pipReceiver,filter,"com.cj.pulse.permission.PIP_CONTROL",null);
        receiverRegistered=true;
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
        cancelRepeats();
        if(root==null){
            root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
            surface=new FrameLayout(this);root.addView(surface,new LinearLayout.LayoutParams(-1,-1));
            root.setOnApplyWindowInsetsListener((v,insets)->{boolean pip=isInPictureInPictureMode();v.setPadding(pip?0:insets.getSystemWindowInsetLeft(),pip?0:insets.getSystemWindowInsetTop(),pip?0:insets.getSystemWindowInsetRight(),pip?0:insets.getSystemWindowInsetBottom());return insets;});
            surface.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)scheduleRender();});
            setContentView(root);root.requestApplyInsets();
        }
        if(keepScreen)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderedWidth=-1;scheduleRender();
    }
    private void scheduleRender(){handler.removeCallbacks(resize);handler.post(resize);}
    private void renderWindow(){
        int w=surface.getWidth(),h=surface.getHeight();boolean pip=isInPictureInPictureMode();
        if(w<=0||h<=0||(w==renderedWidth&&h==renderedHeight&&pip==renderedPip))return;
        renderedWidth=w;renderedHeight=h;renderedPip=pip;cancelRepeats();repeaters.clear();
        WindowLayout layout=WindowLayout.forWindow(Math.round(w/getResources().getDisplayMetrics().density),Math.round(h/getResources().getDisplayMetrics().density),pip);
        surface.removeAllViews();tempo=null;meterButton=null;patternButton=null;play=null;scaleLine=null;beats=null;pipBeat=null;
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(layout.padding),dp(layout.pip?4:8),dp(layout.padding),dp(layout.pip?4:12));
        FrameLayout.LayoutParams bodyParams=new FrameLayout.LayoutParams(Math.min(w,dp(layout.maxWidth)),-1,Gravity.CENTER);surface.addView(body,bodyParams);
        if(pip){
            tempo=tempoView(96);body.addView(tempo,new LinearLayout.LayoutParams(-1,0,1));
            pipBeat=text("",14,MUTED);pipBeat.setMinHeight(dp(24));body.addView(pipBeat);
            refresh();return;
        }
        LinearLayout head=row();TextView name=text("mytempo",15,MUTED);name.setGravity(Gravity.CENTER_VERTICAL);head.addView(name,new LinearLayout.LayoutParams(0,dp(44),1));
        if(supportsPip()){TextView mini=quiet("PiP",()->enterMiniWindow());mini.setContentDescription("Open picture in picture");mini.setTooltipText("Floating mini window");head.addView(mini);}
        TextView more=quiet("•••",()->settings());more.setContentDescription("Settings");head.addView(more);body.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout contents=new LinearLayout(this);contents.setOrientation(layout.twoColumns?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);contents.setGravity(Gravity.CENTER);scroll.addView(contents,new ScrollView.LayoutParams(-1,-1));
        LinearLayout number=new LinearLayout(this);number.setOrientation(LinearLayout.VERTICAL);number.setGravity(Gravity.CENTER);
        tempo=tempoView(layout.tempoSp);tempo.setOnClickListener(v->editTempo());number.addView(tempo,new LinearLayout.LayoutParams(-1,dp(layout.tempoHeight)));
        TextView unit=text("BPM",12,MUTED);unit.setLetterSpacing(.3f);if(!layout.compact)number.addView(unit);
        if(layout.twoColumns)contents.addView(number,new LinearLayout.LayoutParams(0,-2,1));else contents.addView(number,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setGravity(Gravity.CENTER);
        if(layout.twoColumns){controls.setPadding(dp(20),0,0,0);contents.addView(controls,new LinearLayout.LayoutParams(0,-2,1));}else{gap(contents,layout.spacing);contents.addView(controls,new LinearLayout.LayoutParams(-1,-2));}
        LinearLayout adjust=row();TextView minus=button("−",()->changeTempo(-1));minus.setTextSize(26);minus.setContentDescription("Slower");repeat(minus,-1);addWeighted(adjust,minus);
        TextView tap=button("Tap",()->tap());tap.setContentDescription("Tap tempo");addWeighted(adjust,tap);
        TextView plus=button("+",()->changeTempo(1));plus.setTextSize(26);plus.setContentDescription("Faster");repeat(plus,1);addWeighted(adjust,plus);if(!layout.compact){controls.addView(adjust);gap(controls,layout.spacing);}
        lastBeat=-2;beats=new BeatView();controls.addView(beats,new LinearLayout.LayoutParams(-1,dp(WindowLayout.beatRows(cfg.beats(),Math.round((layout.twoColumns?w/2f:w)/getResources().getDisplayMetrics().density)-layout.padding*2)*48+4)));
        LinearLayout timing=row();meterButton=quiet("",()->chooseMeter());patternButton=quiet("",()->choosePattern());meterButton.setContentDescription("Time signature");patternButton.setContentDescription("Subdivision");addWeighted(timing,meterButton);addWeighted(timing,patternButton);controls.addView(timing);
        scaleLine=text("",layout.tablet?18:14,MUTED);scaleLine.setPadding(0,dp(8),0,dp(8));scaleLine.setLineSpacing(dp(5),1);scaleLine.setOnClickListener(v->chooseScale());controls.addView(scaleLine);updateScale();
        if(layout.compact){gap(body,4);body.addView(adjust);}
        gap(body,layout.compact?4:16);
        play=text("",17,BG);play.setLetterSpacing(.12f);play.setMinHeight(dp(layout.compact?48:64));play.setClickable(true);play.setFocusable(true);play.setOnClickListener(v->toggle());play.setContentDescription("Start or stop");body.addView(play,new LinearLayout.LayoutParams(-1,-2));
        lastPlaying=!MetroService.state.playing;refresh();
    }
    private TextView tempoView(int maxSp){TextView t=text(""+cfg.bpm,maxSp,TEXT);t.setTypeface(Typeface.create("sans-serif-thin",0));t.setSingleLine(true);t.setAutoSizeTextTypeUniformWithConfiguration(20,maxSp,2,TypedValue.COMPLEX_UNIT_SP);t.setContentDescription("Tempo. Tap to type.");return t;}
    private boolean supportsPip(){return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);}
    private RemoteAction pipAction(int icon,String title,String command,int request){
        Intent intent=new Intent("com.cj.pulse.PIP_CONTROL").setPackage(getPackageName()).putExtra("command",command);
        PendingIntent action=PendingIntent.getBroadcast(this,request,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new RemoteAction(Icon.createWithResource(this,icon),title,title,action);
    }
    private PictureInPictureParams makePipParams(){
        boolean playing=MetroService.state.playing;
        ArrayList<RemoteAction> actions=new ArrayList<>();actions.add(pipAction(android.R.drawable.ic_media_rew,"Slower by 5 BPM","slower",101));actions.add(pipAction(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Stop":"Start","toggle",102));actions.add(pipAction(android.R.drawable.ic_media_ff,"Faster by 5 BPM","faster",103));
        pipActions=actions;
        PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new Rational(3,2)).setActions(actions);
        if(Build.VERSION.SDK_INT>=31)b.setAutoEnterEnabled(autoPip&&playing&&!isInMultiWindowMode()).setSeamlessResizeEnabled(false);
        return b.build();
    }
    private void enterMiniWindow(){
        if(!supportsPip()){toast("Picture in picture is unavailable. Use Samsung pop-up view instead.");return;}
        try{if(!enterPictureInPictureMode(makePipParams()))toast("Enable picture in picture for mytempo in Android settings.");}catch(IllegalStateException|IllegalArgumentException e){toast("Picture in picture is unavailable in this window.");}
    }
    private void updatePipControls(){if(!supportsPip())return;MetroService.State s=MetroService.state;if(s.bpm==pipBpm&&s.playing==pipPlaying)return;pipBpm=s.bpm;pipPlaying=s.playing;try{setPictureInPictureParams(makePipParams());}catch(IllegalStateException|IllegalArgumentException ignored){}}
    private void disableAutomation(){cfg.stopMode=0;cfg.stopAfter=0;cfg.autoMode=0;cfg.coachMute=0;cfg.countIn=0;}
    private void label(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private void refresh(){
        if(tempo==null)return;
        MetroService.State s=MetroService.state;
        if(wasPlaying&&!s.playing&&s.bpm>=10){cfg.bpm=s.bpm;persist();}wasPlaying=s.playing;
        int bpm=s.playing?s.bpm:cfg.bpm;
        if(bpm<10)bpm=cfg.bpm;
        label(tempo,""+bpm);
        updatePipControls();
        if(isInPictureInPictureMode()){if(pipBeat!=null)label(pipBeat,(s.playing?(s.beat+1)+" / "+cfg.beats():"Stopped")+"  ·  "+cfg.meter());return;}
        if(meterButton==null||play==null)return;
        label(meterButton,cfg.meter());label(patternButton,(cfg.compound()?compound:simple)[cfg.pattern]);
        if(lastPlaying!=s.playing){lastPlaying=s.playing;play.setText(s.playing?"STOP":"START");play.setTextColor(s.playing?TEXT:BG);play.setBackground(s.playing?outline(BG,TEXT):outline(TEXT,0));}
        if(lastBeat!=s.beat||lastBeatPlaying!=s.playing){beats.invalidate();lastBeat=s.beat;lastBeatPlaying=s.playing;}
        if(s.error!=null&&!s.error.isEmpty()&&!s.error.equals(lastError)){lastError=s.error;toast(s.error);}
    }
    private void persist(){cfg.sanitize();getSharedPreferences("pulse",0).edit().putString("config",cfg.toJson().toString()).putBoolean("screen",keepScreen).apply();}
    private void apply(boolean explicitTempo){if(!explicitTempo&&MetroService.state.playing&&MetroService.state.bpm>=10)cfg.bpm=MetroService.state.bpm;persist();if(MetroService.instance!=null&&MetroService.state.playing){Intent i=new Intent(this,MetroService.class).setAction(MetroService.ACTION_UPDATE).putExtra("config",cfg.toJson().toString()).putExtra("explicitTempo",explicitTempo);startService(i);}if(beats!=null)beats.invalidate();refresh();}
    private void setTempo(int bpm){cfg.bpm=Config.clamp(bpm,10,400);apply(true);}
    private void changeTempo(int d){setTempo(cfg.bpm+d);}
    private void toggle(){
        if(MetroService.state.playing){cfg.bpm=MetroService.state.bpm;persist();startService(new Intent(this,MetroService.class).setAction(MetroService.ACTION_STOP));}
        else{lastError="";if(!isInPictureInPictureMode()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);persist();startForegroundService(new Intent(this,MetroService.class).setAction(MetroService.ACTION_START).putExtra("config",cfg.toJson().toString()));}
        handler.postDelayed(()->refresh(),100);
    }
    private final ArrayList<Runnable> repeaters=new ArrayList<>();
    private void cancelRepeats(){for(Runnable r:repeaters)handler.removeCallbacks(r);}
    private void repeat(TextView v,int delta){final Runnable[] repeater=new Runnable[1];repeater[0]=new Runnable(){public void run(){changeTempo(delta*5);handler.postDelayed(this,120);}};repeaters.add(repeater[0]);v.setOnLongClickListener(w->{handler.post(repeater[0]);return true;});v.setOnTouchListener((w,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)handler.removeCallbacks(repeater[0]);return false;});}
    private void tap(){long now=SystemClock.elapsedRealtime();if(!taps.isEmpty()&&now-taps.get(taps.size()-1)>8000)taps.clear();taps.add(now);while(taps.size()>7)taps.remove(0);if(taps.size()>1){ArrayList<Long> ds=new ArrayList<>();for(int i=1;i<taps.size();i++)ds.add(taps.get(i)-taps.get(i-1));Collections.sort(ds);long interval=ds.get(ds.size()/2);if(interval>0)setTempo((int)Math.round(60000.0/interval));}}
    private EditText input(String value){EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setTextSize(28);e.setGravity(Gravity.CENTER);e.setSingleLine(true);e.setSelectAllOnFocus(true);e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private void editTempo(){EditText e=input(""+(MetroService.state.playing?MetroService.state.bpm:cfg.bpm));FrameLayout f=new FrameLayout(this);f.setPadding(dp(24),dp(8),dp(24),0);f.addView(e);AlertDialog d=new AlertDialog.Builder(this).setTitle("BPM  10–400").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Set",(x,w)->{try{setTempo(Integer.parseInt(e.getText().toString()));}catch(Exception ignored){}}).create();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);d.show();}
    private void chooseMeter(){ArrayList<String> names=new ArrayList<>();for(int d:new int[]{4,2})for(int n=1;n<=13;n++)names.add(n+"/"+d);for(int n:new int[]{3,6,9,12})names.add(n+"/8");new AlertDialog.Builder(this).setTitle("Time signature").setSingleChoiceItems(names.toArray(new String[0]),names.indexOf(cfg.meter()),(d,w)->{String[] a=names.get(w).split("/");cfg.numerator=Integer.parseInt(a[0]);cfg.denominator=Integer.parseInt(a[1]);cfg.pattern=0;Arrays.fill(cfg.accents,1);cfg.accents[0]=2;apply(false);d.dismiss();build();}).show();}
    private void choosePattern(){new AlertDialog.Builder(this).setTitle("Subdivision").setSingleChoiceItems(cfg.compound()?compound:simple,cfg.pattern,(d,w)->{cfg.pattern=w;apply(false);d.dismiss();}).show();}
    private void settings(){
        String[] items={"Sound  ·  "+Config.SOUND_NAMES[cfg.sound],"Volume  ·  "+cfg.volume+"%","Keep screen on  ·  "+(keepScreen?"On":"Off"),"Pedal / keyboard","Scales","Available tensions","Auto PiP while playing  ·  "+(autoPip?"On":"Off")};
        new AlertDialog.Builder(this).setTitle("Settings").setItems(items,(d,w)->{switch(w){case 0:chooseSound();break;case 1:chooseVolume();break;case 2:keepScreen=!keepScreen;persist();build();settings();break;case 3:keySettings();break;case 4:chooseScale();break;case 5:showTensions();break;default:autoPip=!autoPip;getSharedPreferences("pulse",0).edit().putBoolean("autoPip",autoPip).apply();pipBpm=-1;updatePipControls();}}).setNegativeButton("Close",null).show();
    }
    private void updateScale(){if(scaleLine==null)return;scaleLine.setText(JazzReference.NAMES[scaleIndex]+"\n"+JazzReference.FORMULAS[scaleIndex]);scaleLine.setVisibility(showScale?View.VISIBLE:View.GONE);}
    private void chooseScale(){String[] labels=new String[JazzReference.ORDER.length+1];labels[0]="Hide scale";int selected=0;for(int i=0;i<JazzReference.ORDER.length;i++){int id=JazzReference.ORDER[i];labels[i+1]=(i+1)+". "+JazzReference.NAMES[id];if(showScale&&id==scaleIndex)selected=i+1;}new AlertDialog.Builder(this).setTitle("Scales · suggested study order").setSingleChoiceItems(labels,selected,(d,w)->{showScale=w>0;if(showScale)scaleIndex=JazzReference.ORDER[w-1];getSharedPreferences("pulse",0).edit().putInt("scaleIndex",scaleIndex).putBoolean("showScale",showScale).apply();updateScale();d.dismiss();}).setNegativeButton("Close",null).show();}
    private void showTensions(){new AlertDialog.Builder(this).setTitle("Available tensions").setItems(JazzReference.CHORDS,(d,w)->new AlertDialog.Builder(this).setTitle(JazzReference.CHORDS[w]).setMessage(JazzReference.TENSIONS[w]+"\n\nMajor-key functions or the named chord scale. Choose for the melody and voicing; these are not instructions to play every tension together. Alterations are relative to the chord root.").setPositiveButton("Close",null).show()).setNegativeButton("Close",null).show();}
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
    @Override protected void onStart(){super.onStart();started=true;handler.removeCallbacks(pulse);handler.post(pulse);}
    @Override protected void onResume(){super.onResume();reloadSaved();build();resumed=true;handler.removeCallbacks(pulse);handler.post(pulse);}
    @Override protected void onPause(){super.onPause();resumed=false;cancelRepeats();if(MetroService.state.playing)cfg.bpm=MetroService.state.bpm;persist();}
    @Override protected void onStop(){super.onStop();started=false;if(!isInPictureInPictureMode())handler.removeCallbacks(pulse);}
    @Override protected void onDestroy(){cancelRepeats();handler.removeCallbacksAndMessages(null);if(receiverRegistered){unregisterReceiver(pipReceiver);receiverRegistered=false;}super.onDestroy();}
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(Build.VERSION.SDK_INT<31&&autoPip&&MetroService.state.playing&&!isInMultiWindowMode())enterMiniWindow();}
    @Override public void onPictureInPictureModeChanged(boolean pip,Configuration config){super.onPictureInPictureModeChanged(pip,config);root.requestApplyInsets();build();handler.removeCallbacks(pulse);if(started||pip)handler.post(pulse);}
    @Override public void onMultiWindowModeChanged(boolean multi,Configuration config){super.onMultiWindowModeChanged(multi,config);pipBpm=-1;build();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    /** Beats as dots. Tap one to cycle accent, normal, silent. */
    private class BeatView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        BeatView(){super(MainActivity.this);setContentDescription("Beats. Tap a beat to cycle accent, normal, silent.");}
        private int columns(){return Math.max(1,Math.min(7,Math.min(cfg.beats(),Math.round(getWidth()/getResources().getDisplayMetrics().density)/48)));}
        private float cx(int index){int cols=columns();int row=index/cols;int count=Math.min(cols,cfg.beats()-row*cols);float step=Math.min(dp(48),getWidth()/(float)Math.max(4,cols));return getWidth()*.5f+(index%cols-(count-1)*.5f)*step;}
        private float cy(int index){return dp(26)+(index/columns())*dp(48);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);int desired=dp(WindowLayout.beatRows(cfg.beats(),Math.round(w/getResources().getDisplayMetrics().density))*48+4);if(getLayoutParams()!=null&&getLayoutParams().height!=desired){getLayoutParams().height=desired;requestLayout();}}
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

