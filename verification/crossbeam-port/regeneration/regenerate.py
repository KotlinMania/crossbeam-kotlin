"""Direct native-kexe source/test adoption. No source snapshots or manual algorithm substitutes."""
from pathlib import Path
import argparse
import difflib
import hashlib
import json
import re
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--expected-sha', required=True)
parser.add_argument('--binary', default='../rust-kotlin/build/bin/macosArm64/releaseExecutable/rust-kotlin.kexe')
args = parser.parse_args()
ROOT = Path.cwd()
RECEIPTS = ROOT / 'verification/crossbeam-port/regeneration'
BINARY = (ROOT / args.binary).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(BINARY) == args.expected_sha, 'Unexpected translator binary'
assert subprocess.check_output(['git', '-C', 'tmp/crossbeam', 'rev-parse', 'HEAD'], text=True).strip() == '2920c984290229ab4e0ca0452ef09e48a82063f3'
old = json.loads((RECEIPTS / 'redo-adoption.json').read_text())
owned = {x['adopted'] for x in old.get('adopted_emission_files', [])}
owned.update(x['adopted'] for x in old.get('inline_test_files', []))
# Remove only known previous emitted outputs. These are not backed up or replaced by manual code.
for name in owned:
    f = Path(name)
    if f.exists():
        f.unlink()
records, adopted, inline, repairs = [], [], [], []
pending_repairs = []
rustc = '/opt/homebrew/bin/rustc'
rust_version = subprocess.check_output([rustc, '--version'], text=True).strip()
target_cfg = {}
for target in ['aarch64-apple-darwin', 'wasm32-unknown-unknown']:
    printed = subprocess.check_output([rustc, '--print', 'cfg', '--target', target], text=True)
    target_cfg[target] = [line for line in printed.splitlines() if line.startswith('target_') or line in {'unix', 'windows'}]
(RECEIPTS / 'rust-target-cfg.json').write_text(json.dumps({'rustc': rustc, 'version': rust_version, 'target_facts': target_cfg, 'qualification': 'Only compiler-reported target facts are passed; debug_assertions/panic profile are not silently enabled.'}, indent=2) + '\n')

def narrow_repairs(path, source, text):
    result = text
    operations = []
    pairs = {
        'io.github.kotlinmania.crossbeam.deque.steal.Empty': 'io.github.kotlinmania.crossbeam.deque.Steal.Empty',
        'io.github.kotlinmania.crossbeam.deque.steal.Retry': 'io.github.kotlinmania.crossbeam.deque.Steal.Retry',
        'io.github.kotlinmania.crossbeam.deque.steal.Success': 'io.github.kotlinmania.crossbeam.deque.Steal.Success',
        'io.github.kotlinmania.crossbeam.epoch.Atomic': 'io.github.kotlinmania.crossbeam.epoch.atomic.Atomic',
        'io.github.kotlinmania.crossbeam.epoch.Owned': 'io.github.kotlinmania.crossbeam.epoch.atomic.Owned',
        'io.github.kotlinmania.crossbeam.epoch.Shared': 'io.github.kotlinmania.crossbeam.epoch.atomic.Shared',
        'io.github.kotlinmania.crossbeam.utils.Backoff': 'io.github.kotlinmania.crossbeam.utils.backoff.Backoff',
        'io.github.kotlinmania.crossbeam.utils.CachePadded': 'io.github.kotlinmania.crossbeam.utils.cachepadded.CachePadded',
        'io.github.kotlinmania.crossbeam.utils.atomic.AtomicConsume': 'io.github.kotlinmania.crossbeam.utils.atomic.consume.AtomicConsume',
        'io.github.kotlinmania.crossbeam.utils.crossbeamutils.src.backoff.Backoff': 'io.github.kotlinmania.crossbeam.utils.backoff.Backoff',
        'io.github.kotlinmania.crossbeam.utils.crossbeamutils.src.cachepadded.CachePadded': 'io.github.kotlinmania.crossbeam.utils.cachepadded.CachePadded',
        'io.github.kotlinmania.crossbeam.utils.sync.WaitGroup': 'io.github.kotlinmania.crossbeam.utils.sync.waitgroup.WaitGroup',
        'io.github.kotlinmania.crossbeam.utils.sync.Parker': 'io.github.kotlinmania.crossbeam.utils.sync.parker.Parker',
        'io.github.kotlinmania.crossbeam.utils.sync.Unparker': 'io.github.kotlinmania.crossbeam.utils.sync.parker.Unparker',
        'io.github.kotlinmania.crossbeam.utils.sync.ShardedLock': 'io.github.kotlinmania.crossbeam.utils.sync.shardedlock.ShardedLock',
    }
    lines = []
    for line in result.splitlines(keepends=True):
        if line.startswith('import '):
            for before, after in pairs.items():
                if line.strip() == 'import ' + before:
                    line = 'import ' + after + '\n'
            line = line.replace('io.github.kotlinmania.crossbeam.epoch.crossbeamepoch.src.', 'io.github.kotlinmania.crossbeam.epoch.')
            if line.strip() in {
                'import io.github.kotlinmania.crossbeam.epoch.crossbeamchannel.benchmarks.atomicring.THREADS',
                'import io.github.kotlinmania.crossbeam.epoch.crossbeamchannel.tests.after.recv',
            }:
                line = ''
        lines.append(line)
    result = ''.join(lines)
    if result != text:
        operations.append('Canonical declaration imports; no facade; remove false unrelated channel imports for local names.')
    if source.endswith('crossbeam-deque/src/deque.rs'):
        for function, module in [('pin', 'default'), ('isPinned', 'default'), ('is_pinned', 'default'), ('unprotected', 'guard')]:
            result = result.replace('epoch.' + function + '(', 'io.github.kotlinmania.crossbeam.epoch.' + module + '.' + function + '(')
        operations.append('Canonical epoch free-function namespace placement only.')
    if 'crossbeam-epoch/' in source and 'crossbeam_utils::thread' in Path(source).read_text():
        result = result.replace('import io.github.kotlinmania.crossbeam.utils.thread\n', '')
        result = result.replace('thread.scope(', 'io.github.kotlinmania.crossbeam.utils.thread.scope(')
        operations.append('Canonical source-explicit crossbeam thread scope function placement.')
    if source.endswith('crossbeam-utils/src/sync/wait_group.rs'):
        result = result.replace('public open fun wait()', 'public open fun await()').replace('public fun wait()', 'public fun await()')
        result = result.replace('wg.wait()', 'wg.await()').replace('wg1.wait()', 'wg1.await()')
        operations.append('JVM Object.wait collision: consuming Rust wait method named await; emitted body unchanged.')
    if source.endswith('crossbeam-utils/tests/wait_group.rs'):
        result = result.replace('.wait()', '.await()')
        operations.append('Original emitted test calls follow necessary JVM-safe method name; assertions unchanged.')
    if source.endswith('crossbeam-utils/src/backoff.rs') and not path.name.endswith('InlineTests.kt'):
        # Each edit is tied to a concrete emitted defect/Rust contract. No replacement algorithm.
        result = result.replace('import io.github.kotlinmania.crossbeam.utils.primitive.hint', 'import io.github.kotlinmania.crossbeam.utils.platform.spinLoop\nimport io.github.kotlinmania.crossbeam.utils.platform.yieldThread')
        result = result.replace('import io.github.kotlinmania.crossbeam.primitive.hint', 'import io.github.kotlinmania.crossbeam.utils.platform.spinLoop\nimport io.github.kotlinmania.crossbeam.utils.platform.yieldThread')
        result = result.replace('public class DebugStruct(', 'private class DebugStruct(')
        result = result.replace('public val SPIN_LIMIT: UInt = 6u', 'private const val SPIN_LIMIT: UInt = 6u').replace('public val YIELD_LIMIT: UInt = 10u', 'private const val YIELD_LIMIT: UInt = 10u')
        result = re.sub(r'public (?:open|data) class Backoff\(public var step: Cell<UInt>(?: = Cell<UInt>\(\))?\) \{', 'public class Backoff private constructor(private var step: UInt) {\n    public constructor() : this(0u)\n', result)
        result = result.replace('public open fun', 'public fun')
        result = result.replace('this.step.set(0)', 'this.step = 0u').replace('this.step.set(this.step.get() + 1)', 'this.step += 1u')
        result = result.replace('this.step.get().min(SPIN_LIMIT)', 'minOf(this.step, SPIN_LIMIT)').replace('this.step.get()', 'this.step')
        result = result.replace('Backoff(step = Cell.new(0))', 'Backoff(step = 0u)').replace('hint.spinLoop()', 'spinLoop()').replace('hint.spin_loop()', 'spinLoop()')
        result = result.replace('for (unused in 0 until 1 shl minOf(this.step, SPIN_LIMIT).toInt()) {', 'repeat(1 shl minOf(this.step, SPIN_LIMIT).toInt()) {')
        result = result.replace('for (unused in 0 until 1 shl this.step.toInt()) {', 'repeat(1 shl this.step.toInt()) {')
        fallback = '        } else {\n            for (unused1 in 0 until 1 shl this.step.toInt()) {\n                spinLoop()\n            }\n        }'
        if fallback in result:
            result = result.replace(fallback, '        } else {\n            // The selected Rust std branch yields; emitted no_std fallback is incorrect.\n            yieldThread()\n        }')
        result = result.replace('std.thread.yieldNow()', 'yieldThread()').replace('thread.yieldNow()', 'yieldThread()').replace('std.thread.yield_now()', 'yieldThread()').replace('thread.yield_now()', 'yieldThread()')
        result = result.replace('    public fun fmt(f: StringBuilder) {', '    override fun toString(): String = StringBuilder().also { fmt(it) }.toString()\n\n    public fun fmt(f: StringBuilder) {')
        result = result.replace('field("step", this.step)', 'field("step", "Cell { value: ${this.step} }")')
        result = result.replace(' * Performs exponential backoff in spin loops.', ' * Performs exponential backoff in spin loops.\n *\n * Keep each instance from concurrent use, as with Rust Cell/non-Sync; Kotlin cannot enforce\n * that bound. Retained emitted examples contain unavailable std-runtime references and\n * documentation translation defects, so they do not establish runnable-Kotlin example parity.', 1)
        result = result.replace('In `@noStd` environments, this method is equivalent to [spin].', 'The upstream no_std branch uses [spin]. This build selects std; browser/WasmJS adapters cannot promise OS scheduling or event-loop progress.')
        if 'fun default()' not in result and 'impl Default for Backoff' in Path(source).read_text():
            position = result.rfind('\n    }\n}')
            assert position != -1, 'Unexpected emitted Backoff companion boundary'
            result = result[:position] + '\n        public fun default(): Backoff = Backoff.new()\n' + result[position:]
        operations.append('Backoff bounded Cell<u32> scalar/private visibility, actual spin/yield adapters, literal/range fixes, original std branch, nested Cell Debug rendering and explicit documentation limits. See exact diff.')
    if 'crossbeam-deque/tests/' in source:
        result = result.replace('import io.github.kotlinmania.rand.Rng', 'import io.github.kotlinmania.rand.rngs.threadRng').replace('thread_rng()', 'threadRng()').replace('.gen_range(', '.randomRange(')
        if source.endswith('/tests/steal.rs'):
            result = result.replace('Worker.new_fifo()', 'Worker.new_fifo<Int>()').replace('Worker.new_lifo()', 'Worker.new_lifo<Int>()').replace('Injector.new()', 'Injector.new<Int>()').replace('Success()', 'Success(Unit)')
        operations.append('Original test calls use real Rand Kotlin threadRng/randomRange, canonical Steal variants and explicit Int/Unit types; assertions retained.')
    replay_name = None
    for rust_name, repair_name in [('seq_lock.rs', 'seq_lock'), ('seq_lock_wide.rs', 'seq_lock_wide'), ('deferred.rs', 'deferred')]:
        if source.endswith('/src/atomic/' + rust_name) or source.endswith('crossbeam-epoch/src/' + rust_name):
            replay_name = repair_name + ('-tests' if path.name.endswith('InlineTests.kt') else '-source')
            break
    if not path.name.endswith('InlineTests.kt'):
        for rust_path, repair_name in [('crossbeam-deque/src/deque.rs', 'deque-source'), ('crossbeam-epoch/src/default.rs', 'default-source'), ('crossbeam-utils/src/sync/sharded_lock.rs', 'sharded_lock-source'), ('crossbeam-epoch/src/epoch.rs', 'epoch-source')]:
            if source.endswith(rust_path):
                replay_name = repair_name
                break
    if replay_name:
        # Reapply the reviewed source/test repair, rather than overwrite it on regeneration.
        # A changed generator output must fail the patch instead of silently losing the repair.
        with tempfile.TemporaryDirectory(prefix='crossbeam-replay-', dir=ROOT / 'build') as folder:
            before = Path(folder) / 'generated.kt'
            after = Path(folder) / 'repaired.kt'
            before.write_text(result)
            subprocess.run(['patch', '-s', '-f', '-o', str(after), str(before), str(RECEIPTS / ('replay-' + replay_name + '.patch'))], check=True)
            result = after.read_text()
        operations.append('Replay tested Kotlin atomics/closure and original Rust test repairs; physical closure placement is runtime-selected.')
    if result != text:
        path.write_text(result)
        diff = RECEIPTS / ('current-repair-' + path.stem + '-' + hashlib.sha256(str(path).encode()).hexdigest()[:10] + '.patch')
        diff.write_text(''.join(difflib.unified_diff(text.splitlines(keepends=True), result.splitlines(keepends=True), fromfile='native-kexe-emission:' + str(path), tofile=str(path))))
        repairs.append({'adopted': str(path), 'operations': operations, 'diff': str(diff), 'adopted_sha256': sha(path)})

for crate, name in [('crossbeam-utils', 'utils'), ('crossbeam-epoch', 'epoch'), ('crossbeam-deque', 'deque')]:
    namespace = 'io.github.kotlinmania.crossbeam.' + name
    for source in sorted((Path('tmp/crossbeam') / crate).rglob('*.rs')):
        rel = source.relative_to(Path('tmp/crossbeam') / crate)
        if rel.parts[0] not in {'src', 'tests', 'examples', 'benches'}:
            continue
        section = rel.parts[0]
        parents = rel.parent.parts[1:] if section == 'src' else rel.parent.parts
        package_base = namespace + ('.' + '.'.join(x.replace('_', '') for x in parents) if parents else '')
        if name == 'deque' and rel.as_posix() == 'src/deque.rs':
            package_base = 'io.github.kotlinmania.crossbeam'
        package = package_base if source.name in {'lib.rs', 'mod.rs'} else package_base + '.' + source.stem.replace('_', '')
        filename = ''.join(x[:1].upper() + x[1:] for x in source.stem.split('_')) + '.kt'
        if section == 'tests':
            output_root = Path('src/common/commonTest/kotlin')
        elif section == 'examples':
            output_root = Path('examples/kotlin')
        elif section == 'benches':
            output_root = Path('src/common/commonBenchmark/kotlin')
        elif source.name == 'seq_lock.rs':
            output_root = Path('src/common/seqLock64Main/kotlin')
        elif source.name == 'seq_lock_wide.rs':
            output_root = Path('src/common/seqLock32Main/kotlin')
        else:
            output_root = Path('src/common/commonMain/kotlin')
        test_root = Path('src/common/seqLock64Test/kotlin') if source.name == 'seq_lock.rs' else Path('src/common/seqLock32Test/kotlin') if source.name == 'seq_lock_wide.rs' else Path('src/common/commonTest/kotlin')
        output_dir = output_root / Path(*package.split('.'))
        output_dir.mkdir(parents=True, exist_ok=True)
        test_dir = test_root / Path(*package.split('.'))
        output = output_dir if source.name in {'lib.rs', 'mod.rs'} else output_dir / filename
        expected_test = test_dir / (Path(filename).stem + 'InlineTests.kt')
        if expected_test.exists():
            expected_test.unlink()
        wide = source.name == 'seq_lock_wide.rs'
        command = [str(BINARY), 'translate', '--force', '--raw-abi', '--split-module-roots', '--feature', 'std', '--feature', 'alloc', '--cfg-closed-world', '--target-arch', 'wasm32' if wide else 'aarch64', '--target-os', 'unknown' if wide else 'macos', '--target-family', 'wasm' if wide else 'unix', '--target-vendor', 'unknown' if wide else 'apple', '--target-pointer-width', '32' if wide else '64', '--package', package_base, '--namespace', namespace, '--crate-namespace', 'crossbeam_utils=io.github.kotlinmania.crossbeam.utils', '--crate-namespace', 'crossbeam_epoch=io.github.kotlinmania.crossbeam.epoch', '--crate-namespace', 'crossbeam_deque=io.github.kotlinmania.crossbeam.deque', '--source-root', 'tmp/crossbeam', '--port-lint', 'tests' if section == 'tests' else 'source']
        for fact in target_cfg['wasm32-unknown-unknown' if wide else 'aarch64-apple-darwin']:
            command += ['--cfg', fact]
        if section == 'tests':
            command += ['--cfg', 'test']
        else:
            command += ['--test-output', str(test_dir)]
        command += ['-o', str(output), str(source)] if source.name in {'lib.rs', 'mod.rs'} else [str(source), str(output)]
        process = subprocess.run(command, text=True, capture_output=True)
        log = RECEIPTS / ('current-' + name + '-' + '-'.join(rel.with_suffix('').parts) + '.log')
        log.write_text(process.stdout + process.stderr)
        # Trust only artifacts explicitly reported by this invocation. Never scan a real
        # product package tree: it also contains unrelated/previously generated glue.
        emitted_paths = []
        for line in process.stdout.splitlines():
            match = re.search(r"->\s+(.+?\.kt)(?:;|$)", line)
            if match:
                artifact = Path(match.group(1))
                if artifact.is_absolute():
                    artifact = artifact.relative_to(ROOT)
                if artifact.exists() and artifact != expected_test:
                    emitted_paths.append(artifact)
        outputs = list(dict.fromkeys(emitted_paths))
        if process.returncode:
            # A prior emitted file must not survive as a substitute for a newly rejected input.
            for previous in outputs:
                if previous.is_file() and str(previous) in owned:
                    previous.unlink()
            outputs = []
        record = {'source': str(source), 'source_sha256': sha(source), 'section': section, 'command': command, 'exit_code': process.returncode, 'log': str(log), 'syntactic_rust_test_annotations': len(re.findall(r'^\s*#\[test\]', source.read_text(), re.M)), 'emitted_files': []}
        for path in outputs:
            raw_text = path.read_text()
            entry = {'source': str(source), 'adopted': str(path), 'native_emitted_sha256_before_repairs': sha(path), 'direct_native_output': True}
            adopted.append(entry)
            record['emitted_files'].append(str(path))
            pending_repairs.append((path, str(source), raw_text))
        if expected_test.exists():
            entry = {'source': str(source), 'adopted': str(expected_test), 'native_emitted_sha256_before_repairs': sha(expected_test), 'direct_native_output': True, 'qualified_test_annotations': expected_test.read_text().count('@kotlin.test.Test')}
            inline.append(entry)
            pending_repairs.append((expected_test, str(source), expected_test.read_text()))
        records.append(record)
assert sha(BINARY) == args.expected_sha, 'Translator changed during generation'
# Complete whole-upstream emission before applying any project repairs.
for repair_path, repair_source, repair_text in pending_repairs:
    narrow_repairs(repair_path, repair_source, repair_text)
report = {'status': 'Actual native-kexe output emitted directly into product/test/example/benchmark paths, then only recorded necessary repairs. Compilation/runtime acceptance is not claimed.', 'binary': str(BINARY), 'binary_sha256': args.expected_sha, 'upstream_commit': '2920c984290229ab4e0ca0452ef09e48a82063f3', 'removed_manual_files': old['removed_manual_files'], 'adopted_emission_files': adopted, 'inline_test_files': inline, 'necessary_repairs_after_adoption': repairs, 'emissions': records, 'no_snapshots': True, 'test_limits': 'Only original Rust standalone/inline test emission; no old manual test reuse or invented tests. Rustdoc examples are not automatically emitted as executable tests by this CLI.', 'validation': 'Pending metadata compilation after this generation; all pre-reset passing counts remain historical.'}
(RECEIPTS / 'redo-adoption.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'inputs': len(records), 'directly_adopted_files': len(adopted), 'inline_test_files': len(inline), 'inline_test_annotations': sum(x['qualified_test_annotations'] for x in inline), 'rejected': [x['source'] for x in records if x['exit_code']]}, indent=2))
