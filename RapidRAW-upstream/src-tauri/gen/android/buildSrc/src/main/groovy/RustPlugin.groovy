import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class RustExtension {
    String rootDirRel
}

class RustPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def rustExt = project.extensions.create('rust', RustExtension)
        def defaultAbiList = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
        def abiList = project.findProperty('abiList')?.split(',')?.toList() ?: defaultAbiList

        def defaultArchList = ['arm64', 'arm', 'x86', 'x86_64']
        def archList = project.findProperty('archList')?.split(',')?.toList() ?: defaultArchList

        def targetsList = project.findProperty('targetList')?.split(',')?.toList() ?: ['aarch64', 'armv7', 'i686', 'x86_64']

        project.extensions.findByType(ApplicationExtension)?.with {
            flavorDimensions 'abi'
            productFlavors {
                universal {
                    dimension 'abi'
                    ndk {
                        abiFilters.addAll(abiList)
                    }
                }
                defaultArchList.eachWithIndex { arch, index ->
                    "${arch}" {
                        dimension 'abi'
                        ndk {
                            abiFilters.add(defaultAbiList[index])
                        }
                    }
                }
            }
        }

        project.afterEvaluate {
            for (profile in ['debug', 'release']) {
                def profileCapitalized = profile.capitalize()
                def buildTask = project.tasks.maybeCreate("rustBuildUniversal${profileCapitalized}")
                buildTask.group = 'rust'
                buildTask.description = "Build dynamic library in ${profile} mode for all targets"

                try {
                    project.tasks["mergeUniversal${profileCapitalized}JniLibFolders"].dependsOn(buildTask)
                } catch (Exception e) {}

                targetsList.eachWithIndex { targetName, index ->
                    def targetArch = archList[index]
                    def targetArchCapitalized = targetArch.capitalize()
                    def targetBuildTask = project.tasks.maybeCreate("rustBuild${targetArchCapitalized}${profileCapitalized}", BuildTask)
                    targetBuildTask.group = 'rust'
                    targetBuildTask.description = "Build dynamic library in ${profile} mode for ${targetArch}"
                    targetBuildTask.rootDirRel = rustExt.rootDirRel
                    targetBuildTask.target = targetName
                    targetBuildTask.release = (profile == 'release')

                    buildTask.dependsOn(targetBuildTask)
                    try {
                        project.tasks["merge${targetArchCapitalized}${profileCapitalized}JniLibFolders"].dependsOn(targetBuildTask)
                    } catch (Exception e) {}
                }
            }
        }
    }
}