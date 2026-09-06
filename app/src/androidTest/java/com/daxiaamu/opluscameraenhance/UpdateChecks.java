package com.daxiaamu.opluscameraenhance;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
import java.lang.reflect.*;
public final class UpdateChecks extends Instrumentation {
 @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
 private Object manager;
 private Method parse;
 private JSONObject valid() throws Exception {
  JSONObject j = new JSONObject();
  j.put("schemaVersion",1).put("channel","stable").put("versionCode",100).put("versionName","1.0.0");
  j.put("policyRevision",1).put("maxForcedVersionCode",0).put("sha256","a".repeat(64));
  JSONArray urls = new JSONArray();
  for(int i=0;i<5;i++) urls.put("https://cdn"+i+".example.org/app.apk");
  return j.put("urls",urls).put("size",1000);
 }
 private void rejects(JSONObject j) throws Exception {
  try { parse.invoke(manager,j); throw new AssertionError("Accepted invalid metadata"); }
  catch(InvocationTargetException expected) { }
 }
 @Override public void onStart() {
  Bundle result = new Bundle();
  try {
   Class<?> owner=Class.forName("com.daxiaamu.opluscameraenhance.update.UpdateManager",true,getTargetContext().getClassLoader());
   manager=owner.getField("INSTANCE").get(null);
   parse=owner.getDeclaredMethod("parseManifest",JSONObject.class); parse.setAccessible(true);
   Object manifest=parse.invoke(manager,valid());
   Method required=manifest.getClass().getMethod("isRequired",long.class);
   if((boolean)required.invoke(manifest,8L)) throw new AssertionError("Optional became required");
   Object forced=parse.invoke(manager,valid().put("maxForcedVersionCode",50));
   if(!(boolean)required.invoke(forced,8L) || (boolean)required.invoke(forced,100L)) throw new AssertionError("Forced boundary wrong");
   rejects(valid().put("sha256","bad"));
   rejects(valid().put("channel","beta"));
   rejects(valid().put("schemaVersion",2));
   rejects(valid().put("versionCode",0));
   rejects(valid().put("maxForcedVersionCode",100));
   rejects(valid().put("maxForcedVersionCode",-1));
   rejects(valid().put("policyRevision",0));
   JSONObject insecure=valid(); insecure.getJSONArray("urls").put(0,"http://cdn.example.org/a.apk"); rejects(insecure);
   JSONObject duplicate=valid(); duplicate.getJSONArray("urls").put(4,"https://cdn0.example.org/other.apk"); rejects(duplicate);
   JSONObject few=valid(); few.getJSONArray("urls").remove(4); rejects(few);
   result.putString("stream","PASS: 12 update metadata and policy checks");
   finish(-1,result);
  } catch(Throwable e) { result.putString("stream","FAIL: "+e); finish(1,result); }
 }
}