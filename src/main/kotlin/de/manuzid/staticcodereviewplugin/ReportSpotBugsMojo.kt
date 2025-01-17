package de.manuzid.staticcodereviewplugin

import de.manuzid.staticcodereviewplugin.model.GitLabAuthenticationConfiguration
import de.manuzid.staticcodereviewplugin.model.GitLabConfiguration
import de.manuzid.staticcodereviewplugin.model.ProxyConfiguration
import de.manuzid.staticcodereviewplugin.model.SpotbugsConfiguration
import de.manuzid.staticcodereviewplugin.service.AnalyseService
import de.manuzid.staticcodereviewplugin.service.GitApiService
import de.manuzid.staticcodereviewplugin.service.GitLabApiServiceImpl
import de.manuzid.staticcodereviewplugin.service.SpotbugsAnalyseServiceImpl
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "report-spotbugs", defaultPhase = LifecyclePhase.VERIFY)
class ReportSpotBugsMojo : AbstractMojo() {

    @Parameter(property = "gitLabUrl", required = false)
    private lateinit var gitLabUrl: String

    @Parameter(property = "projectId", required = false)
    private lateinit var projectId: String

    @Parameter(property = "mergeRequestIid", required = false)
    private lateinit var mergeRequestIid: Integer

    @Parameter(property = "auth.token", required = false)
    private var authToken: String? = null

    @Parameter(property = "auth.username", required = false)
    private var authUsername: String? = null

    @Parameter(property = "auth.password", required = false)
    private var authPassword: String? = null

    @Parameter(property = "proxy.serverAddress", required = false)
    private var proxyServerAddress: String? = null

    @Parameter(property = "proxy.username", required = false)
    private var proxyUsername: String? = null

    @Parameter(property = "proxy.password", required = false)
    private var proxyPassword: String? = null

    @Parameter(property = "applicationSources", defaultValue = "src/main/java")
    private var applicationSourcePath: String = "src/main/java"

    @Parameter(property = "compiledClasses", defaultValue = "classes")
    private var compiledClassPath: String = "classes"

    @Parameter(property = "priorityThresholdLevel", defaultValue = "3")
    private var priorityThresholdLevel: Int = 3

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "static-code-review.skip", defaultValue = "false")
    private var skip: Boolean = false

    override fun execute() {
        if (skip) {
            log.info("Static Code Review has been skipped")
            return
        }

        log.info("Execute Static Code Review Plugin.")

        val gitApiService = gitLabApiServiceImpl()
        val affectedFilePaths = gitApiService.getAffectedFilePaths()
        val mergeRequestMetaData = gitApiService.getMergeRequestMetaData()

        if (affectedFilePaths.isEmpty()) {
            log.info("No files found to analyse.")
            return
        }

        log.debug("Obtain following ${affectedFilePaths.size.bottles("path", "paths")} from remote: $affectedFilePaths.")
        log.info("Start analysing ${affectedFilePaths.size.bottles("file", "files")}.")

        val analyseService = spotbugsAnalyseServiceImpl(affectedFilePaths)
        analyseService.analyse()
        val reportedIssues = analyseService.getReportedIssues()

        if (reportedIssues.isEmpty()) {
            log.info("No bugs found.")
            return
        }

        log.info("Post ${reportedIssues.size.bottles("comment", "comments")} to merge request.")
        gitApiService.commentMergeRequest(reportedIssues, mergeRequestMetaData)
    }

    private fun gitLabApiServiceImpl(): GitApiService {
        val authConfiguration = GitLabAuthenticationConfiguration(authToken, authUsername, authPassword)
        val proxyConfiguration = ProxyConfiguration(proxyServerAddress, proxyUsername, proxyPassword)
        val gitLabConfiguration = GitLabConfiguration(gitLabUrl, authConfiguration, projectId, mergeRequestIid.toInt(),
                proxyConfiguration)

        return GitLabApiServiceImpl(gitLabConfiguration)
    }

    private fun spotbugsAnalyseServiceImpl(filePaths: List<String>): AnalyseService {
        val spotbugsConfiguration = SpotbugsConfiguration(project.artifactId, filePaths, priorityThresholdLevel,
                project.build.directory, applicationSourcePath.removeSurrounding("/"),
                compiledClassPath.removeSurrounding("/"))
        return SpotbugsAnalyseServiceImpl(spotbugsConfiguration)
    }

    private fun Int.bottles(singular: String, plural: String): String = when (this) {
        0 -> "no $singular"
        1 -> "1 $singular"
        else -> "$this $plural"
    }

    private fun String.removeSurrounding(sign: String): String = this.removePrefix(sign).removeSuffix(sign)
}
