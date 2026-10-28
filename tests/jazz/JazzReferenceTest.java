import com.cj.pulse.JazzReference;
import java.util.*;
public class JazzReferenceTest {
 public static void main(String[] args){
  if(JazzReference.NAMES.length!=23||JazzReference.FORMULAS.length!=23)throw new AssertionError("catalog size");
  Set<Integer> order=new HashSet<>();for(int i:JazzReference.ORDER){if(i<0||i>=23||!order.add(i))throw new AssertionError("ordering loses a scale");}if(order.size()!=23)throw new AssertionError("incomplete order");
  int[] major={0,2,4,5,7,9,11};
  for(String formula:JazzReference.FORMULAS){int previous=-1;for(String degree:formula.split(", ")){int delta=degree.startsWith("♭")?-1:degree.startsWith("♯")?1:0;int n=Integer.parseInt(degree.replace("♭","").replace("♯",""));int pitch=major[n-1]+delta;if(pitch<=previous||pitch<0||pitch>11)throw new AssertionError("invalid ascending scale: "+formula);previous=pitch;}}
  if(!JazzReference.FORMULAS[7].equals("1, 2, ♭3, 4, 5, 6, 7"))throw new AssertionError("melodic minor");
  if(JazzReference.CHORDS.length!=JazzReference.TENSIONS.length||!JazzReference.TENSIONS[0].equals("9, 13"))throw new AssertionError("tension lookup");
  System.out.println("PASS jazz catalog: unique study ordering, ascending distinct pitches, melodic minor and tension lookup.");
 }
}
