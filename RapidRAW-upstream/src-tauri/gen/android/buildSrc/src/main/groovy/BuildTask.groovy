import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.apache.tools.ant.taskdefs.condition.Os

class BuildTask extends DefaultTask {
    @Input
    String rootDirRel
    @Input
    String target
    @Input
    Boolean release

    @TaskAction
    void assemble() {
        def executable = "npm"
        try {
            runTauriCli(executable)
        } catch (Exception e) {
            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                def fallbacks = ["${executable}.exe", "${executable}.cmd", "${executable}.bat"]
                def lastException = e
                for (fallback in fallbacks) {
                    try {
                        runTauriCli(fallback)
                        return
                    } catch (Exception fallbackException) {
                        lastException = fallbackException
                    }
                }
                throw lastException
            } else {
                throw e
            }
        }
    }

    void runTauriCli(String execCmd) {
        if (!rootDirRel) throw new GradleException("rootDirRel cannot be null")
        if (!target) throw new GradleException("target cannot be null")
        if (release == null) throw new GradleException("release cannot be null")
        def argsList = ["run", "--", "tauri", "android", "android-studio-script"]

        if (project.logger.isDebugEnabled()) {
            argsList << "-vv"
        } else if (project.logger.isInfoEnabled()) {
            argsList << "-v"
        }
        if (release) {
            argsList << "--release"
        }
        argsList << "--target" << target

        project.exec {
            workingDir = new File(project.projectDir, rootDirRel)
            commandLine(execCmd, *argsList)
        }.assertNormalExitValue()
    }
}
