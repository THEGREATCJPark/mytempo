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
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private Config cfg;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,body;
    private TextView tempo,mark,status,tracker,meterButton,patternButton,play,themeIndicator,scaleDisplay;
    private BeatView beats;
    private int theme=0;
    private final int BG=Color.rgb(13,20,24), CARD=Color.rgb(25,35,41), TEXT=Color.rgb(236,242,243), MUTED=Color.rgb(144,162,171);
    private final int[] colors={0xFFA6EF67,0xFF72C9FF,0xFFFFBC77,0xFFFF8DB2,0xFFB8A0FF};
    private final String[] scaleNames={"메이저 (아이오니안)","도리안", "프리지안", "리디안", "믹솔리디안", "내추럴 마이너 (에올리안)", "로크리안", "멜로딕 마이너", "도리안 ♭2", "리디안 ♯5", "리디안 도미넌트", "믹솔리디안 ♭6", "로크리안 ♮2", "얼터드", "하모닉 마이너", "프리지안 도미넌트", "메이저 펜타토닉", "마이너 펜타토닉", "블루스", "홀톤", "디미니시드 (반음–온음)", "디미니시드 (온음–반음)", "비밥 도미넌트"};
    private final String[] scaleFormula={"1, 2, 3, 4, 5, 6, 7", "1, 2, ♭3, 4, 5, 6, ♭7", "1, ♭2, ♭3, 4, 5, ♭6, ♭7", "1, 2, 3, ♯4, 5, 6, 7", "1, 2, 3, 4, 5, 6, ♭7", "1, 2, ♭3, 4, 5, ♭6, ♭7", "1, ♭2, ♭3, 4, ♭5, ♭6, ♭7", "1, 2, ♭3, 4, 5, 6, 7", "1, ♭2, ♭3, 4, 5, 6, ♭7", "1, 2, 3, ♯4, ♯5, 6, 7", "1, 2, 3, ♯4, 5, 6, ♭7", "1, 2, 3, 4, 5, ♭6, ♭7", "1, 2, ♭3, 4, ♭5, ♭6, ♭7", "1, ♭2, ♭3, ♭4, ♭5, ♭6, ♭7", "1, 2, ♭3, 4, 5, ♭6, 7", "1, ♭2, 3, 4, 5, ♭6, ♭7", "1, 2, 3, 5, 6", "1, ♭3, 4, 5, ♭7", "1, ♭3, 4, ♭5, 5, ♭7", "1, 2, 3, ♯4, ♯5, ♭7", "1, ♭2, ♭3, 3, ♯4, 5, 6, ♭7", "1, 2, ♭3, 4, ♭5, ♭6, 6, 7", "1, 2, 3, 4, 5, 6, ♭7, 7"};
    private int scaleIndex=7;
    private final String[] simple={"♩  기본 박","♫  8분음표","3  셋잇단음표","4  16분음표","♪  점음표","↝  스윙"};
    private final String[] compound={"♩.  기본 박","3  8분음표","↝  첫째·셋째 음"};
    private final ArrayList<Long> taps=new ArrayList<>();
    private final int[] keys={KeyEvent.KEYCODE_SPACE,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN};
    private int captureKey=-1, lastBeat=-2;
    private boolean lastBeatPlaying=false;
    private AlertDialog keyDialog;
    private boolean resumed=false,keepScreen=true,wasPlaying=false;
    private String lastError="";
    private final Runnable pulse=new Runnable(){public void run(){if(resumed){refresh();handler.postDelayed(this,40);}}};


    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        android.content.SharedPreferences p=getSharedPreferences("pulse",0);
        try{cfg=Config.fromJson(new JSONObject(p.getString("config","{}")));}catch(Exception e){cfg=new Config();}
        scaleIndex=Config.clamp(p.getInt("scaleIndex",7),0,scaleNames.length-1);theme=Config.clamp(p.getInt("theme",0),0,4);keepScreen=p.getBoolean("screen",true);
        for(int i=0;i<keys.length;i++)keys[i]=p.getInt("key"+i,keys[i]);
        disableAutomation();
        build();
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int accent(){return colors[theme];}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView button(String s,Runnable r){TextView t=text(s,15,TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(12),dp(8),dp(12),dp(8));t.setBackground(bg(CARD,14));t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->r.run());return t;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void addWeighted(LinearLayout row,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(v,lp);}
    private void gap(LinearLayout into,int h){Space s=new Space(this);into.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private void section(LinearLayout into,String label){TextView t=text(label,12,MUTED);t.setLetterSpacing(.09f);t.setPadding(dp(4),dp(16),0,dp(8));into.addView(t);}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        if(keepScreen)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(20));scroll.addView(body);
        LinearLayout head=row();LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);
        TextView logo=text("PULSE",24,TEXT);logo.setLetterSpacing(.20f);logo.setTypeface(null,1);brand.addView(logo);
        themeIndicator=text("METRONOME  /  YOUR PRACTICE",9,accent());themeIndicator.setLetterSpacing(.12f);brand.addView(themeIndicator);
        head.addView(brand,new LinearLayout.LayoutParams(0,-2,1));head.addView(button("설정",()->settings()));body.addView(head);gap(body,17);
        tempo=text(Integer.toString(cfg.bpm),86,TEXT);tempo.setTypeface(Typeface.create("sans-serif-light",0));tempo.setGravity(Gravity.CENTER);tempo.setIncludeFontPadding(false);tempo.setContentDescription("BPM. 눌러 직접 입력");tempo.setOnClickListener(v->editTempo());body.addView(tempo,new LinearLayout.LayoutParams(-1,dp(104)));
        mark=text("",14,accent());mark.setGravity(Gravity.CENTER);mark.setLetterSpacing(.08f);mark.setMinHeight(dp(30));mark.setOnClickListener(v->tempoMarks());body.addView(mark);
        LinearLayout adjust=row();TextView minus=button("−",()->changeTempo(-1));minus.setTextSize(28);repeat(minus,-1);addWeighted(adjust,minus);
        TextView tap=button("TAP TEMPO",()->tap());tap.setLetterSpacing(.1f);tap.setTextSize(13);addWeighted(adjust,tap);
        TextView plus=button("+",()->changeTempo(1));plus.setTextSize(25);repeat(plus,1);addWeighted(adjust,plus);body.addView(adjust);gap(body,12);
        LinearLayout timing=row();meterButton=button("",()->chooseMeter());patternButton=button("",()->choosePattern());addWeighted(timing,meterButton);addWeighted(timing,patternButton);body.addView(timing);
        lastBeat=-2;beats=new BeatView();body.addView(beats,new LinearLayout.LayoutParams(-1,dp(cfg.beats()>7?104:70)));
        TextView legend=text("박을 눌러  강세 → 보통 → 무음",11,MUTED);legend.setGravity(Gravity.CENTER);body.addView(legend);gap(body,16);
        play=button("▶   시작",()->toggle());play.setTextSize(21);play.setTypeface(null,1);play.setMinHeight(dp(64));play.setBackground(bg(accent(),20));play.setTextColor(BG);body.addView(play);
        LinearLayout stats=row();status=text("READY",11,accent());status.setLetterSpacing(.12f);tracker=text("0 마디  ·  00:00",12,MUTED);tracker.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);stats.addView(status,new LinearLayout.LayoutParams(0,dp(38),1));stats.addView(tracker,new LinearLayout.LayoutParams(0,dp(38),2));body.addView(stats);
        section(body,"JAZZ SCALE  ·  숫자 음정");scaleDisplay=button("",()->chooseScale());scaleDisplay.setTextSize(16);scaleDisplay.setMinHeight(dp(76));body.addView(scaleDisplay);updateScale();gap(body,12);body.addView(button("AVAILABLE TENSION  ·  코드별 보기",()->showTensions()));refresh();
    }
    private void updateScale(){scaleDisplay.setText(scaleNames[scaleIndex]+"  ▾\n"+scaleFormula[scaleIndex]);}
    // Ordered by practical study progression; stored index remains compatible with v1.1.
    private final int[] scaleOrder={0,1,4,5,16,17,18,7,3,14,13,10,12,6,22,20,19,2,15,8,9,11,21};
    private void chooseScale(){String[] sorted=new String[scaleOrder.length];int selected=0;for(int i=0;i<scaleOrder.length;i++){sorted[i]=(i+1)+".  "+scaleNames[scaleOrder[i]];if(scaleOrder[i]==scaleIndex)selected=i;}
        new AlertDialog.Builder(this).setTitle("스케일 · 추천 학습 순서").setSingleChoiceItems(sorted,selected,(d,w)->{scaleIndex=scaleOrder[w];getSharedPreferences("pulse",0).edit().putInt("scaleIndex",scaleIndex).apply();updateScale();d.dismiss();}).setNegativeButton("닫기",null).show();}
    private void showTensions(){
        String[] names={"Ⅰmaj7 · 토닉 메이저", "Ⅱm7 · 도리안", "Ⅲm7 · 프리지안", "Ⅳmaj7 · 리디안", "Ⅴ7 · 믹솔리디안", "Ⅵm7 · 에올리안", "Ⅶm7♭5 · 로크리안", "m7 · 도리안", "m(maj7) · 멜로딕 마이너", "7♯11 · 리디안 도미넌트", "7alt · 얼터드", "m7♭5 · 로크리안 ♮2", "7sus4 · 믹솔리디안"};
        String[] detail={"9, 13\n11은 3과 반음 관계이므로 보이싱에서 보통 피합니다.", "9, 11, 13", "11\n♭9와 ♭13은 안정적인 보이싱에서 주의합니다.", "9, ♯11, 13", "9, 13\n11은 3과 충돌하므로 주의합니다.", "9, 11\n♭13은 5와 반음 관계라 보통 피합니다.", "11, ♭13\n♭9는 1과 반음 관계라 주의합니다.", "9, 11, 13\n도리안에서의 기준입니다. 에올리안은 13 대신 ♭13이므로 주의합니다.", "9, 11, 13", "9, ♯11, 13", "♭9, ♯9, ♯11(♭5), ♭13(♯5)\n같은 도수의 대체 텐션을 모두 동시에 넣는다는 뜻은 아닙니다.", "9, 11, ♭13\n멜로딕 마이너의 6번째 모드 기준입니다.", "9, 13\n4(11)는 3을 대신하는 코드톤입니다."};
        new AlertDialog.Builder(this).setTitle("어베일러블 텐션 · 코드 선택").setItems(names,(d,w)->new AlertDialog.Builder(this).setTitle(names[w]).setMessage(detail[w]+"\n\n텐션 선택은 조성, 코드 기능, 멜로디에 따라 달라집니다. ♭/♯는 코드의 근음을 기준으로 합니다.").setPositiveButton("닫기",null).show()).setNegativeButton("닫기",null).show();
    }
    private void disableAutomation(){cfg.stopMode=0;cfg.stopAfter=0;cfg.autoMode=0;cfg.coachMute=0;cfg.countIn=0;}
    private void label(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private void refresh(){
        MetroService.State s=MetroService.state;
        if(wasPlaying&&!s.playing&&s.bpm>=10){cfg.bpm=s.bpm;persist();}wasPlaying=s.playing;
        int bpm=s.playing?s.bpm:cfg.bpm;
        if(bpm<10)bpm=cfg.bpm;
        label(tempo,""+bpm);label(mark,"BPM  ·  "+Config.tempoName(bpm).toUpperCase(Locale.US));
        label(meterButton,"박자  "+cfg.meter());label(patternButton,(cfg.compound()?compound:simple)[cfg.pattern]);
        label(play,s.playing?"■   정지":"▶   시작");label(status,s.playing?(s.countingIn?"COUNT IN":s.muted?"SILENT BARS":"PLAYING"):"READY");
        long sec=s.elapsedMs/1000;label(tracker,s.bars+" 마디  ·  "+String.format(Locale.US,"%02d:%02d",sec/60,sec%60));
        if(lastBeat!=s.beat||lastBeatPlaying!=s.playing){beats.invalidate();lastBeat=s.beat;lastBeatPlaying=s.playing;}
        if(s.error!=null&&!s.error.isEmpty()&&!s.error.equals(lastError)){lastError=s.error;toast(s.error);}
    }
    private void persist(){cfg.sanitize();getSharedPreferences("pulse",0).edit().putString("config",cfg.toJson().toString()).putInt("theme",theme).putBoolean("screen",keepScreen).apply();}
    private void apply(boolean explicitTempo){if(!explicitTempo&&MetroService.state.playing&&MetroService.state.bpm>=10)cfg.bpm=MetroService.state.bpm;persist();if(MetroService.instance!=null&&MetroService.state.playing){Intent i=new Intent(this,MetroService.class).setAction(MetroService.ACTION_UPDATE).putExtra("config",cfg.toJson().toString()).putExtra("explicitTempo",explicitTempo);startService(i);}beats.invalidate();refresh();}
    private void setTempo(int bpm){cfg.bpm=Config.clamp(bpm,10,400);apply(true);}
    private void changeTempo(int d){setTempo(cfg.bpm+d);}
    private void toggle(){
        if(MetroService.state.playing){cfg.bpm=MetroService.state.bpm;persist();startService(new Intent(this,MetroService.class).setAction(MetroService.ACTION_STOP));}
        else{lastError="";if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);persist();startForegroundService(new Intent(this,MetroService.class).setAction(MetroService.ACTION_START).putExtra("config",cfg.toJson().toString()));}
        handler.postDelayed(()->refresh(),100);
    }
    private void repeat(TextView v,int delta){final Runnable[] repeater=new Runnable[1];repeater[0]=new Runnable(){public void run(){changeTempo(delta*5);handler.postDelayed(this,85);}};v.setOnLongClickListener(w->{handler.post(repeater[0]);return true;});v.setOnTouchListener((w,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)handler.removeCallbacks(repeater[0]);return false;});}
    private void tap(){long now=SystemClock.elapsedRealtime();if(!taps.isEmpty()&&now-taps.get(taps.size()-1)>8000)taps.clear();taps.add(now);while(taps.size()>7)taps.remove(0);if(taps.size()>1){ArrayList<Long> ds=new ArrayList<>();for(int i=1;i<taps.size();i++)ds.add(taps.get(i)-taps.get(i-1));Collections.sort(ds);long interval=ds.get(ds.size()/2);if(interval>0)setTempo((int)Math.round(60000.0/interval));}else toast("박자에 맞춰 계속 눌러주세요");}
    private EditText input(String value,boolean signed){EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setSingleLine(true);e.setSelectAllOnFocus(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|(signed?InputType.TYPE_NUMBER_FLAG_SIGNED:0));return e;}
    private void editTempo(){EditText e=input(""+(MetroService.state.playing?MetroService.state.bpm:cfg.bpm),false);new AlertDialog.Builder(this).setTitle("템포 · 10–400 BPM").setView(e).setNegativeButton("취소",null).setPositiveButton("적용",(d,w)->{try{int x=Integer.parseInt(e.getText().toString());setTempo(x);}catch(Exception ex){toast("숫자를 입력해주세요");}}).show();}
    private void tempoMarks(){int[] values={40,42,44,46,48,50,52,54,56,58,60,63,66,69,72,76,80,84,88,92,96,100,104,108,112,116,120,126,132,138,144,152,160,168,176,184,192,200,208};String[] labels=new String[values.length];for(int i=0;i<values.length;i++)labels[i]=values[i]+"  ·  "+Config.tempoName(values[i]);new AlertDialog.Builder(this).setTitle("클래식 메트로놈 눈금").setItems(labels,(d,w)->setTempo(values[w])).setNegativeButton("닫기",null).show();}
    private void chooseMeter(){ArrayList<String> names=new ArrayList<>();for(int d:new int[]{2,4})for(int n=1;n<=13;n++)names.add(n+"/"+d);for(int n:new int[]{3,6,9,12})names.add(n+"/8");new AlertDialog.Builder(this).setTitle("박자 · 30종").setSingleChoiceItems(names.toArray(new String[0]),names.indexOf(cfg.meter()),(d,w)->{String[] a=names.get(w).split("/");cfg.numerator=Integer.parseInt(a[0]);cfg.denominator=Integer.parseInt(a[1]);cfg.pattern=0;Arrays.fill(cfg.accents,1);cfg.accents[0]=2;apply(false);d.dismiss();build();}).setNegativeButton("닫기",null).show();}
    private void choosePattern(){new AlertDialog.Builder(this).setTitle(cfg.compound()?"복합박 리듬 · BPM은 점4분음표 기준":"리듬 패턴").setSingleChoiceItems(cfg.compound()?compound:simple,cfg.pattern,(d,w)->{cfg.pattern=w;apply(false);d.dismiss();}).setNegativeButton("닫기",null).show();}
    private Spinner spinner(String[] labels,int value){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);s.setAdapter(a);s.setSelection(value);s.setMinimumHeight(dp(48));return s;}
    private void field(LinearLayout panel,String name,View control){TextView t=text(name,13,MUTED);t.setPadding(dp(4),dp(12),0,dp(2));panel.addView(t);panel.addView(control,new LinearLayout.LayoutParams(-1,-2));}
    private void settings(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(20),dp(4),dp(20),dp(24));ScrollView sc=new ScrollView(this);sc.addView(panel);
        Spinner sound=spinner(Config.SOUND_NAMES,cfg.sound);field(panel,"클릭 사운드 · 12종",sound);sound.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(cfg.sound!=pos){cfg.sound=pos;apply(false);}}});
        TextView vol=text("앱 음량  "+cfg.volume+"%",13,MUTED);vol.setPadding(0,dp(18),0,dp(4));panel.addView(vol);SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(cfg.volume);panel.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user){cfg.volume=p;vol.setText("앱 음량  "+p+"%");apply(false);}}});
        Spinner themes=spinner(new String[]{"라임","아이스 블루","앰버","로즈","라벤더"},theme);field(panel,"컬러 테마",themes);themes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(theme!=pos){theme=pos;persist();build();}}});
        Switch screen=new Switch(this);screen.setText("앱이 열려 있을 때 화면 유지");screen.setTextColor(TEXT);screen.setTextSize(13);screen.setMinHeight(dp(56));screen.setChecked(keepScreen);screen.setOnCheckedChangeListener((v,checked)->{keepScreen=checked;persist();if(checked)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});panel.addView(screen);
        panel.addView(button("블루투스 페달 / 키보드",()->keySettings()));gap(panel,8);
        TextView note=text("Pulse Metronome 1.2\n오프라인 · 광고 없음 · 계정 불필요\n\n복합박(3/8, 6/8, 9/8, 12/8)은 점4분음표 1박 기준입니다. 블루투스 오디오에는 기기 자체의 지연이 있을 수 있습니다.",12,MUTED);note.setLineSpacing(dp(3),1);note.setPadding(0,dp(20),0,0);panel.addView(note);
        new AlertDialog.Builder(this).setTitle("설정").setView(sc).setPositiveButton("닫기",null).show();
    }
    private void keySettings(){String[] names={"재생 / 정지","BPM +1","BPM −1"};int[] indices={0,3,4};String[] labels=new String[4];for(int i=0;i<3;i++)labels[i]=names[i]+"  ·  "+KeyEvent.keyCodeToString(keys[indices[i]]).replace("KEYCODE_","");labels[3]="기본 키로 초기화";new AlertDialog.Builder(this).setTitle("페달 키 매핑").setItems(labels,(d,w)->{if(w==3){int[] defaults={62,-1,-1,19,20};System.arraycopy(defaults,0,keys,0,5);saveKeys();return;}captureKey=indices[w];keyDialog=new AlertDialog.Builder(this).setTitle(names[w]).setMessage("연결한 페달 또는 키보드에서 사용할 키를 누르세요.\n기본: Space 재생, ↑/↓ BPM").setNegativeButton("취소",(x,y)->captureKey=-1).create();keyDialog.setOnKeyListener((dialog,key,event)->capture(key,event));keyDialog.setOnDismissListener(x->captureKey=-1);keyDialog.show();}).setNegativeButton("닫기",null).show();}
    private boolean capture(int code,KeyEvent event){if(captureKey<0||code==KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){for(int i=0;i<keys.length;i++)if(i!=captureKey&&keys[i]==code)keys[i]=-1;keys[captureKey]=code;saveKeys();captureKey=-1;if(keyDialog!=null)keyDialog.dismiss();toast("키를 저장했습니다");}return true;}
    private void saveKeys(){android.content.SharedPreferences.Editor e=getSharedPreferences("pulse",0).edit();for(int i=0;i<5;i++)e.putInt("key"+i,keys[i]);e.apply();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(captureKey>=0&&capture(e.getKeyCode(),e))return true;if(getCurrentFocus() instanceof EditText)return super.dispatchKeyEvent(e);int code=e.getKeyCode();for(int i=0;i<keys.length;i++)if(i!=1&&i!=2&&keys[i]==code){if(e.getAction()==KeyEvent.ACTION_DOWN&&(e.getRepeatCount()==0||i>=3)){switch(i){case 0:toggle();break;case 3:changeTempo(1);break;case 4:changeTempo(-1);break;}}return true;}return super.dispatchKeyEvent(e);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void reloadSaved(){try{cfg=Config.fromJson(new JSONObject(getSharedPreferences("pulse",0).getString("config","{}")));}catch(Exception ignored){}disableAutomation();}
    @Override protected void onResume(){super.onResume();reloadSaved();build();resumed=true;handler.post(pulse);}
    @Override protected void onPause(){super.onPause();resumed=false;handler.removeCallbacksAndMessages(null);if(MetroService.state.playing)cfg.bpm=MetroService.state.bpm;persist();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    private class BeatView extends View {
        private final Paint p=new Paint(3);
        BeatView(){super(MainActivity.this);setContentDescription("박별 강세 설정. 탭하면 강세, 보통, 무음 순으로 변경됩니다.");setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private int columns(){return Math.min(cfg.beats(),7);}
        private float cx(int index){int cols=columns();int rows=(cfg.beats()+cols-1)/cols;int row=index/cols;int count=Math.min(cols,cfg.beats()-row*cols);return getWidth()*.5f+(index%cols-(count-1)*.5f)*(getWidth()/Math.max(4,cols));}
        private float cy(int index){return dp(35)+(index/columns())*dp(48);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);MetroService.State s=MetroService.state;for(int i=0;i<cfg.beats();i++){boolean active=s.playing&&s.beat==i;float x=cx(i),y=cy(i);int a=cfg.accents[i];p.setStyle(Paint.Style.FILL);p.setColor(active?accent():a==2?0xFF405731:CARD);if(active)p.setShadowLayer(dp(12),0,0,accent());else p.clearShadowLayer();canvas.drawCircle(x,y,dp(18),p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(a==2?2:1));p.setColor(a==2?accent():MUTED);canvas.drawCircle(x,y,dp(18),p);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(dp(14));p.setTextAlign(Paint.Align.CENTER);p.setColor(active?BG:a==0?MUTED:TEXT);canvas.drawText(a==0?"–":""+(i+1),x,y+dp(5),p);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN)return true;if(e.getAction()==MotionEvent.ACTION_UP){for(int i=0;i<cfg.beats();i++){if(Math.abs(e.getX()-cx(i))<dp(24)&&Math.abs(e.getY()-cy(i))<dp(24)){cfg.accents[i]=(cfg.accents[i]+2)%3;apply(false);performClick();break;}}return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
