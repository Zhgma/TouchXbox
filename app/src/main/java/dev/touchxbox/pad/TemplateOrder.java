package dev.touchxbox.pad;

import java.util.List;

/** Move one template to another template's position, retaining the other entries in order. */
public final class TemplateOrder {
    private TemplateOrder(){}
    public static boolean move(List<LayoutProfile> profiles,String id,String targetId){
        if(id==null||targetId==null)return false;
        int from=-1,to=-1;
        for(int i=0;i<profiles.size();i++){
            String candidate=profiles.get(i).id;
            if(candidate.equals(id))from=i;
            if(candidate.equals(targetId))to=i;
        }
        if(from<0||to<0)return false;
        if(from!=to)profiles.add(to,profiles.remove(from));
        return true;
    }
}
