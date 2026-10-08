package dev.touchxbox.pad;

import android.content.*;
import org.json.*;
import java.util.*;

/** Named templates are committed atomically; editing a draft never changes a saved template. */
public final class LayoutStore {
    private final SharedPreferences prefs; private final Context context;
    public LayoutStore(Context c){context=c;prefs=c.getSharedPreferences("templates-v2",0);}
    public List<LayoutProfile> all(){
        List<LayoutProfile> result=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("profiles","[]"));for(int i=0;i<a.length();i++)result.add(decode(a.getJSONObject(i).toString()));}catch(Exception ignored){}
        if(result.isEmpty()&&!prefs.contains("profiles")){
            SharedPreferences old=context.getSharedPreferences("layout",0);
            // Only migrate a real pre-template layout. A fresh install starts empty.
            if(!old.getAll().isEmpty()){
                LayoutProfile p=new LayoutProfile();p.opacity=LayoutProfile.clamp(old.getFloat("opacity",.72f),.25f,.9f);
                for(boolean wide:new boolean[]{true,false})for(LayoutProfile.Spec s:LayoutProfile.SPECS){LayoutProfile.Item v=p.page(wide).items.get(s.name);String k=(wide?"landscape":"portrait")+s.name;v.x=old.getFloat(k+"x",v.x);v.y=old.getFloat(k+"y",v.y);v.scale=old.getFloat("scale",1);}
                result.add(p);
            }
            write(result,result.isEmpty()?"":result.get(0).id);
        }
        boolean migrated=false;for(LayoutProfile p:result)migrated|=prepareSize(p);
        if(migrated)write(result,prefs.getString("active",""));
        return result;
    }
    /** Capture old/default sizes once, then carry that ratio unchanged across devices. */
    public boolean prepareSize(LayoutProfile profile){boolean changed=false;for(boolean wide:new boolean[]{true,false}){LayoutViewport v=ScreenSpace.viewport(context,wide);changed|=profile.page(wide).ensureRelativeSize(v.width,v.height);}return changed;}
    public LayoutProfile active(){List<LayoutProfile> list=all();String id=prefs.getString("active","");for(LayoutProfile p:list)if(p.id.equals(id))return p;return list.isEmpty()?null:list.get(0);}
    public boolean select(String id){return prefs.edit().putString("active",id).commit();}
    public boolean delete(String id){List<LayoutProfile> list=all();String active=prefs.getString("active","");boolean removed=false;for(Iterator<LayoutProfile> it=list.iterator();it.hasNext();)if(it.next().id.equals(id)){it.remove();removed=true;}if(!removed)return false;if(active.equals(id))active=list.isEmpty()?"":list.get(0).id;return write(list,active);}
    public String uniqueName(String requested){String base=requested.trim();if(base.isEmpty())base="新模板";base=base.substring(0,Math.min(40,base.length()));Set<String> names=new HashSet<>();for(LayoutProfile p:all())names.add(p.name.toLowerCase(Locale.ROOT));String candidate=base;for(int n=2;names.contains(candidate.toLowerCase(Locale.ROOT));n++){String suffix=" ("+n+")";candidate=base.substring(0,Math.min(base.length(),40-suffix.length()))+suffix;}return candidate;}
    public String save(LayoutProfile profile){
        String name=profile.name.trim();if(name.isEmpty()||name.length()>40)return "模板名需为 1–40 个字符";
        List<LayoutProfile> list=all();int replace=-1;
        for(int i=0;i<list.size();i++){LayoutProfile p=list.get(i);if(p.id.equals(profile.id))replace=i;else if(p.name.equalsIgnoreCase(name))return "已有同名模板，请换一个名称";}
        profile.name=name;if(replace<0)list.add(profile);else list.set(replace,profile);
        return write(list,profile.id)?null:"保存失败，请重试";
    }
    private boolean write(List<LayoutProfile> list,String active){JSONArray a=new JSONArray();try{for(LayoutProfile p:list){prepareSize(p);a.put(new JSONObject(encode(p)));}}catch(JSONException e){return false;}return prefs.edit().putString("profiles",a.toString()).putString("active",active).commit();}
    public static String encode(LayoutProfile p){
        try{JSONObject o=new JSONObject().put("version",7).put("id",p.id).put("name",p.name).put("protocol",p.protocol).put("landscapeOnly",p.landscapeOnly).put("leftFloating",p.leftFloating).put("rightFloating",p.rightFloating).put("leftTriggerClick",p.leftTriggerClick).put("rightTriggerClick",p.rightTriggerClick).put("swapAB",p.swapAB).put("swapXY",p.swapXY).put("abxyStyle",p.abxyStyle).put("dpadStyle",p.dpadStyle).put("opacity",p.opacity);
            JSONArray keyboard=new JSONArray();for(LayoutProfile.Spec s:p.keyboard)keyboard.put(s.name);o.put("keyboard",keyboard);JSONObject labels=new JSONObject();for(Map.Entry<String,String[]> e:p.labels.entrySet())labels.put(e.getKey(),new JSONArray().put(e.getValue()[0]).put(e.getValue()[1]));o.put("labels",labels);JSONArray toggles=new JSONArray();for(String key:new TreeSet<>(p.toggleLabels))toggles.put(key);o.put("toggleLabels",toggles);JSONArray hidden=new JSONArray();for(String key:new TreeSet<>(p.hiddenButtons))hidden.put(key);o.put("hiddenButtons",hidden);JSONArray retention=new JSONArray();for(String key:new TreeSet<>(p.holdOutside))retention.put(key);o.put("holdOutside",retention);
            for(boolean wide:new boolean[]{true,false}){LayoutProfile.Page page=p.page(wide);JSONObject v=new JSONObject().put("unitRatio",page.unitRatio).put("shoulderLayout",page.shoulderLayout).put("shoulderX",page.shoulderX).put("shoulderY",page.shoulderY).put("foldX",page.foldX).put("foldY",page.foldY).put("foldW",page.foldW).put("foldH",page.foldH).put("shoulderWidth",page.shoulderWidth).put("triggerHeight",page.triggerHeight).put("bumperHeight",page.bumperHeight).put("keyboardScale",page.keyboardScale);JSONObject items=new JSONObject();for(Map.Entry<String,LayoutProfile.Item> e:page.items.entrySet()){LayoutProfile.Item item=e.getValue();JSONObject geometry=new JSONObject().put("x",item.x).put("y",item.y).put("scale",item.scale);if(item.width>0)geometry.put("width",item.width);if(item.height>0)geometry.put("height",item.height);items.put(e.getKey(),geometry);}v.put("items",items);o.put(wide?"landscape":"portrait",v);}return o.toString();
        }catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    public static LayoutProfile decode(String json){
        LayoutProfile p=new LayoutProfile();try{JSONObject o=new JSONObject(json);p.id=o.optString("id",p.id);p.name=o.optString("name",p.name);p.protocol=o.optInt("protocol",0);p.landscapeOnly=o.optBoolean("landscapeOnly");if(p.protocol!=ControllerProtocol.valid(p.protocol))throw new IllegalArgumentException("模板包含不支持的手柄协议");p.leftFloating=o.optBoolean("leftFloating");p.rightFloating=o.optBoolean("rightFloating");p.leftTriggerClick=o.optBoolean("leftTriggerClick");p.rightTriggerClick=o.optBoolean("rightTriggerClick");p.swapAB=o.optBoolean("swapAB");p.swapXY=o.optBoolean("swapXY");p.abxyStyle=(int)number(o,"abxyStyle",0,0,1);p.dpadStyle=(int)number(o,"dpadStyle",0,0,2);p.opacity=number(o,"opacity",.72f,.25f,.9f);
            JSONArray keyboard=o.optJSONArray("keyboard");if(keyboard!=null){if(keyboard.length()>32)throw new IllegalArgumentException("模板键盘按键过多");Set<String> seen=new HashSet<>();for(int i=0;i<keyboard.length();i++){String key=keyboard.getString(i);LayoutProfile.Spec s=LayoutProfile.spec(key);if(s==null||s.kind!=5||!seen.add(key))throw new IllegalArgumentException("无效的键盘按键");p.keyboard.add(s);p.landscape.items.put(key,new LayoutProfile.Item(s.x,s.y,1));p.portrait.items.put(key,new LayoutProfile.Item(s.x,s.y,1));}}
            JSONObject labels=o.optJSONObject("labels");if(labels!=null){if(labels.length()>64)throw new IllegalArgumentException("按键名称过多");for(Iterator<String> keys=labels.keys();keys.hasNext();){String key=keys.next();JSONArray pair=labels.optJSONArray(key);if(!LayoutProfile.labelKey(key)||pair==null||pair.length()!=2)throw new IllegalArgumentException("无效的按键名称");String up=pair.getString(0),down=pair.getString(1);if(up.length()>24||down.length()>24)throw new IllegalArgumentException("按键名称最多 24 字符");p.labels.put(key,new String[]{up,down});}}
            JSONArray hidden=o.optJSONArray("hiddenButtons");if(hidden!=null){if(hidden.length()>18)throw new IllegalArgumentException("无效的隐藏按键配置");for(int i=0;i<hidden.length();i++){String key=hidden.getString(i);LayoutProfile.Spec spec=LayoutProfile.spec(key);if(spec==null||spec.kind==4||spec.kind==5)throw new IllegalArgumentException("无效的隐藏按键");p.hiddenButtons.add(key);}}
            JSONArray retention=o.optJSONArray("holdOutside");if(retention!=null){if(retention.length()>64)throw new IllegalArgumentException("滑出保持配置过多");p.holdOutside.clear();for(int i=0;i<retention.length();i++){String key=retention.getString(i);LayoutProfile.Spec s=LayoutProfile.spec(key);if(!LayoutProfile.canHoldOutside(s)||s.kind==5&&p.keyboard.stream().noneMatch(k->k.name.equals(key)))throw new IllegalArgumentException("无效的滑出保持按键");p.holdOutside.add(key);}}
            JSONArray toggles=o.optJSONArray("toggleLabels");if(toggles!=null){if(toggles.length()>64)throw new IllegalArgumentException("高亮配置过多");for(int i=0;i<toggles.length();i++){String key=toggles.getString(i);if(!LayoutProfile.labelKey(key))throw new IllegalArgumentException("无效的高亮配置");p.toggleLabels.add(key);}}
            for(boolean wide:new boolean[]{true,false}){JSONObject v=o.optJSONObject(wide?"landscape":"portrait");if(v==null)continue;LayoutProfile.Page page=p.page(wide);page.unitRatio=number(v,"unitRatio",0,0,.01f);page.shoulderLayout=(int)number(v,"shoulderLayout",0,0,2);page.shoulderX=number(v,"shoulderX",.2f,0,.5f);page.shoulderY=number(v,"shoulderY",.2f,0,1);page.foldW=number(v,"foldW",page.foldW,.08f,.6f);page.foldH=number(v,"foldH",page.foldH,.035f,.4f);page.foldX=number(v,"foldX",page.foldX,page.foldW/2,1-page.foldW/2);page.foldY=number(v,"foldY",page.foldY,page.foldH/2,1-page.foldH/2);JSONObject items=v.optJSONObject("items");if(items!=null)for(LayoutProfile.Spec s:p.specs()){JSONObject item=items.optJSONObject(s.name);if(item==null)continue;LayoutProfile.Item t=page.items.get(s.name);t.x=number(item,"x",t.x,0,1);t.y=number(item,"y",t.y,0,1);t.scale=number(item,"scale",1,.55f,1.65f);t.width=number(item,"width",0,0,400);t.height=number(item,"height",0,0,400);}page.shoulderWidth=number(v,"shoulderWidth",page.items.get("LT").scale,.5f,1.8f);page.triggerHeight=number(v,"triggerHeight",page.items.get("LT").scale,.4f,2.2f);page.bumperHeight=number(v,"bumperHeight",page.items.get("LB").scale,.4f,2.2f);page.keyboardScale=number(v,"keyboardScale",1,.55f,1.65f);}
        }catch(JSONException e){throw new IllegalArgumentException("布局文件损坏",e);}return p;
    }
    private static float number(JSONObject o,String key,float def,float lo,float hi){double n=o.optDouble(key,def);return Double.isNaN(n)||Double.isInfinite(n)?def:LayoutProfile.clamp((float)n,lo,hi);}
}
