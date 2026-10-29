package com.cj.pulse;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

public class WindowSmokeTest extends Instrumentation {
 private Bundle arguments;private MainActivity activity;private String mode;
 @Override public void onCreate(Bundle a){arguments=a;start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   mode=arguments.getString("mode","portrait");
   if(mode.startsWith("popup")){Config c=new Config();c.numerator=13;getTargetContext().getSharedPreferences("pulse",0).edit().putString("config",c.toJson().toString()).commit();}
   Intent intent=new Intent(Intent.ACTION_MAIN).setClassName("com.cj.pulse","com.cj.pulse.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
   waitUntil(()->view("Start or stop")!=null,"footer appears");
   checkFooter();if(mode.startsWith("popup"))checkCompactControls();shot(mode);
   if(mode.equals("portrait")){
    onMain(()->view("Settings").performClick());clickText("Scales");clickText("8. Melodic minor");
    waitUntil(()->textStarts(activity.getWindow().getDecorView(),"Melodic minor")!=null,"selected scale shown");
    onMain(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));SystemClock.sleep(1600);waitForIdleSync();checkFooter();
    check(textStarts(activity.getWindow().getDecorView(),"Melodic minor")!=null,"scale survives rotation");shot("rotated-scale");
   }
   if(mode.equals("pip")){
    check(activity.getPackageManager().hasSystemFeature("android.software.picture_in_picture"),"emulator PiP feature");
    onMain(()->view("Start or stop").performClick());waitUntil(()->MetroService.state.playing,"play starts");
    onMain(()->view("Open picture in picture").performClick());waitUntil(()->activity.isInPictureInPictureMode(),"actual PiP entered");
    int original=MetroService.state.bpm;sendAction(2);waitUntil(()->MetroService.state.bpm==original+5,"PiP +5");
    waitUntil(()->tempoText().equals(""+(original+5)),"PiP BPM updates while Activity paused");
    sendAction(0);waitUntil(()->MetroService.state.bpm==original,"PiP -5");
    shot("pip-playing");sendAction(1);waitUntil(()->!MetroService.state.playing,"PiP stop");sendAction(1);waitUntil(()->MetroService.state.playing,"PiP restart");
    long before=MetroService.state.elapsedMs;SystemClock.sleep(1200);check(MetroService.state.elapsedMs>before,"PiP clock keeps advancing");
    sendAction(1);waitUntil(()->!MetroService.state.playing,"cleanup stop");
   }
   result.putString("stream","PASS "+mode+": pinned footer, window rendering"+(mode.equals("pip")?", actual PiP with live BPM and start/stop actions":"")+"\n");
   onMain(()->activity.finish());finish(Activity.RESULT_OK,result);
  }catch(Throwable e){result.putString("stream","FAIL "+mode+": "+e+"\n"+android.util.Log.getStackTraceString(e));try{shot("failure-"+mode);}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}
 }
 private void onMain(Runnable r){runOnMainSync(r);waitForIdleSync();}
 private void waitUntil(BooleanSupplier condition,String label){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();runOnMainSync(()->ok.set(condition.getAsBoolean()));if(ok.get())return;SystemClock.sleep(120);}throw new AssertionError(label);}
 private View view(String description){return find(activity.getWindow().getDecorView(),description);}
 private View find(View v,String d){if(d.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),d);if(x!=null)return x;}}return null;}
 private TextView textStarts(View v,String prefix){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(prefix))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView x=textStarts(g.getChildAt(i),prefix);if(x!=null)return x;}}return null;}
 private String tempoText(){View v=view("Tempo. Tap to type.");return v==null?"":((TextView)v).getText().toString();}
 private void checkFooter(){onMain(()->{View footer=view("Start or stop");Rect rect=new Rect();check(footer!=null&&footer.getGlobalVisibleRect(rect),"footer visible");float density=activity.getResources().getDisplayMetrics().density;check(rect.height()>=Math.round(47*density),"footer touch target not clipped");Rect screen=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(screen);check(rect.bottom<=screen.bottom,"footer is above navigation/keyboard");});}
 private void checkCompactControls(){onMain(()->{for(String description:new String[]{"Slower","Faster","Tap tempo"}){View v=view(description);Rect r=new Rect();check(v!=null&&v.getGlobalVisibleRect(r)&&r.height()>=Math.round(47*activity.getResources().getDisplayMetrics().density),"compact control pinned and fully visible: "+description);}});}
 private void clickText(String text){long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(text.contentEquals(node.getText()==null?"":node.getText())){AccessibilityNodeInfo n=node;while(n!=null&&!n.isClickable())n=n.getParent();if(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(350);waitForIdleSync();return;}}}SystemClock.sleep(150);}throw new AssertionError("Cannot click "+text);}
 @SuppressWarnings("unchecked") private void sendAction(int index){try{Field f=MainActivity.class.getDeclaredField("pipActions");f.setAccessible(true);AtomicReference<RemoteAction> r=new AtomicReference<>();runOnMainSync(()->{try{r.set(((ArrayList<RemoteAction>)f.get(activity)).get(index));}catch(Exception e){throw new RuntimeException(e);}});r.get().getActionIntent().send();}catch(Exception e){throw new RuntimeException(e);}}
 private void shot(String name)throws IOException{SystemClock.sleep(200);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("screenshot unavailable");File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(FileOutputStream o=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
