package com.daxiaamu.opluscameraenhance;

/** Shared by the hook entry and UI. No Android/Xposed dependencies: directly testable. */
public final class NativeSupportPolicy {
 public static final String PROPERTY = "ro.oplus.camera.out.flash.support";
 public enum Decision { NATIVE_SUPPORTED, UNKNOWN, ELIGIBLE }
 private NativeSupportPolicy() {}
 public static Decision decide(String raw, boolean readSucceeded) {
  if (!readSucceeded || raw == null) return Decision.UNKNOWN;
  String value = raw.trim();
  if ("true".equalsIgnoreCase(value)) return Decision.NATIVE_SUPPORTED;
  if (!value.isEmpty() && !"false".equalsIgnoreCase(value)) {
   try { if (Integer.decode(value) > 0) return Decision.NATIVE_SUPPORTED; }
   catch (NumberFormatException error) { return Decision.UNKNOWN; }
  }
  return Decision.ELIGIBLE;
 }
}