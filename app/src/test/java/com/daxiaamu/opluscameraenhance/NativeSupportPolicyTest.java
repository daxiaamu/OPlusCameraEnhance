package com.daxiaamu.opluscameraenhance;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.daxiaamu.opluscameraenhance.NativeSupportPolicy.Decision.*;
public class NativeSupportPolicyTest {
 @Test public void nativeOppoNeverHooks() { assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("OPPO","OPPO","1",true)); }
 @Test public void spoofedBrandCannotBypassNativeSupport() { assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("OnePlus","OnePlus","1",true)); }
 @Test public void futureNativeOnePlusNeverHooks() { assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("OnePlus","OnePlus","2",true)); }
 @Test public void unsupportedOppoStillSkipped() { assertEquals(UNSUPPORTED_BRAND, NativeSupportPolicy.decide("OPPO","OPPO","",true)); }
 @Test public void missingPropertyOnOnePlusIsEligible() { assertEquals(ELIGIBLE, NativeSupportPolicy.decide("OnePlus","OPPO","",true)); }
 @Test public void explicitZeroIsEligible() { assertEquals(ELIGIBLE, NativeSupportPolicy.decide("oneplus","OnePlus","0",true)); }
 @Test public void readFailureNeverHooks() { assertEquals(UNKNOWN, NativeSupportPolicy.decide("OnePlus","OnePlus","0",false)); }
 @Test public void malformedValueNeverHooks() { assertEquals(UNKNOWN, NativeSupportPolicy.decide("OnePlus","OnePlus","broken",true)); }
 @Test public void unrelatedBrandNeverHooks() { assertEquals(UNSUPPORTED_BRAND, NativeSupportPolicy.decide("other","other","0",true)); }
 @Test public void booleanAndHexSupportAreConservative() {
  assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("OnePlus","OnePlus","true",true));
  assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("OnePlus","OnePlus","0x1",true));
 }
}