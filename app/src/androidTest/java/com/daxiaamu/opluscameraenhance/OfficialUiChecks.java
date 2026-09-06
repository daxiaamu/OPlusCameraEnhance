package com.daxiaamu.opluscameraenhance;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
public final class OfficialUiChecks extends Instrumentation {
 @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
 @Override public void onStart() {
  Bundle result=new Bundle();
  final Throwable[] failure={null};
  Activity activity=null;
  try {
   activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   final Activity host=activity;
   runOnMainSync(()->{
    try {
     ColorOsRuntime runtime=new ColorOsRuntime(host);
     android.content.res.ColorStateList colors=runtime.updateTextColors();
     int normal=colors.getColorForState(new int[]{android.R.attr.state_enabled},0);
     int disabled=colors.getColorForState(new int[]{-android.R.attr.state_enabled},0);
     if(android.graphics.Color.alpha(normal)<=android.graphics.Color.alpha(disabled)) throw new AssertionError("Official disabled color state missing");
     CompoundButton control=(CompoundButton)runtime.widget("com.coui.appcompat.couiswitch.COUISwitch");
     if(!control.getClass().getName().equals("com.coui.appcompat.couiswitch.COUISwitch")) throw new AssertionError("Wrong widget");
     java.lang.reflect.Field bar=control.getClass().getDeclaredField("mBarCheckedColor"); bar.setAccessible(true);
     if(bar.getInt(control)!=normal) throw new AssertionError("Switch and update action disagree on current theme color");
     result.putString("themeColor",Integer.toHexString(normal));
     final boolean[] changed={false};
     control.setOnCheckedChangeListener((button,value)->changed[0]=value);
     control.setChecked(true);
     if(!changed[0]) throw new AssertionError("Switch callback missing");
     android.view.View row=runtime.preference("Test","Summary",control);
     if(!"Test".contentEquals(((TextView)row.findViewById(android.R.id.title)).getText())) throw new AssertionError("Resource binding");
     TextView notes=new TextView(host); notes.setText("Official dialog integration test");
     ColorOsRuntime.OfficialDialog dialog=runtime.dialog("UI test",notes,"Install","Later","Skip");
     try {
      dialog.show();
      for(int id:new int[]{-1,-2,-3}) if(dialog.getButton(id)==null) throw new AssertionError("Missing dialog action "+id);
      final boolean[] clicked={false};
      dialog.getButton(-1).setOnClickListener(view->clicked[0]=true);
      dialog.getButton(-1).performClick();
      if(!clicked[0]) throw new AssertionError("Dialog callback missing");
     } finally { dialog.dismiss(); }
    } catch(Throwable error) { failure[0]=error; }
   });
   if(failure[0]!=null) throw failure[0];
   result.putString("stream","Official Settings APK: switch, layout, Breeno color states, three dialog actions and callbacks passed.");
   finish(-1,result);
  } catch(Throwable error) {
   result.putString("stream",android.util.Log.getStackTraceString(error));
   finish(0,result);
  }
 }
}
