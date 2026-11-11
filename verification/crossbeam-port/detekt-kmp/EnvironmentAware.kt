package dev.detekt.core.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.pom.PomModel
import com.intellij.pom.tree.TreeAspect
import dev.detekt.core.parser.createCompilerConfiguration
import dev.detekt.parser.DetektPomModel
import dev.detekt.tooling.api.spec.CompilerSpec
import dev.detekt.tooling.api.spec.LoggingSpec
import dev.detekt.tooling.api.spec.ProjectSpec
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.cli.common.config.kotlinSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.friendPaths
import org.jetbrains.kotlin.config.jdkHome
import org.jetbrains.kotlin.config.jvmTarget
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.io.OutputStream
import java.io.PrintStream
import kotlin.io.path.Path

interface EnvironmentAware {
    val languageVersionSettings: LanguageVersionSettings
    val ktFiles: List<KtFile>
}

internal class EnvironmentFacade(projectSpec: ProjectSpec, compilerSpec: CompilerSpec, loggingSpec: LoggingSpec) :
    AutoCloseable,
    EnvironmentAware {

    private val printStream = if (loggingSpec.debug) loggingSpec.errorChannel.asPrintStream() else NullPrintStream

    // This lateinit var can be changed to val if https://github.com/JetBrains/kotlin/pull/5703 is merged
    private lateinit var sourceModules: List<KaSourceModule>

    private val configuration: CompilerConfiguration = createCompilerConfiguration(
        projectSpec.inputPaths.toList(),
        compilerSpec.classpath,
        compilerSpec.apiVersion,
        compilerSpec.languageVersion,
        compilerSpec.jvmTarget,
        compilerSpec.jdkHome,
        compilerSpec.freeCompilerArgs,
        printStream,
    )

    private val disposable: Disposable = Disposer.newDisposable()

    override val languageVersionSettings: LanguageVersionSettings
        get() = configuration.languageVersionSettings

    @OptIn(KaExperimentalApi::class)
    override val ktFiles: List<KtFile>
        get() = sourceModules.flatMap { it.psiRoots }.filterIsInstance<KtFile>()

    init {
        buildStandaloneAnalysisAPISession(disposable) {
            // Required for autocorrect support
            registerProjectService(TreeAspect::class.java)
            registerProjectService(PomModel::class.java, DetektPomModel(project))

            configuration.putIfAbsent(CommonConfigurationKeys.MODULE_NAME, "<no module name provided>")

            buildKtModuleProvider {
                val targetPlatform =
                    JvmPlatforms.jvmPlatformByTargetVersion(configuration.jvmTarget ?: JvmTarget.DEFAULT)
                platform = targetPlatform

                val jdk = configuration.jdkHome?.let { jdkHome ->
                    buildKtSdkModule {
                        addBinaryRootsFromJdkHome(jdkHome.toPath(), isJre = false)
                        platform = targetPlatform
                        libraryName = "jdk"
                    }
                }

                val friends = configuration.friendPaths.takeIf { it.isNotEmpty() }
                    ?.let { paths ->
                        buildKtLibraryModule {
                            platform = targetPlatform
                            paths.forEach { addBinaryRoot(Path(it)) }
                            libraryName = "friendDependencies"
                        }
                    }

                val dependencies = buildKtLibraryModule {
                    platform = targetPlatform
                    addBinaryRoots(configuration.jvmClasspathRoots.map { it.toPath() })
                    libraryName = "regularDependencies"
                }

                // The compiler flags identify the roots of the common source module. Keep
                // those roots separate from platform actuals and express their dependency.
                val commonPaths = compilerSpec.freeCompilerArgs
                    .filter { it.startsWith("-Xcommon-sources=") }
                    .flatMap { it.substringAfter('=').split(',') }
                    .filter { it.isNotBlank() }
                    .map { Path(it).toAbsolutePath().normalize() }
                    .toSet()
                val allRoots = configuration.kotlinSourceRoots.map { Path(it.path).toAbsolutePath().normalize() }
                val commonRoots = allRoots.filter { it in commonPaths }
                val platformRoots = allRoots.filterNot { it in commonPaths }
                val commonModule = if (commonRoots.isNotEmpty()) buildKtSourceModule {
                    addSourceRoots(commonRoots)
                    platform = targetPlatform
                    moduleName = "common"
                    jdk?.let { addRegularDependency(it) }
                    friends?.let {
                        addFriendDependency(it)
                        addRegularDependency(it)
                    }
                    addRegularDependency(dependencies)
                    languageVersionSettings = configuration.languageVersionSettings
                } else null

                val sourceModule = buildKtSourceModule {
                    addSourceRoots(platformRoots)
                    platform = targetPlatform
                    moduleName = "source"

                    jdk?.let { addRegularDependency(it) }
                    friends?.let {
                        // Friend dependencies must also be declared as regular dependencies - https://github.com/JetBrains/kotlin/commit/69cfa0498a76f0c3eec39eb06b5de70a0d06e41a
                        addFriendDependency(it)
                        addRegularDependency(it)
                    }
                    addRegularDependency(dependencies)
                    commonModule?.let { addDependsOnDependency(it) }

                    languageVersionSettings = configuration.languageVersionSettings
                }

                sourceModules = listOfNotNull(commonModule, sourceModule)
                sourceModules.forEach { addModule(it) }
            }
        }
    }

    override fun close() {
        Disposer.dispose(disposable)
    }
}

private object NullPrintStream : PrintStream(
    object : OutputStream() {
        override fun write(b: Int) {
            // no-op
        }
    }
)

private fun Appendable.asPrintStream(): PrintStream {
    val appendable = this
    return if (appendable is PrintStream) {
        appendable
    } else {
        PrintStream(
            object : OutputStream() {
                override fun write(b: Int) {
                    appendable.append(b.toChar())
                }
            }
        )
    }
}
