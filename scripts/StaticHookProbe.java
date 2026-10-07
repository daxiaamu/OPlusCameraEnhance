import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.util.*;
import java.util.zip.*;

/** Offline structural check of the native camera hook contract. Requires the JADX all jar. */
public class StaticHookProbe {
  record Info(Method method, Set<String> strings, Set<String> calls, Set<String> fields) {}
  static final Map<String,Info> methods = new LinkedHashMap<>();
  static final Map<String,String> parents = new HashMap<>();
  static String key(MethodReference m) {
    return m.getDefiningClass()+"->"+m.getName()+"("+String.join("",m.getParameterTypes())+")"+m.getReturnType();
  }
  static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
  static Info unique(List<Info> values,String label) {
    check(values.size()==1,label+": expected one match, found "+values.size());
    System.out.println(label+" = "+key(values.get(0).method)); return values.get(0);
  }
  static boolean shape(Info i,String params,boolean isStatic,String result) {
    return String.join("",i.method.getParameterTypes()).equals(params)
      && ((i.method.getAccessFlags()&8)!=0)==isStatic && i.method.getReturnType().equals(result);
  }
  static Info anchor(String text,String params) {
    return unique(methods.values().stream().filter(i->shape(i,params,false,"V") && i.strings.stream().anyMatch(s->s.contains(text))).toList(),text);
  }
  static boolean view(String type) {
    Set<String> seen=new HashSet<>();
    while(type!=null && seen.add(type)) {
      if(Set.of("Landroid/view/View;","Landroid/view/ViewGroup;","Landroid/widget/LinearLayout;","Landroid/widget/FrameLayout;").contains(type)) return true;
      type=parents.get(type);
    }
    return false;
  }
  public static void main(String[] args) throws Exception {
    try(ZipFile zip=new ZipFile(args[0])) {
      for(var entry:Collections.list(zip.entries())) if(entry.getName().endsWith(".dex")) {
        var dex=new DexBackedDexFile(null,zip.getInputStream(entry).readAllBytes());
        for(ClassDef cls:dex.getClasses()) {
          parents.put(cls.getType(),cls.getSuperclass());
          for(Method m:cls.getMethods()) {
            Set<String> strings=new HashSet<>(),calls=new HashSet<>(),fields=new HashSet<>();
            if(m.getImplementation()!=null) for(var instruction:m.getImplementation().getInstructions()) {
              if(instruction instanceof ReferenceInstruction r) {
                var ref=r.getReference();
                if(ref instanceof StringReference s) strings.add(s.getString());
                if(ref instanceof MethodReference c) calls.add(key(c));
                if(ref instanceof FieldReference f) fields.add(f.getDefiningClass()+"->"+f.getName()+":"+f.getType());
              }
            }
            methods.put(key(m),new Info(m,strings,calls,fields));
          }
        }
      }
    }
    var old=methods.values().stream().filter(i->shape(i,"",false,"V") && i.strings.stream().anyMatch(s->s.contains("expandAnim, debug, mbUseSharedIcon:"))).toList();
    Info expand=old.isEmpty()?unique(methods.values().stream().filter(i->shape(i,"",false,"V") && i.strings.containsAll(Set.of("expandAnim, recover deferred icon back to mStartView","expandAnim, recover attach from mStartView"))).toList(),"OS17 expand"):unique(old,"legacy expand");
    if(expand.strings.contains("pref_app_outflash_key")) System.out.println("UI: stock handles external flash");
    else {
      check(expand.strings.contains("pref_camera_flashmode_key"),"Missing stock flash branch");
      Info collapse=unique(methods.values().stream().filter(i->shape(i,"ZZ",false,"V") && i.method.getDefiningClass().equals(expand.method.getDefiningClass()) && i.strings.stream().anyMatch(s->s.contains("collapseAnimFromCurrentPosition, fromCurrentPosition:"))).toList(),"collapse");
      Info spacing=unique(methods.values().stream().filter(i->shape(i,"ZZ",false,"V") && i.method.getDefiningClass().equals(expand.method.getDefiningClass()) && i.strings.stream().anyMatch(s->s.contains("playCheckListBarTransition, expanded:"))).toList(),"spacing");
      String owner=expand.method.getDefiningClass();
      check(owner.equals(collapse.method.getDefiningClass()) && owner.equals(spacing.method.getDefiningClass()) && view(owner),"UI owner mismatch");
      var shared=expand.calls.stream().filter(collapse.calls::contains).map(methods::get).filter(Objects::nonNull)
        .filter(i->i.method.getDefiningClass().equals(owner)&&shape(i,"Z",false,"V")).toList();
      unique(shared.stream().filter(i->i.calls.stream().anyMatch(s->s.endsWith("->setClickable(Z)V"))).toList(),"siblings");
      unique(shared.stream().filter(i->i.calls.stream().anyMatch(s->s.startsWith("Landroid/animation/ObjectAnimator;->ofFloat(")) && i.calls.contains("Landroid/animation/AnimatorSet;->start()V")).toList(),"icon");
      var fields=spacing.fields.stream().filter(s->s.startsWith(owner+"->")&&view(s.substring(s.indexOf(':')+1))).toList();
      check(fields.size()==1,"Expected unique list View field: "+fields); System.out.println("list = "+fields.get(0));
    }
    Info connect=unique(methods.values().stream().filter(i->shape(i,"ILandroid/app/Activity;Ljava/lang/String;Z",false,"V") &&
      i.strings.containsAll(Set.of("OutFlashServiceListener","OUT_FLASH","intent_blue_tooth_mac_address"))).toList(),"discovery");
    Info registry=unique(methods.values().stream().filter(i->i.method.getName().equals("<clinit>")&&i.strings.contains("key_outflash_connected_device")).toList(),"registry");
    unique(connect.calls.stream().map(methods::get).filter(Objects::nonNull).filter(i->i.method.getDefiningClass().equals(registry.method.getDefiningClass()) && shape(i,"Ljava/lang/String;Ljava/lang/String;",true,"Z")).toList(),"known device");
    unique(methods.values().stream().filter(i->i.strings.contains("ro.oplus.camera.out.flash.support") && i.calls.contains("Lcom/oplus/wrapper/os/SystemProperties;->getInt(Ljava/lang/String;I)I")).toList(),"feature gate");
    System.out.println("PASS: static camera hook contract (not an on-device DexKit/injection test)");
  }
}
