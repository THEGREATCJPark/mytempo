package com.cj.pulse;
import android.media.AudioTrack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
public class AudioEngineTest {
 static int checks=0;static AudioEngine.Snapshot finalState;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static AudioTrack render(Config c)throws Exception{
  CountDownLatch done=new CountDownLatch(1);String[] err={""};
  AudioEngine e=new AudioEngine(c,new AudioEngine.Listener(){public void onState(AudioEngine.Snapshot s){finalState=s;}public void onStopped(String error){err[0]=error;done.countDown();}});
  e.start();check(done.await(15,TimeUnit.SECONDS),"render timed out");check(err[0].isEmpty(),"engine error "+err[0]);return AudioTrack.last;
 }
 static AudioTrack renderUpdated(Config c,int atFrame,Config replacement,boolean explicit)throws Exception{
  CountDownLatch done=new CountDownLatch(1);String[] err={""};
  AudioEngine e=new AudioEngine(c,new AudioEngine.Listener(){public void onState(AudioEngine.Snapshot s){finalState=s;}public void onStopped(String error){err[0]=error;done.countDown();}});
  AudioTrack.onWrite=track->{if(track.size>=atFrame){AudioTrack.onWrite=null;e.update(replacement,explicit);}};
  e.start();check(done.await(15,TimeUnit.SECONDS),"updated render timeout");check(err[0].isEmpty(),"updated engine error");AudioTrack.onWrite=null;return AudioTrack.last;
 }
 static int peak(AudioTrack t,int start,int end){int max=0;for(int i=Math.max(0,start);i<Math.min(t.size,end);i++)max=Math.max(max,Math.abs(t.captured[i]));return max;}
 public static void main(String[] args)throws Exception{
  for(int bpm:new int[]{10,60,120,137,400}){
   Config c=new Config();c.bpm=bpm;AudioTrack t=render(c);
   check(t.size==Math.round(8*48000*60.0/bpm),"bar stop length "+bpm+" got "+t.size);
   for(int b=0;b<8;b++){int p=(int)Math.round(b*48000*60.0/bpm);check(peak(t,p,p+200)>1000,"missing click bpm="+bpm+" beat="+b);}
   check(finalState.bars==2,"final completed bars");
  }
  for(int pat=0;pat<6;pat++){
   Config c=new Config();c.pattern=pat;c.bpm=137;AudioTrack t=render(c);double period=48000*60.0/137;
   for(int b=0;b<8;b++)for(double offset:Rhythm.offsets(false,pat)){int p=(int)Math.round((b+offset)*period);check(peak(t,p,p+200)>600,"subdivision pattern "+pat);}
  }
  Config c=new Config();c.coachSound=1;c.coachMute=1;c.stopAfter=4;AudioTrack t=render(c);
  check(peak(t,0,96000)>0&&peak(t,96000,192000)==0&&peak(t,192000,288000)>0&&peak(t,288000,384000)==0,"coach alternating bar silence");
  c=new Config();c.accents[1]=0;t=render(c);check(peak(t,24000,48000)==0,"muted beat silent");
  c=new Config();c.countIn=1;c.stopAfter=1;t=render(c);check(t.size==192000,"countin excludes practice stop");check(finalState.elapsedMs==2000,"countin excluded from elapsed");
  c=new Config();c.autoMode=1;c.autoEvery=1;c.autoStep=30;c.autoTarget=180;c.stopAfter=3;t=render(c);
  check(t.size==236800,"bar ramp sample duration got "+t.size);check(finalState.bpm==180,"ramp target reached");
  c=new Config();c.autoMode=1;c.autoEvery=1;c.autoStep=-30;c.autoTarget=60;c.stopAfter=3;t=render(c);
  check(t.size==416000,"decreasing ramp duration got "+t.size);
  c=new Config();c.autoMode=2;c.autoEvery=1;c.autoStep=60;c.autoTarget=180;c.stopMode=2;c.stopAfter=3;t=render(c);
  check(t.size==144000,"second stop exact");check(finalState.bpm==180,"second ramp");
  for(int meter:new int[]{3,6,9,12}){c=new Config();c.numerator=meter;c.denominator=8;t=render(c);check(t.size==Math.round(2*(meter/3)*24000),"compound meter "+meter);}
  c=new Config();c.volume=0;t=render(c);check(peak(t,0,t.size)==0,"zero volume");
  c=new Config();Config replacement=c.copy();replacement.bpm=240;t=renderUpdated(c,24000,replacement,true);
  check(t.size==108000,"live manual tempo preserves beat phase: "+t.size);check(finalState.bpm==240,"live manual bpm state");
  c=new Config();c.autoMode=2;c.autoEvery=1;c.autoStep=60;c.autoTarget=180;c.stopMode=2;c.stopAfter=3;
  replacement=c.copy();replacement.bpm=125;replacement.volume=50;t=renderUpdated(c,72000,replacement,false);
  check(finalState.bpm==180,"non-tempo edit must preserve auto-ramp bpm");check(t.size==144000,"live volume update preserves timer");
  c=new Config();c.autoMode=2;c.autoEvery=1;c.autoStep=40;c.autoTarget=160;c.stopMode=2;c.stopAfter=2;
  replacement=c.copy();replacement.bpm=120;t=renderUpdated(c,72000,replacement,true);
  check(finalState.bpm==120,"explicit return to starting BPM must override ramp");check(t.size==96000,"return-to-start BPM preserves stop timer");
  long[] fingerprints=new long[12];
  for(int sound=0;sound<12;sound++){c=new Config();c.sound=sound;c.stopAfter=1;t=render(c);check(peak(t,0,2000)>1000,"audible sound "+sound);long h=0;for(int i=0;i<2000;i++)h=h*31+t.captured[i];fingerprints[sound]=h;}
  for(int a=0;a<12;a++)for(int b=a+1;b<12;b++)check(fingerprints[a]!=fingerprints[b],"distinct synthesized sound "+a+"/"+b);
  double p=Rhythm.framesPerBeat(48000,137),clock=0;
  for(int b=0;b<137*60;b++){check(Math.abs(Math.round(clock)-b*p)<=.501,"one hour fractional clock drift");clock+=p;}
  check(Rhythm.ramp(395,10,400)==400&&Rhythm.ramp(12,-10,10)==10,"ramp clipping");
  System.out.println("PASS "+checks+" assertions: real engine PCM timing, meters, subdivisions, coach, accents, count-in, ramps, stops, 12 sounds; one-hour clock arithmetic.");
 }
}
