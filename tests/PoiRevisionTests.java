import kr.harusarri.neodymium2.core.PoiRevisionGrid;
import java.util.Random;
public class PoiRevisionTests {
 public static void main(String[] args){var grid=new PoiRevisionGrid();var random=new Random(772);int n=0;
  for(int i=0;i<20000;i++){int x=random.nextInt(2000000)-1000000,z=random.nextInt(2000000)-1000000;long before=grid.near(x,z);grid.changed(x+random.nextInt(97)-48,z+random.nextInt(97)-48);if(grid.near(x,z)<=before)throw new AssertionError("Missed POI change within native search extent");n++;}
  System.out.println("NEO_POI_REVISION_PASS "+n);
 }
}
