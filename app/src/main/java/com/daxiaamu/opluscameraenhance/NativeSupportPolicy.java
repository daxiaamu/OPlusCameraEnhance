package com.daxiaamu.opluscameraenhance;

/** Shared by the hook entry and UI. No Android/Xposed dependencies: directly testable. */
public final class NativeSupportPolicy {
 public static final String PROPERTY = "ro.oplus.camera.out.flash.support";
 public enum Decision { NATIVE_SUPPORTED, UNSUPPORTED_BRAND, UNKNOWN, ELIGIBLE }
 private NativeSupportPolicy() {}
 public static Decision decide(String brand, String manufacturer, String raw, boolean readSucceeded) {
  if (!readSucceeded || raw == null) return Decision.UNKNOWN;
  String value = raw.trim();
  if ("true".equalsIgnoreCase(value)) return Decision.NATIVE_SUPPORTED;
  if (!value.isEmpty() && !"false".equalsIgnoreCase(value)) {
   try { if (Integer.decode(value) > 0) return Decision.NATIVE_SUPPORTED; }
   catch (NumberFormatException error) { return Decision.UNKNOWN; }
  }
  if (!"OnePlus".equalsIgnoreCase(brand) && !"OnePlus".equalsIgnoreCase(manufacturer))
   return Decision.UNSUPPORTED_BRAND;
  return Decision.ELIGIBLE;
 }
}