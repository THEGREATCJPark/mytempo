package com.cj.pulse;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import java.util.ArrayDeque;

/** PCM events are scheduled against a fractional sample clock, never a UI timer. */
public final class AudioEngine {
    public interface Listener {
        void onState(Snapshot snapshot);
        void onStopped(String error);
    }
    public static final class Snapshot {
        public final boolean muted, countingIn;
        public final int beat, bpm;
        public final long bars, elapsedMs;
        public Snapshot(boolean muted, boolean countingIn, int beat, int bpm, long bars, long elapsedMs) {
            this.muted=muted;this.countingIn=countingIn;this.beat=beat;this.bpm=bpm;this.bars=bars;this.elapsedMs=elapsedMs;
        }
    }
    private static final int RATE=48000, BLOCK=240, RING_SIZE=8192;
    private static final class Request {
        final Config config;
        final long tempoRevision;
        Request(Config config,long tempoRevision){this.config=config;this.tempoRevision=tempoRevision;}
    }
    private volatile Request requested;
    private volatile boolean running;
    private volatile AudioTrack track;
    private Thread worker;
    private final Listener listener;
    private final float[][][] sounds = makeSounds();
    public AudioEngine(Config config, Listener listener) { this.requested=new Request(config.copy(),0);this.listener=listener; }
    public synchronized void start() {
        if(running)return;
        running=true;
        worker=new Thread(this::render,"Pulse PCM clock");worker.start();
    }
    public void update(Config config) { update(config,true); }
    public synchronized void update(Config config, boolean explicitTempo) {
        Request previous=requested;
        Config next=config.copy();
        if(!explicitTempo)next.bpm=previous.config.bpm;
        // Publish config and intent atomically: re-entering the original BPM is still an edit.
        requested=new Request(next,previous.tempoRevision+(explicitTempo?1:0));
    }
    public void stop() {
        running=false;
        AudioTrack t=track;
        if(t!=null)try{t.pause();t.flush();}catch(IllegalStateException ignored){}
    }
    private static final class Mark {
        final long frame, practiceStart;
        final Snapshot snapshot;
        Mark(long frame,long practiceStart,Snapshot snapshot){this.frame=frame;this.practiceStart=practiceStart;this.snapshot=snapshot;}
    }
    private void render() {
        String error="";
        AudioTrack output=null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<0)throw new IllegalStateException("오디오 형식을 열 수 없습니다");
            output=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min,1920*2))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("오디오 출력을 초기화할 수 없습니다");
            track=output;
            if(!running)return;
            output.play();
            Request request=requested;
            Config c=request.config;
            long lastTempoRevision=request.tempoRevision;
            int actualBpm=c.bpm, beat=0, subdivision=0;
            int beatCount=c.beats(), sound=c.sound, accent=2;
            long frame=0, bar=-c.countIn, practiceStart=c.countIn==0?0:-1;
            double beatStart=0, duration=Rhythm.framesPerBeat(RATE,actualBpm), nextEvent=0;
            double[] offsets=Rhythm.offsets(c.compound(),c.pattern);
            boolean first=true, muted=false, countIn=bar<0, automaticEnd=false;
            long lastBarRamp=-1, secondRampCount=0;
            int lastAutoMode=c.autoMode, lastAutoEvery=c.autoEvery;
            long secondRampOrigin=practiceStart;
            short[] pcm=new short[BLOCK];float[] mix=new float[RING_SIZE];
            ArrayDeque<Mark> marks=new ArrayDeque<>();
            Snapshot visible=new Snapshot(false,countIn,0,actualBpm,0,0);
            long visiblePracticeStart=practiceStart, lastPublish=-1, played=0, playWrap=0, lastHead=0;
            while(running) {
                request=requested;
                c=request.config;
                // A manual tempo edit preserves progress within the current beat.
                if(request.tempoRevision!=lastTempoRevision) {
                    double ratio=(double)actualBpm/c.bpm;
                    beatStart=frame+(beatStart-frame)*ratio;
                    nextEvent=frame+(nextEvent-frame)*ratio;
                    duration*=ratio;actualBpm=c.bpm;lastTempoRevision=request.tempoRevision;
                    marks.add(new Mark(frame,practiceStart,new Snapshot(muted,countIn,beat,actualBpm,Math.max(0,bar),practiceStart<0?0:Math.max(0,frame-practiceStart)*1000/RATE)));
                }
                if(c.autoMode!=lastAutoMode||c.autoEvery!=lastAutoEvery) {
                    lastAutoMode=c.autoMode;lastAutoEvery=c.autoEvery;
                    secondRampOrigin=practiceStart<0?-1:frame;secondRampCount=0;lastBarRamp=bar;
                }
                int produced=0;
                for(;produced<BLOCK;produced++) {
                    if(!running)break;
                    long practiceFrames=practiceStart<0?0:frame-practiceStart;
                    if(practiceStart>=0&&c.stopMode==2&&c.stopAfter>0&&practiceFrames>=((long)c.stopAfter)*RATE) {
                        if(subdivision==0&&!first&&frame>=Math.round(nextEvent)&&beat+1>=beatCount)bar++;
                        automaticEnd=true;break;
                    }
                    if(practiceStart>=0&&c.autoMode==2) {
                        if(secondRampOrigin<0)secondRampOrigin=practiceStart;
                        long count=(frame-secondRampOrigin)/(((long)c.autoEvery)*RATE);
                        if(count>secondRampCount) {
                            int nextBpm=Rhythm.ramp(actualBpm,c.autoStep,c.autoTarget);
                            double ratio=(double)actualBpm/nextBpm;
                            beatStart=frame+(beatStart-frame)*ratio;nextEvent=frame+(nextEvent-frame)*ratio;duration*=ratio;
                            actualBpm=nextBpm;secondRampCount=count;
                            marks.add(new Mark(frame,practiceStart,new Snapshot(muted,countIn,beat,actualBpm,Math.max(0,bar),practiceFrames*1000/RATE)));
                        }
                    }
                    if(frame>=Math.round(nextEvent)) {
                        if(subdivision==0) {
                            if(!first) {
                                beat++;
                                if(beat>=beatCount) {beat=0;bar++;}
                            }
                            first=false;
                            beatCount=c.beats();
                            if(beat>=beatCount){beat=0;bar++;}
                            if(bar>=0&&practiceStart<0) {practiceStart=frame;secondRampOrigin=frame;secondRampCount=0;}
                            countIn=bar<0;
                            if(!countIn&&beat==0&&c.stopMode==1&&c.stopAfter>0&&bar>=c.stopAfter) {
                                automaticEnd=true;break;
                            }
                            if(!countIn&&beat==0&&c.autoMode==1&&bar>0&&bar!=lastBarRamp&&bar%c.autoEvery==0) {
                                actualBpm=Rhythm.ramp(actualBpm,c.autoStep,c.autoTarget);lastBarRamp=bar;
                            }
                            duration=Rhythm.framesPerBeat(RATE,actualBpm);
                            beatStart=nextEvent;
                            offsets=Rhythm.offsets(c.compound(),c.pattern);
                            muted=!countIn&&Rhythm.mutedBar(bar,c.coachSound,c.coachMute);
                            accent=countIn?(beat==0?2:1):c.accents[beat];sound=c.sound;
                            long ms=practiceStart<0?0:Math.max(0,frame-practiceStart)*1000/RATE;
                            marks.add(new Mark(frame,practiceStart,new Snapshot(muted,countIn,beat,actualBpm,Math.max(0,bar),ms)));
                        }
                        if(!muted&&accent>0) {
                            int voice=subdivision==0?(accent==2?0:1):2;
                            float[] wave=sounds[sound][voice];
                            for(int i=0;i<wave.length;i++)mix[(int)((frame+i)&(RING_SIZE-1))]+=wave[i];
                        }
                        subdivision++;
                        if(subdivision>=offsets.length){subdivision=0;nextEvent=beatStart+duration;}
                        else nextEvent=beatStart+duration*offsets[subdivision];
                    }
                    int at=(int)(frame&(RING_SIZE-1));
                    float sample=mix[at]*(c.volume/100f);mix[at]=0;
                    pcm[produced]=(short)Math.max(-32767,Math.min(32767,Math.round(sample*26000f)));
                    frame++;
                }
                int wrote=0;
                while(running&&wrote<produced) {
                    int n=output.write(pcm,wrote,produced-wrote,AudioTrack.WRITE_BLOCKING);
                    if(n<0)throw new IllegalStateException("오디오 출력 오류 ("+n+")");
                    if(n==0)break;wrote+=n;
                }
                long head=output.getPlaybackHeadPosition()&0xffffffffL;
                if(head<lastHead)playWrap+=1L<<32;lastHead=head;played=playWrap+head;
                while(!marks.isEmpty()&&marks.peek().frame<=played){Mark m=marks.remove();visible=m.snapshot;visiblePracticeStart=m.practiceStart;}
                if(played-lastPublish>=RATE/30||lastPublish<0) {
                    long ms=visiblePracticeStart<0?0:Math.max(0,played-visiblePracticeStart)*1000/RATE;
                    listener.onState(new Snapshot(visible.muted,visible.countingIn,visible.beat,visible.bpm,visible.bars,ms));lastPublish=played;
                }
                if(automaticEnd) {
                    // Drain only the scheduled samples; never queue a click beyond the stop boundary.
                    long deadline=System.nanoTime()+500000000L;
                    while(running&&played<frame&&System.nanoTime()<deadline) {
                        try{Thread.sleep(2);}catch(InterruptedException ignored){}
                        head=output.getPlaybackHeadPosition()&0xffffffffL;
                        if(head<lastHead)playWrap+=1L<<32;lastHead=head;played=playWrap+head;
                    }
                    long ms=practiceStart<0?0:Math.max(0,frame-practiceStart)*1000/RATE;
                    listener.onState(new Snapshot(muted,false,beat,actualBpm,Math.max(0,bar),ms));
                    break;
                }
            }
        } catch(Exception e) {
            if(running)error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        } finally {
            running=false;track=null;
            if(output!=null){try{output.pause();output.flush();output.release();}catch(Exception ignored){}}
            listener.onStopped(error);
        }
    }
    /** Short, synthesized original clicks; no copied sample assets. */
    private static float[][][] makeSounds() {
        float[][][] all=new float[12][3][];
        for(int type=0;type<12;type++)for(int voice=0;voice<3;voice++) {
            int n=(type==7?3360:type==11?1440:1920);
            float[] x=new float[n];double pitch=voice==0?1.3:voice==2?.82:1;
            double gain=voice==0?.9:voice==1?.68:.36;
            int random=701+type*997+voice*31;double previous=0;
            for(int i=0;i<n;i++) {
                double t=i/(double)RATE;
                random^=random<<13;random^=random>>>17;random^=random<<5;
                double noise=(random/(double)Integer.MAX_VALUE),v=0;
                switch(type) {
                    case 0: v=Math.sin(2*Math.PI*1750*pitch*t)*Math.exp(-t*230);break;
                    case 1: v=(Math.sin(2*Math.PI*920*pitch*t)+.4*Math.sin(2*Math.PI*1860*pitch*t))*Math.exp(-t*125)*.7;break;
                    case 2: v=Math.sin(2*Math.PI*2450*pitch*t)*Math.exp(-t*240);break;
                    case 3: v=(.55*noise+.5*Math.sin(2*Math.PI*1600*pitch*t))*Math.exp(-t*240);break;
                    case 4: v=(Math.signum(Math.sin(2*Math.PI*540*pitch*t))+.6*Math.signum(Math.sin(2*Math.PI*800*pitch*t)))*.4*Math.exp(-t*95);break;
                    case 5: v=(noise-previous)*.5*Math.exp(-t*110);break;
                    case 6: v=(noise*.3+Math.sin(2*Math.PI*3500*pitch*t)*.6)*Math.exp(-t*420);break;
                    case 7: v=(Math.sin(2*Math.PI*1400*pitch*t)+.3*Math.sin(2*Math.PI*2830*pitch*t))*.65*Math.exp(-t*60);break;
                    case 8: v=Math.sin(2*Math.PI*320*pitch*t)*Math.exp(-t*110);break;
                    case 9: v=(Math.sin(2*Math.PI*2400*pitch*t)*.8+noise*.2)*Math.exp(-t*145);break;
                    case 10: v=Math.sin(2*Math.PI*(1800*pitch*t-7000*t*t))*Math.exp(-t*130);break;
                    default:v=Math.sin(2*Math.PI*650*pitch*t)*Math.exp(-t*155);break;
                }
                previous=noise;
                // Sub-millisecond attack and tail taper avoid unintended discontinuities.
                double envelope=Math.min(1,i/12.0)*Math.min(1,(n-i)/96.0);
                x[i]=(float)(v*gain*envelope);
            }
            all[type][voice]=x;
        }
        return all;
    }
}
