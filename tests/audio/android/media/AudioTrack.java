package android.media;
import java.util.Arrays;
public class AudioTrack {
 public static final int MODE_STREAM=1,PERFORMANCE_MODE_LOW_LATENCY=1,STATE_INITIALIZED=1,WRITE_BLOCKING=1;
 public static volatile AudioTrack last;
 public static volatile java.util.function.Consumer<AudioTrack> onWrite;
 public short[] captured=new short[1000000];public int size=0;private boolean paused;
 public AudioTrack(){last=this;}
 public static int getMinBufferSize(int r,int c,int e){return 1920;}
 public int getState(){return STATE_INITIALIZED;}public void play(){paused=false;}public void pause(){paused=true;}public void flush(){}public void release(){}
 public int write(short[] p,int offset,int count,int mode){if(paused)return -1;if(size+count>captured.length)captured=Arrays.copyOf(captured,captured.length*2);System.arraycopy(p,offset,captured,size,count);size+=count;if(onWrite!=null)onWrite.accept(this);return count;}
 public int getPlaybackHeadPosition(){return size;}
 public static class Builder {public Builder setAudioAttributes(AudioAttributes a){return this;}public Builder setAudioFormat(AudioFormat a){return this;}public Builder setTransferMode(int m){return this;}public Builder setBufferSizeInBytes(int b){return this;}public Builder setPerformanceMode(int m){return this;}public AudioTrack build(){return new AudioTrack();}}
}
