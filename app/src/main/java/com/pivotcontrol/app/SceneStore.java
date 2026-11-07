package com.pivotcontrol.app;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.ArrayList;
import java.util.Collections;

final class SceneStore {
    private final SharedPreferences p;
    SceneStore(Context c){p=c.getSharedPreferences("mirror",0);}
    Scene load(){String raw=p.getString("scene",null);if(raw!=null)return decode(raw);Scene s=new Scene();s.portrait=p.getBoolean("portrait",true);s.reverse=p.getBoolean("reverse",false);s.scaling=p.getBoolean("fill",false)?1:0;return s;}
    void save(Scene s){p.edit().putString("scene",encode(s)).putBoolean("portrait",s.portrait).putBoolean("reverse",s.reverse).putBoolean("fill",s.scaling==1).apply();}
    void preset(String name,Scene s){p.edit().putString("preset:"+name,encode(s)).apply();}
    Scene preset(String name){return decode(p.getString("preset:"+name,"{}"));}
    void delete(String name){p.edit().remove("preset:"+name).apply();}
    ArrayList<String> names(){ArrayList<String> n=new ArrayList<>();for(String k:p.getAll().keySet())if(k.startsWith("preset:"))n.add(k.substring(7));Collections.sort(n);return n;}
    static String encode(Scene s){try{
        JSONObject j=new JSONObject();j.put("portrait",s.portrait);j.put("reverse",s.reverse);j.put("regions",s.regions);j.put("dim",s.dim);j.put("quality",s.highQuality);j.put("lock",s.lockRatio);j.put("scaling",s.scaling);j.put("layout",s.layout);j.put("fps",s.fps);j.put("sharp",s.sharpness);
        JSONArray a=new JSONArray();for(Scene.Region r:s.items){JSONArray v=new JSONArray();for(float f:new float[]{r.source.l,r.source.t,r.source.r,r.source.b,r.dest.l,r.dest.t,r.dest.r,r.dest.b})v.put(f);a.put(v);}j.put("items",a);return j.toString();
    }catch(JSONException e){throw new IllegalStateException(e);}}
    static Scene decode(String raw){Scene s=new Scene();try{
        JSONObject j=new JSONObject(raw);s.portrait=j.optBoolean("portrait",true);s.reverse=j.optBoolean("reverse");s.regions=j.optBoolean("regions");s.dim=j.optBoolean("dim",true);s.highQuality=j.optBoolean("quality",true);s.lockRatio=j.optBoolean("lock");s.scaling=Math.max(0,Math.min(2,j.optInt("scaling")));s.layout=Math.max(0,Math.min(2,j.optInt("layout")));s.fps=j.optInt("fps",60)==30?30:60;s.sharpness=(float)Math.max(0,Math.min(.5,j.optDouble("sharp",0)));
        JSONArray a=j.optJSONArray("items");if(a!=null&&a.length()>0){s.items.clear();for(int i=0;i<Math.min(4,a.length());i++){JSONArray v=a.getJSONArray(i);Scene.Region r=new Scene.Region((float)v.getDouble(0),(float)v.getDouble(1),(float)v.getDouble(2),(float)v.getDouble(3));r.source.clamp();r.dest=new Scene.Box((float)v.getDouble(4),(float)v.getDouble(5),(float)v.getDouble(6),(float)v.getDouble(7));r.dest.clamp();s.items.add(r);}}
    }catch(Exception ignored){return new Scene();}return s;}
}
