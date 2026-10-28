package com.cj.pulse;

import org.json.*;
import java.util.Arrays;

public class Config {
    public int bpm=120, numerator=4, denominator=4, pattern=0, sound=0, volume=80;
    public int[] accents=new int[]{2,1,1,1,1,1,1,1,1,1,1,1,1};
    public int stopMode=0, stopAfter=0, autoMode=0, autoEvery=4, autoStep=5, autoTarget=160;
    public int coachSound=4, coachMute=0, countIn=0;
    public static final String[] SOUND_NAMES={"Digital", "Woodblock", "Clave", "Rimshot", "Cowbell", "Hi-hat", "Stick", "Bell", "Low click", "Live click", "Drop", "Soft"};
    public boolean compound(){return denominator==8;}
    public int beats(){return compound()?numerator/3:numerator;}
    public String meter(){return numerator+"/"+denominator;}
    public static int clamp(int x,int min,int max){return Math.max(min,Math.min(max,x));}
    public void sanitize(){
        bpm=clamp(bpm,10,400); denominator=(denominator==2||denominator==8)?denominator:4;
        numerator=denominator==8?clamp((numerator/3)*3,3,12):clamp(numerator,1,13);
        pattern=clamp(pattern,0,compound()?2:5); sound=clamp(sound,0,11); volume=clamp(volume,0,100);
        if(accents==null||accents.length!=13){accents=new int[13];Arrays.fill(accents,1);accents[0]=2;}
        for(int i=0;i<13;i++)accents[i]=clamp(accents[i],0,2);
        stopMode=clamp(stopMode,0,2);stopAfter=clamp(stopAfter,0,86400);
        autoMode=clamp(autoMode,0,2);autoEvery=clamp(autoEvery,1,3600);autoStep=clamp(autoStep,-100,100);autoTarget=clamp(autoTarget,10,400);
        coachSound=clamp(coachSound,1,64);coachMute=clamp(coachMute,0,64);countIn=clamp(countIn,0,4);
    }
    public JSONObject toJson(){
        JSONObject j=new JSONObject();try{
            j.put("bpm",bpm).put("numerator",numerator).put("denominator",denominator).put("pattern",pattern).put("sound",sound).put("volume",volume)
             .put("accents",new JSONArray(accents)).put("stopMode",stopMode).put("stopAfter",stopAfter).put("autoMode",autoMode).put("autoEvery",autoEvery)
             .put("autoStep",autoStep).put("autoTarget",autoTarget).put("coachSound",coachSound).put("coachMute",coachMute).put("countIn",countIn);
        }catch(JSONException ignored){}return j;
    }
    public static Config fromJson(JSONObject j){
        Config c=new Config();if(j==null)return c;
        c.bpm=j.optInt("bpm",120);c.numerator=j.optInt("numerator",4);c.denominator=j.optInt("denominator",4);c.pattern=j.optInt("pattern");c.sound=j.optInt("sound");c.volume=j.optInt("volume",80);
        JSONArray a=j.optJSONArray("accents");if(a!=null)for(int i=0;i<13;i++)c.accents[i]=a.optInt(i,i==0?2:1);
        c.stopMode=j.optInt("stopMode");c.stopAfter=j.optInt("stopAfter");c.autoMode=j.optInt("autoMode");c.autoEvery=j.optInt("autoEvery",4);c.autoStep=j.optInt("autoStep",5);c.autoTarget=j.optInt("autoTarget",160);
        c.coachSound=j.optInt("coachSound",4);c.coachMute=j.optInt("coachMute");c.countIn=j.optInt("countIn");c.sanitize();return c;
    }
    public Config copy(){return fromJson(toJson());}
    public static String tempoName(int bpm){
        if(bpm<40)return "Grave";if(bpm<60)return "Largo";if(bpm<66)return "Larghetto";if(bpm<76)return "Adagio";if(bpm<108)return "Andante";if(bpm<120)return "Moderato";if(bpm<156)return "Allegro";if(bpm<176)return "Vivace";if(bpm<200)return "Presto";return "Prestissimo";
    }
}
