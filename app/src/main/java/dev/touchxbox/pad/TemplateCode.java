package dev.touchxbox.pad;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.*;
import org.json.*;

/** Portable layout only: no bridge keys, device identity, or application permissions. */
public final class TemplateCode {
    private static final String PREFIX="TXPAD1:";
    private static final int MAX_JSON=65536,MAX_CODE=90000;
    public static String encode(LayoutProfile profile){
        try{JSONObject json=new JSONObject(LayoutStore.encode(profile));json.remove("id");byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_JSON)throw new IllegalArgumentException("配置过大");ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream zip=new GZIPOutputStream(out)){zip.write(bytes);}return PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());}
        catch(IOException|JSONException e){throw new IllegalArgumentException("配置复制失败",e);}
    }
    public static LayoutProfile decode(String text){
        if(text==null||text.length()>MAX_CODE)throw new IllegalArgumentException("配置字符串过长");String code=text.trim();if(!code.startsWith(PREFIX))throw new IllegalArgumentException("请粘贴以 TXPAD1: 开头的配置字符串");
        try{byte[] bytes=Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPInputStream zip=new GZIPInputStream(new ByteArrayInputStream(bytes))){byte[] buffer=new byte[2048];for(int n;(n=zip.read(buffer))!=-1;){if(out.size()+n>MAX_JSON)throw new IllegalArgumentException("配置内容过大");out.write(buffer,0,n);}}
            JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));int version=json.optInt("version",0);if(version<1||version>8)throw new IllegalArgumentException("配置版本不支持，请更新应用");if(!(json.opt("name") instanceof String)||json.getString("name").trim().isEmpty()||json.getString("name").length()>40||json.optJSONObject("landscape")==null||json.optJSONObject("portrait")==null)throw new IllegalArgumentException("配置内容不完整");LayoutProfile p=LayoutStore.decode(json.toString());p.id=UUID.randomUUID().toString();return p;
        }catch(IOException|JSONException e){throw new IllegalArgumentException("配置字符串损坏或不完整",e);}catch(IllegalArgumentException e){throw new IllegalArgumentException(e.getMessage()==null?"配置字符串无效":e.getMessage(),e);}
    }
}
