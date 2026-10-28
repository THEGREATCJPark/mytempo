package com.cj.pulse;
/** Test fixture replaces only JSON serialization; engine uses production AudioEngine/Rhythm. */
public class Config implements Cloneable {
 public int bpm=120,numerator=4,denominator=4,pattern=0,sound=0,volume=80;
 public int[] accents={2,1,1,1,1,1,1,1,1,1,1,1,1};
 public int stopMode=1,stopAfter=2,autoMode=0,autoEvery=4,autoStep=5,autoTarget=160,coachSound=4,coachMute=0,countIn=0;
 public boolean compound(){return denominator==8;}public int beats(){return compound()?numerator/3:numerator;}
 public Config copy(){try{Config c=(Config)super.clone();c.accents=accents.clone();return c;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
}
