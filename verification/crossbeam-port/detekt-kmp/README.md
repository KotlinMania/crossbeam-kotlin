# Detekt common/platform module repair

The pinned official Detekt 2.0.0-alpha.6 Analysis API builder puts all sources in one JVM module. It does not consume Kotlin common-source roots as a module graph. This produces false same-module expect/actual and missing-expect errors for this project.

The reviewed source copy and patch preserve both common and platform fragments for rule analysis and connect them with a depends-on edge. Both use the JVM-refined platform because these typed Detekt tasks receive JVM libraries, preserving the SDK, regular and friend dependencies. A pure common-platform session with JVM libraries is invalid; no compiler errors are suppressed.

The project tasks compileDetektKmpPatch and detektKmpCore reproduce the patched JAR from the existing official dependency classpath. The original core SHA and version are checked. Dependency caches are not changed. All existing Detekt tasks remain enabled and use the repaired core; the three typed JVM/Android/test tasks pass their common-source inputs. Source-set rule analysis retains its original input sets. No rules, baselines or diagnostic severity settings are weakened.

The analyzer-only diagnostic probe verifies that a matching expect/actual pair has no compiler diagnostic and an intentional wrong return type still produces its compiler error. This is a tool diagnostic, not an upstream Crossbeam test, and is excluded from product suite counts.

See provenance.json, module-graph.patch and diagnostic-proof.json. Validation logs are in build/reports/crossbeam-port/detekt-kmp-repair-validation.log and detekt-kmp-final-gates.log.
