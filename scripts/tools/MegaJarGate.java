/*
 * MegaJarGate — the DERIVED mega-jar soundness gate (ADR-0044 §7 G2), replacing the hand-maintained
 * ALLOW_ERA_EXCLUSIVE / ALLOW_ERA_EXCLUSIVE_PREFIX allowlist that used to live inline in
 * scripts/build-mega-jar.sh.
 *
 * Why this exists: the shipped StarEnchants jar is a Multi-Release JAR whose BASE tree is the downgraded
 * legacy (v52) classes and whose META-INF/versions/17 tree is the modern (v61) classes; on a given JVM the
 * classloader serves exactly one era's bytecode per class name. That is sound ONLY if a class present in just
 * one era's tree is never reachable from the other era's code — otherwise it loads with its own-era bytecode
 * and calls a shared class with the wrong-era signature (NoSuchMethodError). ADR-0036 asserted this with a
 * hand allowlist of the two era-exclusive singletons plus an integrate/ prefix. ADR-0044 makes it DERIVED:
 * era-exclusivity is read from the overlay tree, module exclusion from the module set, and cross-era
 * unreachability is proven by a constant-pool walk. No allowlist to drift out of date.
 *
 * Deliberately a tiny standalone ASM tool (compiled+run ad-hoc by scripts/mega-jar-gate.sh against a cached
 * ASM jar, mirroring scripts/tools/Jdk8ApiGate.java) so it perturbs neither the Gradle module spine nor the
 * modern build. Plain Java 8 source: compiles under any JDK 8+.
 *
 * Inputs: the modern fat jar (build/), the downgraded legacy fat jar (build-legacy/), the repo root (for the
 * overlay tree + settings module list), and — as they come to exist during the era-erasure migration — the
 * declared same-FQN bindings twins. Proofs P1–P6 below; each fails with the offending class/FQN named.
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
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MegaJarGate {

    /** Internal-name prefixes never subject to the class-set comparison: the JDG shims (legacy-only by design)
     *  and any Multi-Release variant (an input jar carries none, but guard anyway). */
    static final String[] IGNORED_PREFIXES = { "se_jdg/", "META-INF/" };

    static final List<String> errors = new ArrayList<String>();

    // Populated in main().
    static Set<String> modernClasses;   // internal names (no .class), modern fat jar, minus IGNORED_PREFIXES
    static Set<String> legacyClasses;   // internal names, downgraded legacy fat jar, minus IGNORED_PREFIXES
    static Set<String> eraExclusiveModern; // FQNs of classes with a source ONLY under some overlay/modern
    static Set<String> eraExclusiveLegacy; // FQNs of classes with a source ONLY under some overlay/legacy
    static Set<String> moduleRoots;     // package roots that are declared Gradle modules (P3 candidates)
    static Set<String> legacyRoots;     // package roots actually present in the legacy jar
    static Set<String> bindings;        // declared same-FQN bindings twins (internal names)
    static String commandBinding;       // the ONE binding expected to carry the v1_8_R3 era-direction marker (P5)

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: MegaJarGate <modernJar> <legacyJar> <repoRoot> "
                + "[--bindings a/b/C,...] [--modules a,b,...] [--command-binding a/b/C]");
            System.exit(2);
        }
        File modernJar = new File(args[0]);
        File legacyJar = new File(args[1]);
        File repoRoot = new File(args[2]);
        String bindingsArg = "";
        String modulesArg = "";
        commandBinding = "";
        for (int i = 3; i < args.length; i++) {
            if ("--bindings".equals(args[i]) && i + 1 < args.length) bindingsArg = args[++i];
            else if ("--modules".equals(args[i]) && i + 1 < args.length) modulesArg = args[++i];
            else if ("--command-binding".equals(args[i]) && i + 1 < args.length) commandBinding = args[++i].trim();
        }
        for (File f : new File[] { modernJar, legacyJar }) {
            if (!f.isFile()) { System.err.println("FATAL: jar not found: " + f); System.exit(2); }
        }
        if (!repoRoot.isDirectory()) { System.err.println("FATAL: repo root not a directory: " + repoRoot); System.exit(2); }

        modernClasses = classNames(modernJar);
        legacyClasses = classNames(legacyJar);
        legacyRoots = roots(legacyClasses);
        eraExclusiveModern = deriveEraExclusive(repoRoot, "modern");
        eraExclusiveLegacy = deriveEraExclusive(repoRoot, "legacy");
        moduleRoots = modulesArg.isEmpty() ? parseModuleRoots(repoRoot) : csv(modulesArg);
        bindings = csv(bindingsArg);

        // P1 / P3 — every one-era-only class must be an overlay-derived era-exclusive class (or, modern-only,
        // a whole legacy-excluded module). Collect the forbidden sets for P2 as a side effect.
        Set<String> modernOnly = minus(modernClasses, legacyClasses);
        Set<String> legacyOnly = minus(legacyClasses, modernClasses);
        for (String c : modernOnly) {
            if (isEraExclusive(c, eraExclusiveModern)) continue;              // has an overlay/modern source
            if (isLegacyExcludedModule(c)) continue;                          // P3: a whole modern-only module
            fail("UNSOUND merge: '" + c + ".class' exists only in the modern tree but has no overlay/modern source"
                + " and its module is present on legacy — a one-era class must live under overlay/<era>/ (ADR-0044).");
        }
        for (String c : legacyOnly) {
            if (isEraExclusive(c, eraExclusiveLegacy)) continue;
            fail("UNSOUND merge: '" + c + ".class' exists only in the legacy tree but has no overlay/legacy source"
                + " — a one-era class must live under overlay/<era>/ (ADR-0044).");
        }

        // P2 — cross-era unreachability. No modern class may reference a legacy-only FQN, and no legacy class may
        // reference a modern-only FQN (that would load wrong-era bytecode → NoSuchMethodError).
        crossEraScan(modernJar, "modern", legacyOnly, "legacy");
        crossEraScan(legacyJar, "legacy", modernOnly, "modern");

        // P4 — each declared bindings twin must exist in BOTH trees (its versions/17 copy shadows the base one,
        // so each era runs its own wiring).
        for (String b : bindings) {
            boolean inModern = modernClasses.contains(b);
            boolean inLegacy = legacyClasses.contains(b);
            if (!inModern || !inLegacy) {
                fail("bindings '" + b + ".class' missing from " + (inModern ? "the legacy tree" : "versions/17")
                    + " — a same-FQN bindings twin must be present in both eras (ADR-0044 P4).");
            }
        }

        // P5 — the ONE command-carrying binding must fork by era: its base (v52) copy references v1_8_R3 (the 1.8
        // command-map cast) and its versions/17 (v61) copy must not. Generalises the old bootstrap.compat.Commands
        // grep onto whatever binding absorbs the command seam (ADR-0044 #26); absent until that binding exists.
        if (!commandBinding.isEmpty()) {
            if (!modernClasses.contains(commandBinding) || !legacyClasses.contains(commandBinding)) {
                fail("command-binding '" + commandBinding + ".class' absent from one era — cannot verify era direction (ADR-0044 P5).");
            } else {
                boolean legacyRefsV18 = classRefsV18(legacyJar, commandBinding);
                boolean modernRefsV18 = classRefsV18(modernJar, commandBinding);
                if (!legacyRefsV18 || modernRefsV18) {
                    fail("command-binding '" + commandBinding + ".class' era-direction wrong (base→1.8 v1_8_R3="
                        + (legacyRefsV18 ? 1 : 0) + " want 1; versions/17→modern v1_8_R3=" + (modernRefsV18 ? 1 : 0)
                        + " want 0) (ADR-0044 P5).");
                }
            }
        }

        // P6 — resources are single-sourced, so every non-class, non-JDG entry must match by name and SHA-256
        // across the two input jars. A mismatch means a stale input jar or a merge fault (the historical
        // stale-jar trap, made mechanical).
        resourceDiff(modernJar, legacyJar);

        report();
    }

    // ── P1/P3 helpers ─────────────────────────────────────────────────────────────────────────────────
    /** A one-era class is era-exclusive if its own FQN, or its enclosing top-level FQN, has an overlay source. */
    static boolean isEraExclusive(String internalName, Set<String> eraSet) {
        if (eraSet.contains(internalName)) return true;
        int dollar = internalName.indexOf('$');
        return dollar > 0 && eraSet.contains(internalName.substring(0, dollar));
    }

    /** P3: a modern-only class whose package root is a declared module that is wholly absent from the legacy jar. */
    static boolean isLegacyExcludedModule(String internalName) {
        int slash = internalName.indexOf('/');
        if (slash <= 0) return false;
        String root = internalName.substring(0, slash);
        return moduleRoots.contains(root) && !legacyRoots.contains(root);
    }

    // ── P2 — constant-pool cross-era reachability ───────────────────────────────────────────────────────
    static void crossEraScan(File jar, String jarEra, final Set<String> forbidden, String forbiddenEra) throws IOException {
        if (forbidden.isEmpty()) return;
        JarFile jf = new JarFile(jar);
        try {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class")) continue;
                if (ignored(n) || n.equals("module-info.class")) continue;
                final String self = n.substring(0, n.length() - ".class".length());
                final Set<String> refs = new LinkedHashSet<String>();
                new ClassReader(readAll(jf.getInputStream(e)))
                    .accept(new RefCollector(refs), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                for (String r : refs) {
                    if (forbidden.contains(r)) {
                        fail("UNSOUND merge: " + jarEra + " class '" + self + "' references " + forbiddenEra
                            + "-only class '" + r + "' — on the " + jarEra + " JVM that would load wrong-era bytecode"
                            + " → NoSuchMethodError (ADR-0044 P2).");
                    }
                }
            }
        } finally {
            jf.close();
        }
    }

    /** Whether a single class in a jar references any {@code v1_8_R3} type (the era-direction marker for P5). */
    static boolean classRefsV18(File jar, String internalName) throws IOException {
        JarFile jf = new JarFile(jar);
        try {
            JarEntry e = jf.getJarEntry(internalName + ".class");
            if (e == null) return false;
            final Set<String> refs = new LinkedHashSet<String>();
            new ClassReader(readAll(jf.getInputStream(e)))
                .accept(new RefCollector(refs), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (String r : refs) if (r.contains("v1_8_R3")) return true;
            return false;
        } finally {
            jf.close();
        }
    }

    /** Collects every referenced object-type internal name from a class (super/itf, descriptors, instructions,
     *  indy, annotations) — the P2/P5 constant-pool walk, mirroring Jdk8ApiGate's visitor coverage. */
    static final class RefCollector extends ClassVisitor {
        final Set<String> out;
        RefCollector(Set<String> out) { super(Opcodes.ASM9); this.out = out; }

        void type(String internal) {
            if (internal == null || internal.isEmpty() || internal.charAt(0) == '[') { desc(internal); return; }
            out.add(internal);
        }
        void desc(String d) {
            if (d == null || d.isEmpty()) return;
            if (d.charAt(0) == '(') {
                Type mt = Type.getMethodType(d);
                for (Type a : mt.getArgumentTypes()) note(a);
                note(mt.getReturnType());
            } else {
                note(Type.getType(d));
            }
        }
        void note(Type t) {
            if (t == null) return;
            if (t.getSort() == Type.ARRAY) t = t.getElementType();
            if (t.getSort() == Type.OBJECT) out.add(t.getInternalName());
            else if (t.getSort() == Type.METHOD) desc(t.getDescriptor());
        }
        void handle(Handle h) { if (h != null) { out.add(h.getOwner()); desc(h.getDesc()); } }
        void constant(Object o) {
            if (o instanceof Type) note((Type) o);
            else if (o instanceof Handle) handle((Handle) o);
            else if (o instanceof ConstantDynamic) {
                ConstantDynamic cd = (ConstantDynamic) o;
                desc(cd.getDescriptor());
                handle(cd.getBootstrapMethod());
                for (int i = 0; i < cd.getBootstrapMethodArgumentCount(); i++) constant(cd.getBootstrapMethodArgument(i));
            }
        }

        @Override public void visit(int v, int a, String name, String sig, String sup, String[] itf) {
            if (sup != null) type(sup);
            if (itf != null) for (String i : itf) type(i);
        }
        @Override public AnnotationVisitor visitAnnotation(String d, boolean vis) { desc(d); return new AnnScan(); }
        @Override public AnnotationVisitor visitTypeAnnotation(int tr, TypePath tp, String d, boolean vis) { desc(d); return new AnnScan(); }
        @Override public FieldVisitor visitField(int a, String n, String d, String s, Object val) {
            desc(d);
            return new FieldVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String de, boolean vi) { desc(de); return new AnnScan(); }
                @Override public AnnotationVisitor visitTypeAnnotation(int tr, TypePath tp, String de, boolean vi) { desc(de); return new AnnScan(); }
            };
        }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] exc) {
            desc(d);
            if (exc != null) for (String ex : exc) type(ex);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public void visitTypeInsn(int op, String t) { type(t); }
                @Override public void visitFieldInsn(int op, String o, String nm, String de) { type(o); desc(de); }
                @Override public void visitMethodInsn(int op, String o, String nm, String de, boolean it) { type(o); desc(de); }
                @Override public void visitInvokeDynamicInsn(String nm, String de, Handle bsm, Object... ba) {
                    desc(de); handle(bsm); for (Object o : ba) constant(o);
                }
                @Override public void visitLdcInsn(Object c) { constant(c); }
                @Override public void visitMultiANewArrayInsn(String de, int dims) { desc(de); }
                @Override public void visitTryCatchBlock(Label s, Label e, Label h, String t) { if (t != null) type(t); }
                @Override public AnnotationVisitor visitAnnotation(String de, boolean vi) { desc(de); return new AnnScan(); }
                @Override public AnnotationVisitor visitParameterAnnotation(int p, String de, boolean vi) { desc(de); return new AnnScan(); }
                @Override public AnnotationVisitor visitTypeAnnotation(int tr, TypePath tp, String de, boolean vi) { desc(de); return new AnnScan(); }
                @Override public AnnotationVisitor visitInsnAnnotation(int tr, TypePath tp, String de, boolean vi) { desc(de); return new AnnScan(); }
                @Override public AnnotationVisitor visitTryCatchAnnotation(int tr, TypePath tp, String de, boolean vi) { desc(de); return new AnnScan(); }
                @Override public AnnotationVisitor visitLocalVariableAnnotation(int tr, TypePath tp, Label[] st, Label[] en, int[] ix, String de, boolean vi) { desc(de); return new AnnScan(); }
            };
        }

        final class AnnScan extends AnnotationVisitor {
            AnnScan() { super(Opcodes.ASM9); }
            @Override public void visit(String name, Object value) { if (value instanceof Type) note((Type) value); }
            @Override public void visitEnum(String name, String d, String value) { desc(d); }
            @Override public AnnotationVisitor visitAnnotation(String name, String d) { desc(d); return this; }
            @Override public AnnotationVisitor visitArray(String name) { return this; }
        }
    }

    // ── P6 — resource diff ──────────────────────────────────────────────────────────────────────────────
    static void resourceDiff(File modernJar, File legacyJar) throws Exception {
        Map<String, String> m = resourceHashes(modernJar);
        Map<String, String> l = resourceHashes(legacyJar);
        Set<String> names = new java.util.TreeSet<String>();
        names.addAll(m.keySet());
        names.addAll(l.keySet());
        int shown = 0;
        for (String n : names) {
            String mh = m.get(n);
            String lh = l.get(n);
            if (mh == null || lh == null || !mh.equals(lh)) {
                String why = mh == null ? "only in legacy" : lh == null ? "only in modern" : "content differs";
                if (shown++ < 12) {
                    fail("resource divergence: '" + n + "' " + why
                        + " — resources are single-sourced, so a mismatch means a stale input jar or build fault (ADR-0044 P6).");
                }
            }
        }
    }

    static Map<String, String> resourceHashes(File jar) throws Exception {
        Map<String, String> out = new TreeMap<String, String>();
        JarFile jf = new JarFile(jar);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || n.endsWith(".class")) continue;
                if (ignored(n)) continue;                 // se_jdg/, META-INF/ (manifests/descriptors are build metadata)
                sha.reset();
                out.put(n, hex(sha.digest(readAll(jf.getInputStream(e)))));
            }
        } finally {
            jf.close();
        }
        return out;
    }

    // ── derivation from the repo tree ───────────────────────────────────────────────────────────────────
    /** FQNs of classes whose source lives under some se/<module>/overlay/<era> but NOT the sibling era — the
     *  overlay-derived era-exclusive set, replacing the hand ALLOW_ERA_EXCLUSIVE list. */
    static Set<String> deriveEraExclusive(File repoRoot, String era) throws IOException {
        Set<String> thisEra = overlayFqns(repoRoot, era);
        Set<String> otherEra = overlayFqns(repoRoot, era.equals("modern") ? "legacy" : "modern");
        Set<String> out = new LinkedHashSet<String>(thisEra);
        out.removeAll(otherEra);
        return out;
    }

    /** Every {@code se/<module>/overlay/<era>/<pkg>/<Name>.java} → internal FQN {@code <pkg>/<Name>}. */
    static Set<String> overlayFqns(File repoRoot, String era) throws IOException {
        Set<String> out = new LinkedHashSet<String>();
        File se = new File(repoRoot, "se");
        File[] modules = se.listFiles();
        if (modules == null) return out;
        for (File module : modules) {
            File overlay = new File(module, "overlay/" + era);
            if (!overlay.isDirectory()) continue;
            Path base = overlay.toPath();
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    String rel = base.relativize(p).toString();
                    if (!rel.endsWith(".java") || rel.equals("package-info.java")
                        || rel.endsWith("/package-info.java")) continue;
                    out.add(rel.substring(0, rel.length() - ".java".length()).replace(File.separatorChar, '/'));
                }
            }
        }
        return out;
    }

    /** Declared Gradle module package roots, parsed from settings.gradle.kts (hyphens stripped to match the
     *  single-segment package convention, e.g. {@code compat-folia} → {@code compatfolia}). */
    static Set<String> parseModuleRoots(File repoRoot) throws IOException {
        Set<String> out = new LinkedHashSet<String>();
        File settings = new File(repoRoot, "settings.gradle.kts");
        if (!settings.isFile()) return out;
        // Only the quoted module names inside the listOf(...) block; each `include(name)` maps a name to se/<name>.
        Pattern p = Pattern.compile("\"([a-z][a-z0-9-]*)\"");
        boolean inList = false;
        for (String line : Files.readAllLines(settings.toPath())) {
            if (line.contains("listOf(")) inList = true;
            if (!inList) continue;
            if (line.contains(").forEach")) break;
            Matcher mt = p.matcher(line);
            while (mt.find()) out.add(mt.group(1).replace("-", ""));
        }
        return out;
    }

    // ── jar / io helpers ────────────────────────────────────────────────────────────────────────────────
    static boolean ignored(String entryName) {
        for (String p : IGNORED_PREFIXES) if (entryName.startsWith(p)) return true;
        return false;
    }

    static Set<String> classNames(File jar) throws IOException {
        Set<String> out = new LinkedHashSet<String>();
        JarFile jf = new JarFile(jar);
        try {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.equals("module-info.class")) continue;
                if (ignored(n)) continue;
                out.add(n.substring(0, n.length() - ".class".length()));
            }
        } finally {
            jf.close();
        }
        return out;
    }

    static Set<String> roots(Set<String> internalNames) {
        Set<String> out = new LinkedHashSet<String>();
        for (String c : internalNames) {
            int slash = c.indexOf('/');
            if (slash > 0) out.add(c.substring(0, slash));
        }
        return out;
    }

    static Set<String> csv(String s) {
        Set<String> out = new LinkedHashSet<String>();
        if (s == null) return out;
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<String>(a);
        out.removeAll(b);
        return out;
    }

    static void fail(String message) { errors.add(message); }

    static void report() {
        if (errors.isEmpty()) {
            System.out.println("[mega-gate] OK — derived soundness gate passed (P1 class-sets, P2 cross-era refs, "
                + "P3 modules, P4/P5 bindings, P6 resources).");
            System.exit(0);
        }
        System.out.println("[mega-gate] " + errors.size() + " SOUNDNESS VIOLATION(s):");
        for (String e : errors) System.out.println("    FAIL  " + e);
        System.exit(1);
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        return sb.toString();
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

    private MegaJarGate() {}
}
