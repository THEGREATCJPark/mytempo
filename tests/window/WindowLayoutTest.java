import com.cj.pulse.WindowLayout;
public class WindowLayoutTest {
 public static void main(String[] args){
  WindowLayout portrait=WindowLayout.forWindow(600,1000,false),landscape=WindowLayout.forWindow(1000,600,false),popup=WindowLayout.forWindow(240,320,false),pip=WindowLayout.forWindow(240,160,true);
  check(portrait.tablet&&!portrait.twoColumns,"S6 Lite portrait");check(landscape.tablet&&landscape.twoColumns,"S6 Lite landscape");check(popup.compact&&!popup.twoColumns,"small popup");check(pip.pip&&pip.compact,"PiP");
  for(int width=180;width<=1600;width+=20)for(int height=160;height<=1200;height+=40){WindowLayout l=WindowLayout.forWindow(width,height,false);check(l.maxWidth>=600&&l.padding<=24,"bounded content");for(int beats=1;beats<=13;beats++){int rows=WindowLayout.beatRows(beats,width-l.padding*2);check(rows>=1&&rows<=beats,"beat rows cover all beats");}}
  check(WindowLayout.beatRows(13,192)==4,"13 beats in a narrow popup must wrap");
  System.out.println("PASS window layout: tablet orientations, compact popup, PiP and all 1–13 beat wrapping sizes.");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
