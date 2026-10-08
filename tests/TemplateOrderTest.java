import dev.touchxbox.pad.LayoutProfile;
import dev.touchxbox.pad.TemplateOrder;
import java.util.*;

public final class TemplateOrderTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static List<LayoutProfile> fixtures(){
        List<LayoutProfile> list=new ArrayList<>();
        for(int i=0;i<8;i++){
            LayoutProfile p=new LayoutProfile();p.id="template-"+i;p.name="Template "+i;p.protocol=i%4;
            p.leftFloating=i%2==0;p.landscape.items.get("LT").x=.1f+i*.03f;list.add(p);
        }
        return list;
    }
    public static void main(String[] args){
        for(int from=0;from<8;from++)for(int to=0;to<8;to++){
            List<LayoutProfile> original=fixtures(),moved=new ArrayList<>(original);
            LayoutProfile selected=original.get(3),source=original.get(from);
            check(TemplateOrder.move(moved,source.id,original.get(to).id),"Valid move succeeds");
            check(moved.size()==original.size()&&moved.get(to)==source,"Source reaches requested final position");
            check(new HashSet<>(moved).size()==original.size(),"No duplicate or missing template");
            List<LayoutProfile> otherBefore=new ArrayList<>(original),otherAfter=new ArrayList<>(moved);
            otherBefore.remove(source);otherAfter.remove(source);
            check(otherBefore.equals(otherAfter),"All other templates keep relative order");
            check(moved.stream().anyMatch(p->p==selected),"Selection by ID is unaffected");
            for(LayoutProfile p:moved){
                int i=Integer.parseInt(p.id.substring("template-".length()));
                check(p==original.get(i)&&p.name.equals("Template "+i)&&p.protocol==i%4&&p.leftFloating==(i%2==0),"Template identity and settings unchanged");
                check(p.landscape.items.get("LT").x==.1f+i*.03f,"Geometry unchanged");
            }
        }
        List<LayoutProfile> list=fixtures(),before=new ArrayList<>(list);
        check(!TemplateOrder.move(list,"missing",list.get(0).id)&&list.equals(before),"Stale dragged ID is rejected without changes");
        check(!TemplateOrder.move(list,list.get(0).id,"missing")&&list.equals(before),"Stale destination is rejected without changes");
        check(!TemplateOrder.move(list,null,list.get(0).id)&&list.equals(before),"Invalid dragged ID is rejected");
        check(!TemplateOrder.move(list,list.get(0).id,null)&&list.equals(before),"Invalid target ID is rejected");
        check(!TemplateOrder.move(new ArrayList<>(),"missing","missing"),"Empty library is safe");
        List<LayoutProfile> single=new ArrayList<>(Collections.singletonList(list.get(0)));
        check(TemplateOrder.move(single,single.get(0).id,single.get(0).id)&&single.size()==1,"Same-position move is a no-op");
        System.out.println("TemplateOrderTest passed: all 64 moves, data preservation, stale IDs and empty/single libraries");
    }
}
