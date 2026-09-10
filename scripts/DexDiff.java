import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;

import java.io.File;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Names every host method whose body differs between a clean APK and a patched one, and prints
 * the register evidence for each: how many registers the method declares before and after, and
 * every instruction the two bodies do not share.
 *
 * <p>The point of it is the last line of the report. A patch that writes into a register the
 * method never declared assembles happily and only fails when a device verifies the class, so
 * every instruction the patched body added is checked against the register count of the method
 * it landed in. The highest register travels with the rendered line rather than being read back
 * out of the text, because this app has string constants that read like register names.
 *
 *   java -cp <cli jar> DexDiff.java <cleanApk> <patchedApk> <reportFile>
 *
 * <p>Run by scripts/verify-injected-registers.ps1, which also exercises the same output through
 * a real ART verifier when a device is attached.
 */
public class DexDiff {

    /** Signature -> "registerCount:bodyHash", for every method of an APK. */
    private static Map<String, String> fingerprintAll(File apk) throws Exception {
        Map<String, String> out = new HashMap<>(1 << 20);
        MultiDexContainer<? extends DexFile> container =
                DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String entry : container.getDexEntryNames()) {
            for (ClassDef cd : container.getEntry(entry).getDexFile().getClasses()) {
                for (Method m : cd.getMethods()) {
                    MethodImplementation impl = m.getImplementation();
                    StringBuilder body = new StringBuilder();
                    int registers = 0;
                    if (impl != null) {
                        registers = impl.getRegisterCount();
                        for (Instruction i : impl.getInstructions()) body.append(render(i)).append('\n');
                    }
                    digest.reset();
                    byte[] hash = digest.digest(body.toString().getBytes("UTF-8"));
                    StringBuilder hex = new StringBuilder();
                    for (int k = 0; k < 8; k++) hex.append(String.format("%02x", hash[k]));
                    out.put(sig(cd, m), registers + ":" + hex);
                }
            }
        }
        return out;
    }

    /** Signature -> rendered body, for the named methods only. */
    private static Map<String, List<String>> bodiesOf(File apk, Set<String> wanted) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        MultiDexContainer<? extends DexFile> container =
                DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String entry : container.getDexEntryNames()) {
            for (ClassDef cd : container.getEntry(entry).getDexFile().getClasses()) {
                for (Method m : cd.getMethods()) {
                    String s = sig(cd, m);
                    if (!wanted.contains(s)) continue;
                    List<String> body = new ArrayList<>();
                    MethodImplementation impl = m.getImplementation();
                    if (impl != null) {
                        body.add("# registers=" + impl.getRegisterCount());
                        for (Instruction i : impl.getInstructions()) body.add(render(i));
                    }
                    out.put(s, body);
                }
            }
        }
        return out;
    }

    private static String sig(ClassDef cd, Method m) {
        StringBuilder b = new StringBuilder(cd.getType()).append("->").append(m.getName()).append('(');
        for (CharSequence p : m.getParameterTypes()) b.append(p);
        return b.append(')').append(m.getReturnType()).toString();
    }

    /** One instruction as text: opcode, the registers it names, its literal and its reference. */
    private static String render(Instruction i) {
        StringBuilder b = new StringBuilder(i.getOpcode().name);
        List<String> regs = new ArrayList<>();
        int maxReg = -1;
        if (i instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction r = (RegisterRangeInstruction) i;
            regs.add("v" + r.getStartRegister() + "..v" + (r.getStartRegister() + r.getRegisterCount() - 1));
            maxReg = r.getStartRegister() + r.getRegisterCount() - 1;
        } else if (i instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction r = (FiveRegisterInstruction) i;
            int n = r.getRegisterCount();
            int[] all = { r.getRegisterC(), r.getRegisterD(), r.getRegisterE(), r.getRegisterF(), r.getRegisterG() };
            for (int k = 0; k < n; k++) { regs.add("v" + all[k]); maxReg = Math.max(maxReg, all[k]); }
        } else if (i instanceof ThreeRegisterInstruction) {
            ThreeRegisterInstruction r = (ThreeRegisterInstruction) i;
            regs.add("v" + r.getRegisterA());
            regs.add("v" + r.getRegisterB());
            regs.add("v" + r.getRegisterC());
            maxReg = Math.max(r.getRegisterA(), Math.max(r.getRegisterB(), r.getRegisterC()));
        } else if (i instanceof TwoRegisterInstruction) {
            TwoRegisterInstruction r = (TwoRegisterInstruction) i;
            regs.add("v" + r.getRegisterA());
            regs.add("v" + r.getRegisterB());
            maxReg = Math.max(r.getRegisterA(), r.getRegisterB());
        } else if (i instanceof OneRegisterInstruction) {
            regs.add("v" + ((OneRegisterInstruction) i).getRegisterA());
            maxReg = ((OneRegisterInstruction) i).getRegisterA();
        }
        if (!regs.isEmpty()) b.append(' ').append(String.join(", ", regs));
        if (i instanceof WideLiteralInstruction) {
            b.append(", #").append(((WideLiteralInstruction) i).getWideLiteral());
        }
        if (i instanceof ReferenceInstruction) {
            Reference r = ((ReferenceInstruction) i).getReference();
            if (r != null) b.append(", ").append(r);
        }
        return b.append(" |maxreg=").append(maxReg).toString();
    }

    public static void main(String[] args) throws Exception {
        File clean = new File(args[0]);
        File patched = new File(args[1]);
        PrintWriter report = new PrintWriter(args[2], "UTF-8");

        System.out.println("[diff] fingerprinting clean " + clean.getName());
        Map<String, String> before = fingerprintAll(clean);
        System.out.println("[diff] " + before.size() + " methods");
        System.out.println("[diff] fingerprinting patched " + patched.getName());
        Map<String, String> after = fingerprintAll(patched);
        System.out.println("[diff] " + after.size() + " methods");

        Set<String> changed = new TreeSet<>();
        Set<String> added = new TreeSet<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            String was = before.get(e.getKey());
            if (was == null) added.add(e.getKey());
            else if (!was.equals(e.getValue())) changed.add(e.getKey());
        }
        Set<String> removed = new TreeSet<>();
        for (String k : before.keySet()) if (!after.containsKey(k)) removed.add(k);

        // Everything Hushfeed ships of its own lives under app/morphe; anything else that appeared
        // is the host's own code moving and is worth seeing.
        int ownAdded = 0;
        for (String s : added) if (s.startsWith("Lapp/morphe/")) ownAdded++;

        System.out.println("[diff] host methods changed: " + changed.size());
        System.out.println("[diff] methods added: " + added.size() + " (" + ownAdded + " under Lapp/morphe/)");
        System.out.println("[diff] methods removed: " + removed.size());

        System.out.println("[diff] reading both bodies for the changed methods");
        Map<String, List<String>> beforeBodies = bodiesOf(clean, changed);
        Map<String, List<String>> afterBodies = bodiesOf(patched, changed);

        report.println("Host methods whose body changed between the clean and the patched APK.");
        report.println("clean:   " + clean.getAbsolutePath());
        report.println("patched: " + patched.getAbsolutePath());
        report.println("changed=" + changed.size() + " added=" + added.size()
                + " (own=" + ownAdded + ") removed=" + removed.size());
        report.println();

        int overRegister = 0;
        for (String s : changed) {
            List<String> b = beforeBodies.getOrDefault(s, List.of());
            List<String> a = afterBodies.getOrDefault(s, List.of());
            int regsBefore = registersOf(b), regsAfter = registersOf(a);
            report.println("==== " + s);
            report.println("     registers " + regsBefore + " -> " + regsAfter
                    + ", instructions " + Math.max(0, b.size() - 1) + " -> " + Math.max(0, a.size() - 1));
            // Only the lines the patched body has that the clean one does not, which for an
            // injected call is the call itself and whatever moves its result about.
            List<String> onlyAfter = minus(a, b);
            List<String> onlyBefore = minus(b, a);
            for (String line : onlyBefore) report.println("  -  " + line);
            for (String line : onlyAfter) {
                int high = highestRegister(line);
                boolean bad = high >= 0 && regsAfter > 0 && high >= regsAfter;
                if (bad) overRegister++;
                report.println("  +  " + line + (bad ? "   <<< REGISTER >= registerCount" : ""));
            }
            report.println();
        }
        report.println("Injected lines naming a register at or above the method's register count: " + overRegister);
        report.close();
        System.out.println("[diff] injected lines naming an out-of-range register: " + overRegister);
        System.out.println("[diff] report written to " + args[2]);
    }

    private static int registersOf(List<String> body) {
        if (body.isEmpty() || !body.get(0).startsWith("# registers=")) return 0;
        return Integer.parseInt(body.get(0).substring("# registers=".length()));
    }

    /** Lines of a that are not in b, counting duplicates. */
    private static List<String> minus(List<String> a, List<String> b) {
        Map<String, Integer> pool = new HashMap<>();
        for (String s : b) pool.merge(s, 1, Integer::sum);
        List<String> out = new ArrayList<>();
        for (String s : a) {
            Integer left = pool.get(s);
            if (left != null && left > 0) pool.put(s, left - 1);
            else if (!s.startsWith("# registers=")) out.add(s);
        }
        return out;
    }

    /** The highest register the instruction named, read back from the marker render() wrote. */
    private static int highestRegister(String line) {
        int at = line.lastIndexOf("|maxreg=");
        if (at < 0) return -1;
        return Integer.parseInt(line.substring(at + "|maxreg=".length()).trim());
    }
}
