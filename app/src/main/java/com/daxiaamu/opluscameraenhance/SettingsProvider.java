package com.daxiaamu.opluscameraenhance;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
public final class SettingsProvider extends ContentProvider {
 public static final Uri URI = Uri.parse("content://com.daxiaamu.opluscameraenhance.settings");
 public static final String PREFS = "features";
 @Override public boolean onCreate() { return true; }
 @Override public Bundle call(String method, String arg, Bundle extras) {
  String caller = getCallingPackage();
  if (!"com.oplus.camera".equals(caller) && !"com.heytap.mydevices".equals(caller)
      && !getContext().getPackageName().equals(caller)) throw new SecurityException("Unsupported caller");
  SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  Bundle result = new Bundle();
  if ("read".equals(method)) {
   result.putBoolean("enabled", prefs.getBoolean("enabled", true));
   result.putLong("revision", prefs.getLong("revision", 0));
  } else if ("applied".equals(method) && extras != null) {
   prefs.edit().putLong(caller + ".revision", extras.getLong("revision", -1))
    .putInt(caller + ".version", extras.getInt("version", -1))
    .putString(caller + ".status", extras.getString("status", ""))
    .putLong(caller + ".targetCode", extras.getLong("targetCode", -1))
    .putLong(caller + ".targetUpdate", extras.getLong("targetUpdate", -1))
    .putInt(caller + ".boot", extras.getInt("boot", -1)).apply();
  } else throw new IllegalArgumentException("Unsupported method");
  return result;
 }
 @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
 @Override public String getType(Uri u) { return null; }
 @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
 @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
 @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}