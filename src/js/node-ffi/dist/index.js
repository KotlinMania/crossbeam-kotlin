// Crossbeam's generated N-API module, shared by JS and WasmJS on Node.js.
// Browsers and standalone WASI cannot load native Node addons.
const fs = require('node:fs');
const path = require('node:path');
const platformKey = process.platform + '-' + process.arch;
const platformDirectory = path.join(__dirname, 'platform', platformKey);
const release = path.join(platformDirectory, 'Release', 'ffi_native.node');
const debug = path.join(platformDirectory, 'Debug', 'ffi_native.node');

// Only fall back to Debug for this exact OS/architecture when Release is absent.
// Never load the build host's unqualified binary on another runtime. An existing
// broken Release binary must surface its original load error.
if (fs.existsSync(release)) {
    module.exports = require(release);
} else if (fs.existsSync(debug)) {
    module.exports = require(debug);
} else {
    throw new Error(
        'Crossbeam N-API addon is missing for ' + platformKey + '. Expected ' +
        release + ' or ' + debug + '. Build it on the target host with ' +
        './gradlew buildNodeFfi or install a package containing that platform.'
    );
}
