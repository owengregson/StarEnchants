/*
 * Jdk8ApiGate — the §3.4 "closed-world" static gate over the DOWNGRADED legacy jar.
 *
 * Why this exists (docs/legacy-1.8.9-codeshare-design.md, Gate 2 / R1): JvmDowngrader lowers the
 * LANGUAGE (records, sealed, switch-expr, string-concat indy) and shims the COMMON post-8 stdlib calls
 * (List.of, Optional.isEmpty, String.isBlank/strip/repeat, Stream.toList, some NIO). It does NOT shim
 * everything — a stray un-shimmable JDK-9+ API compiles, downgrades and shades GREEN, then
 * NoSuchMethodErrors on a real Java-8 server. The live 1.8 smoke (Gate 4) only drives a reduced,
 * per-EffectKind-FAMILY subset, so a hazard on an unexercised path ships unseen. This gate is the static
 * net for that blind spot: every JDK reference in the downgraded bytecode must resolve against the actual
 * Java-8 API (a real JDK-8 rt.jar), or the build fails.
 *
 * It is deliberately a tiny standalone tool (compiled+run ad-hoc by scripts/jdk8-api-gate.sh against an
 * ad-hoc ASM jar, mirroring how the JDG CLI itself is fetched) so it perturbs neither the Gradle module spine
 * nor the modern build. Plain Java 8 source: compiles under any JDK 8+ (CI's JDK 17, a dev's JDK 25).
 *
 * Coverage: every method/field/type reference in every instruction, plus class super/interfaces, field and
 * method descriptors + exceptions, invokedynamic bootstrap handles/args (incl. ConstantDynamic), and
 * annotation element values (Class literals / enum constants / nested annotations — runtime-reflected on 1.8).
 * It scans BOTH the application classes AND the shaded se_jdg/ JDG shims (those are downgraded v52 bytecode
 * too, with the same un-shimmable-passthrough risk). It does NOT see reflective string-named lookups
 * (Class.forName("…"), MethodHandles.Lookup.find*) — an inherent limit of any bytecode scanner.
 *
 * Severity tiers (the documented hazards are ALL public java/*; the public surface blocks, the JDK-internal
 * member surface — inherently volatile — warns unless --strict-internal):
 *   java/ javax/ org/w3c/ org/xml/sax/ org/ietf/jgss/ org/omg/ org/jcp/   -> HARD (missing class OR member)
 *   jdk/ sun/ com/sun/                                                     -> missing CLASS = HARD;
 *                                                                            missing MEMBER = WARN (--strict-internal => HARD)
 * Everything else (our own roots, server externals org.bukkit/net.minecraft/io.papermc/snakeyaml, any other
 * bundled lib) is not a JDK reference and is never checked. A reference resolved against a bundled copy in the
 * jar-under-test (e.g. a 3rd-party javax.* shaded in) is accepted — that copy is what runs.
 */
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Jdk8ApiGate {

    /** Which JDK namespace tier a referenced owner falls in (drives severity). */
    enum Tier { HARD, INTERNAL, NONE }

    static final class ClassInfo {
        final String superName;
        final String[] interfaces;
        final Set<String> methods = new HashSet<String>(); // name+descriptor
        final Set<String> fields = new HashSet<String>();  // name+descriptor
        ClassInfo(String superName, String[] interfaces) {
            this.superName = superName;
            this.interfaces = interfaces == null ? new String[0] : interfaces;
        }
    }

    static final class Violation {
        final Tier tier;
        final boolean classMiss; // true = missing class, false = missing member on an existing class
        final String key;        // stable, dedup + allowlist key
        final String exampleSrc; // one class that references it
        Violation(Tier tier, boolean classMiss, String key, String exampleSrc) {
            this.tier = tier; this.classMiss = classMiss; this.key = key; this.exampleSrc = exampleSrc;
        }
        boolean isHard(boolean strictInternal) {
            switch (tier) {
                case HARD: return true;                          // public surface: class OR member miss blocks
                case INTERNAL: return classMiss || strictInternal; // internal: class miss blocks, member miss warns
                default: return false;
            }
        }
    }

    static final Map<String, ClassInfo> baseline = new HashMap<String, ClassInfo>();
    static final Map<String, ClassInfo> underTest = new HashMap<String, ClassInfo>();
    static final Map<String, Violation> violations = new LinkedHashMap<String, Violation>();
    static final List<String> allow = new ArrayList<String>();

    static boolean strictInternal = false;
    static int allowlisted = 0;

    // Public JDK surface (a miss here is unambiguous): the java.* core plus the JDK-shipped javax.*/org.* trees.
    static final String[] HARD_PREFIXES =
        { "java/", "javax/", "org/w3c/", "org/xml/sax/", "org/ietf/jgss/", "org/omg/", "org/jcp/" };
    // JDK-internal surface (inherently volatile): a class miss still blocks, a member miss warns by default.
    static final String[] INTERNAL_PREFIXES = { "jdk/", "sun/", "com/sun/" };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: Jdk8ApiGate <jarToCheck> <jdk8Home> [--strict-internal] [--allow <file>]");
            System.exit(2);
        }
        File jar = new File(args[0]);
        File jdk8 = new File(args[1]);
        String allowFile = null;
        for (int i = 2; i < args.length; i++) {
            if ("--strict-internal".equals(args[i])) strictInternal = true;
            else if ("--allow".equals(args[i]) && i + 1 < args.length) allowFile = args[++i];
        }
        if (!jar.isFile()) { System.err.println("FATAL: jar not found: " + jar); System.exit(2); }
        loadAllow(allowFile);

        // 1. Index the real Java-8 API surface from a genuine JDK 8 (rt.jar + the rest of jre/lib + ext + tools.jar).
        //    Collect all candidate dirs unconditionally (collectJars no-ops on a missing dir), covering both the
        //    normal JDK layout (jre/lib, lib/tools.jar) and a JRE-style layout (lib/) — never drop ext.
        List<File> baseJars = new ArrayList<File>();
        collectJars(new File(jdk8, "jre/lib"), baseJars);
        collectJars(new File(jdk8, "jre/lib/ext"), baseJars);
        collectJars(new File(jdk8, "lib"), baseJars);      // JDK tools.jar lives here; also the JRE-style rt.jar
        collectJars(new File(jdk8, "lib/ext"), baseJars);
        if (baseJars.isEmpty()) {
            System.err.println("FATAL: no Java-8 baseline jars under " + jdk8 + " (need a real JDK 8 rt.jar)");
            System.exit(2);
        }
        for (File f : baseJars) index(f, baseline);
        if (baseline.get("java/lang/Object") == null) {
            System.err.println("FATAL: baseline does not contain java.lang.Object — is " + jdk8 + " a JDK 8?");
            System.exit(2);
        }

        // 2. Index the jar under test too, so a class extending/implementing another in-jar type resolves its hierarchy.
        index(jar, underTest);

        // 3. Walk every reference in the downgraded bytecode (application classes AND the se_jdg shims).
        scan(jar);

        // 4. Report + verdict.
        System.exit(report(jar, baseJars.size()));
    }

    // ── indexing ────────────────────────────────────────────────────────────────────────────────────
    static void index(File jarFile, final Map<String, ClassInfo> into) throws IOException {
        JarFile jf = new JarFile(jarFile);
        try {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.equals("module-info.class")) continue;
                // Skip META-INF/ (incl. multi-release versions/<n>/ variants): only the base v52 tree runs on 1.8,
                // and an MR variant would otherwise overwrite a base ClassInfo and corrupt hierarchy resolution.
                if (n.startsWith("META-INF/")) continue;
                byte[] bytes = readAll(jf.getInputStream(e));
                try {
                    new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                        ClassInfo cur;
                        @Override public void visit(int v, int a, String name, String sig, String sup, String[] itf) {
                            cur = new ClassInfo(sup, itf);
                            into.put(name, cur);
                        }
                        @Override public MethodVisitor visitMethod(int a, String mn, String md, String s, String[] x) {
                            if (cur != null) cur.methods.add(mn + md);
                            return null;
                        }
                        @Override public FieldVisitor visitField(int a, String fn, String fd, String s, Object val) {
                            if (cur != null) cur.fields.add(fn + fd);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (Throwable t) {
                    // A class we cannot index just risks a false positive later, not a crash — warn and continue.
                    System.err.println("[jdk8-gate] warn: could not index " + n + " (" + t + ")");
                }
            }
        } finally {
            jf.close();
        }
    }

    // ── scanning the jar under test ───────────────────────────────────────────────────────────────────
    static void scan(File jarFile) throws IOException {
        JarFile jf = new JarFile(jarFile);
        try {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class")) continue;
                if (n.startsWith("META-INF/") || n.equals("module-info.class")) continue;
                byte[] bytes = readAll(jf.getInputStream(e));
                try {
                    new ClassReader(bytes).accept(new ScanClassVisitor(), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (Throwable t) {
                    // An app/shim class we cannot parse cannot be verified — fail loudly rather than skip it silently.
                    record(Tier.HARD, false, "UNPARSEABLE " + n + " — " + t, n);
                }
            }
        } finally {
            jf.close();
        }
    }

    static final class ScanClassVisitor extends ClassVisitor {
        String src = "?";
        ScanClassVisitor() { super(Opcodes.ASM9); }
        @Override public void visit(int v, int a, String name, String sig, String sup, String[] itf) {
            src = name;
            if (sup != null) checkType(sup, src);
            if (itf != null) for (String i : itf) checkType(i, src);
        }
        @Override public AnnotationVisitor visitAnnotation(String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitTypeAnnotation(int tr, TypePath tp, String desc, boolean vis) { return ann(desc, src); }
        @Override public FieldVisitor visitField(int a, final String n, final String d, String s, Object val) {
            noteDesc(d, src);
            final String fsrc = src;
            return new FieldVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String desc, boolean vis) { return ann(desc, fsrc); }
                @Override public AnnotationVisitor visitTypeAnnotation(int tr, TypePath tp, String desc, boolean vis) { return ann(desc, fsrc); }
            };
        }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] exc) {
            noteDesc(d, src);
            if (exc != null) for (String ex : exc) checkType(ex, src);
            return new ScanMethodVisitor(src);
        }
    }

    static final class ScanMethodVisitor extends MethodVisitor {
        final String src;
        ScanMethodVisitor(String src) { super(Opcodes.ASM9); this.src = src; }
        @Override public void visitTypeInsn(int op, String type) { checkTypeOperand(type, src); }
        @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
            noteDesc(desc, src);
            checkMember(owner, name, desc, false, src);
        }
        @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            noteDesc(desc, src);
            checkMember(owner, name, desc, true, src);
        }
        @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            noteDesc(desc, src);
            checkHandle(bsm, src);
            for (Object o : bsmArgs) noteConst(o, src);
        }
        @Override public void visitLdcInsn(Object cst) { noteConst(cst, src); }
        @Override public void visitMultiANewArrayInsn(String desc, int dims) { noteDesc(desc, src); }
        @Override public void visitTryCatchBlock(Label s, Label e, Label h, String type) {
            if (type != null) checkType(type, src);
        }
        @Override public AnnotationVisitor visitAnnotation(String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitParameterAnnotation(int p, String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitTypeAnnotation(int tr, TypePath tp, String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitInsnAnnotation(int tr, TypePath tp, String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitTryCatchAnnotation(int tr, TypePath tp, String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitLocalVariableAnnotation(int tr, TypePath tp, Label[] st, Label[] en, int[] idx, String desc, boolean vis) { return ann(desc, src); }
        @Override public AnnotationVisitor visitAnnotationDefault() { return new AnnScanner(src); }
    }

    /** Scans annotation element values: Class-literal types, enum constants, and nested annotations. */
    static final class AnnScanner extends AnnotationVisitor {
        final String src;
        AnnScanner(String src) { super(Opcodes.ASM9); this.src = src; }
        @Override public void visit(String name, Object value) { if (value instanceof Type) noteType((Type) value, src); }
        @Override public void visitEnum(String name, String desc, String value) { noteDesc(desc, src); }
        @Override public AnnotationVisitor visitAnnotation(String name, String desc) { noteDesc(desc, src); return this; }
        @Override public AnnotationVisitor visitArray(String name) { return this; }
    }

    /** Note an annotation's own type, then return a scanner for its element values. */
    static AnnotationVisitor ann(String desc, String src) {
        noteDesc(desc, src);
        return new AnnScanner(src);
    }

    static void noteConst(Object o, String src) {
        if (o instanceof Type) noteType((Type) o, src);
        else if (o instanceof Handle) checkHandle((Handle) o, src);
        else if (o instanceof ConstantDynamic) {
            ConstantDynamic cd = (ConstantDynamic) o;
            noteDesc(cd.getDescriptor(), src);
            checkHandle(cd.getBootstrapMethod(), src);
            for (int i = 0; i < cd.getBootstrapMethodArgumentCount(); i++) noteConst(cd.getBootstrapMethodArgument(i), src);
        }
    }

    static void checkHandle(Handle h, String src) {
        int tag = h.getTag();
        boolean method = tag == Opcodes.H_INVOKEVIRTUAL || tag == Opcodes.H_INVOKESTATIC
            || tag == Opcodes.H_INVOKESPECIAL || tag == Opcodes.H_NEWINVOKESPECIAL
            || tag == Opcodes.H_INVOKEINTERFACE;
        noteDesc(h.getDesc(), src);
        checkMember(h.getOwner(), h.getName(), h.getDesc(), method, src);
    }

    // ── reference checks ──────────────────────────────────────────────────────────────────────────────
    static void noteDesc(String desc, String src) {
        if (desc == null || desc.isEmpty()) return;
        if (desc.charAt(0) == '(') {
            Type mt = Type.getMethodType(desc);
            for (Type a : mt.getArgumentTypes()) noteType(a, src);
            noteType(mt.getReturnType(), src);
        } else {
            noteType(Type.getType(desc), src);
        }
    }

    static void noteType(Type t, String src) {
        if (t == null) return;
        if (t.getSort() == Type.ARRAY) t = t.getElementType();
        if (t.getSort() == Type.OBJECT) checkType(t.getInternalName(), src);
        else if (t.getSort() == Type.METHOD) noteDesc(t.getDescriptor(), src);
    }

    /** A visitTypeInsn operand: either an internal name or an array descriptor. */
    static void checkTypeOperand(String operand, String src) {
        if (operand == null || operand.isEmpty()) return;
        if (operand.charAt(0) == '[') noteType(Type.getType(operand), src);
        else checkType(operand, src);
    }

    /** Class-existence check for a referenced type. */
    static void checkType(String internalName, String src) {
        Tier tier = tierOf(internalName);
        if (tier == Tier.NONE) return;
        if (baseline.containsKey(internalName) || underTest.containsKey(internalName)) return;
        record(tier, true, "CLASS " + internalName, src);
    }

    /** Member-existence check for a referenced method/field, with full hierarchy resolution. */
    static void checkMember(String owner, String name, String desc, boolean method, String src) {
        if (owner == null) return;
        if (owner.charAt(0) == '[') {
            // Arrays expose java.lang.Object's members; clone() is a synthetic (sometimes covariant) member every
            // array has on every JDK — always Java-8-valid regardless of the descriptor the compiler emitted.
            if ("clone".equals(name)) return;
            owner = "java/lang/Object";
        }
        // Signature-polymorphic methods carry the call-site descriptor, not a declared one — never a real miss.
        if (method && "java/lang/invoke/MethodHandle".equals(owner)
            && ("invoke".equals(name) || "invokeExact".equals(name))) return;
        Tier tier = tierOf(owner);
        if (tier == Tier.NONE) return;
        if (lookup(owner) == null) {
            record(tier, true, "CLASS " + owner, src); // owner class itself absent from Java 8
            return;
        }
        if (resolveMember(owner, name + desc, method, new HashSet<String>())) return;
        record(tier, false, (method ? "METHOD " : "FIELD ") + owner + "#" + name + " " + desc, src);
    }

    static boolean resolveMember(String owner, String key, boolean method, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        ClassInfo ci = lookup(owner);
        if (ci == null) return false; // hierarchy escaped into an unindexed type — inconclusive => not found
        if (method ? ci.methods.contains(key) : ci.fields.contains(key)) return true;
        for (String itf : ci.interfaces) if (resolveMember(itf, key, method, seen)) return true;
        return resolveMember(ci.superName, key, method, seen);
    }

    /**
     * Resolve a class by name. For a JDK (HARD-tier) owner, the real baseline is authoritative — never let a
     * same-named class bundled in the jar-under-test shadow it (that could hide real JDK-8 members). For any
     * other owner, a bundled copy wins, since that copy is what actually runs.
     */
    static ClassInfo lookup(String name) {
        if (tierOf(name) == Tier.HARD) {
            ClassInfo b = baseline.get(name);
            return b != null ? b : underTest.get(name);
        }
        ClassInfo c = underTest.get(name);
        return c != null ? c : baseline.get(name);
    }

    static Tier tierOf(String internalName) {
        if (internalName == null || internalName.isEmpty()) return Tier.NONE;
        for (String p : HARD_PREFIXES) if (internalName.startsWith(p)) return Tier.HARD;
        for (String p : INTERNAL_PREFIXES) if (internalName.startsWith(p)) return Tier.INTERNAL;
        return Tier.NONE;
    }

    static void record(Tier tier, boolean classMiss, String key, String src) {
        // Anchored match: an allow entry must be a PREFIX of the key (so it includes the METHOD/FIELD/CLASS tag and
        // owner, and ideally the descriptor) — never an unanchored substring that could swallow unrelated misses.
        for (String a : allow) {
            if (key.equals(a) || key.startsWith(a)) { allowlisted++; return; }
        }
        if (!violations.containsKey(key)) violations.put(key, new Violation(tier, classMiss, key, src));
    }

    // ── reporting ───────────────────────────────────────────────────────────────────────────────────
    static int report(File jar, int baseJarCount) {
        List<Violation> hard = new ArrayList<Violation>();
        List<Violation> warn = new ArrayList<Violation>();
        for (Violation v : violations.values()) (v.isHard(strictInternal) ? hard : warn).add(v);

        System.out.println("[jdk8-gate] scanned " + jar.getName()
            + " against a Java-8 baseline of " + baseline.size() + " classes (" + baseJarCount + " jars)"
            + (strictInternal ? " [strict-internal]" : ""));
        if (allowlisted > 0) System.out.println("[jdk8-gate] " + allowlisted + " reference(s) allow-listed");

        if (!warn.isEmpty()) {
            System.out.println("[jdk8-gate] " + warn.size() + " WARNING(s) — JDK-internal member absent from Java 8 (not blocking; --strict-internal to enforce):");
            for (Violation v : warn) System.out.println("    WARN  " + v.key + "   (e.g. in " + v.exampleSrc + ")");
        }
        if (!hard.isEmpty()) {
            System.out.println("[jdk8-gate] " + hard.size() + " VIOLATION(s) — referenced API does NOT exist in Java 8:");
            for (Violation v : hard) System.out.println("    FAIL  " + v.key + "   (e.g. in " + v.exampleSrc + ")");
            System.out.println();
            System.out.println("[jdk8-gate] Each FAIL would NoSuchMethodError/NoClassDefFoundError on a real 1.8 server.");
            System.out.println("[jdk8-gate] Fix: use a Java-8-available alternative (or one JvmDowngrader shims), or, if a");
            System.out.println("[jdk8-gate] reference is provably safe, add a matching prefix to scripts/jdk8-api-gate.allow.");
            return 1;
        }
        System.out.println("[jdk8-gate] OK — every JDK reference in the downgraded jar exists in Java 8.");
        return 0;
    }

    // ── io helpers ──────────────────────────────────────────────────────────────────────────────────
    static void collectJars(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) if (f.isFile() && f.getName().endsWith(".jar")) out.add(f);
    }

    static void loadAllow(String allowFile) throws IOException {
        File f = allowFile != null ? new File(allowFile) : new File("scripts/jdk8-api-gate.allow");
        if (!f.isFile()) return;
        for (String line : Files.readAllLines(f.toPath())) {
            String s = line.trim();
            if (s.isEmpty() || s.charAt(0) == '#') continue;
            allow.add(s);
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private Jdk8ApiGate() {}
}
