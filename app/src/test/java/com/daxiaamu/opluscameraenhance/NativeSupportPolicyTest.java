package com.daxiaamu.opluscameraenhance;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.daxiaamu.opluscameraenhance.NativeSupportPolicy.Decision.*;
public class NativeSupportPolicyTest {
 @Test public void nativeSupportNeverHooks() { assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("1",true)); }
 @Test public void futureNativeSupportNeverHooks() { assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("2",true)); }
 @Test public void missingPropertyIsEligibleRegardlessOfBrand() { assertEquals(ELIGIBLE, NativeSupportPolicy.decide("",true)); }
 @Test public void zeroIsEligibleRegardlessOfBrand() { assertEquals(ELIGIBLE, NativeSupportPolicy.decide("0",true)); }
 @Test public void explicitFalseIsEligible() { assertEquals(ELIGIBLE, NativeSupportPolicy.decide("false",true)); }
 @Test public void readFailureNeverHooks() { assertEquals(UNKNOWN, NativeSupportPolicy.decide("0",false)); }
 @Test public void missingReadResultNeverHooks() { assertEquals(UNKNOWN, NativeSupportPolicy.decide(null,true)); }
 @Test public void malformedValueNeverHooks() { assertEquals(UNKNOWN, NativeSupportPolicy.decide("broken",true)); }
 @Test public void booleanAndHexSupportAreConservative() {
  assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("true",true));
  assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide("0x1",true));
 }
 @Test public void whitespaceAndCaseAreNormalized() {
  assertEquals(NATIVE_SUPPORTED, NativeSupportPolicy.decide(" TRUE ",true));
  assertEquals(ELIGIBLE, NativeSupportPolicy.decide(" FALSE ",true));
 }
}
