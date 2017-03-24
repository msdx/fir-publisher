package com.githang.firplugin

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import groovy.io.FileType
import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

class FirPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.extensions.create("fir", FirPluginExtension)

        project.afterEvaluate {
            FirPluginExtension fir = project.extensions.findByName("fir") as FirPluginExtension

            if (!fir.upload) {
                println "fir.upload is false, skip."
                return
            }

            if (!project.plugins.hasPlugin("com.android.application")) {
                throw new RuntimeException("FirPlugin can only be applied for android application module.")
            }

            project.android.applicationVariants.each { variant ->
                if ("debug".equalsIgnoreCase(variant.buildType.name)) {
                    return
                }

                injectFirTask(project, variant, fir)
            }
        }
    }

    void injectFirTask(Project project, ApplicationVariant variant, FirPluginExtension config) {
        String name = variant.flavorName
        String versionName = variant.mergedFlavor.versionName
        int versionCode = variant.mergedFlavor.versionCode
        String bundleId = variant.mergedFlavor.applicationId
        String changeLog = config.changeLog
        String token = config.apiTokens[name]

        if (token == null) {
            println "Could not found token for the flavor [${name}], skip."
            return
        }

        def firTask = project.tasks.create(name: "fir${name}") << {
            def cert = getCert(bundleId, token)

            BaseVariantOutput output = variant.outputs.last()
            File manifestFile = output.processManifest.manifestOutputFile
            File resDir = output.processResources.resDir
            def manifestXml = new XmlSlurper().parse(manifestFile)

            def iconFile = getIconFile(resDir, manifestXml)
            def firIconResult = uploadIcon(cert.cert.icon, iconFile)
            if (!firIconResult) {
                System.err.println "Upload ${name} icon [${iconFile}] failed."
            }

            def appName = getAppName(resDir, manifestXml)

            // 获取要上传的APK
            File apk = variant.outputs.last().outputFile

            if (uploadApk(cert.cert.binary, apk, appName, versionName, versionCode, changeLog)) {
                println "Publish apk Successful ^_^"
            } else {
                System.err.println "Publish apk Failed!"
            }
        }

        project.tasks.getByPath("assemble${name}Release").dependsOn firTask
        firTask.dependsOn project.tasks.getByPath("package${name}Release")
    }

    static Object getCert(String bundleId, String apiToken) {
        HttpClient client = new DefaultHttpClient()
        HttpPost post = new HttpPost('http://api.fir.im/apps')
        post.setHeader('Content-Type', 'application/json')
        post.setEntity(new StringEntity("{\"type\":\"android\", \"bundle_id\":\"${bundleId}\", \"api_token\":\"${apiToken}\"}"))
        HttpResponse response = client.execute(post)
        return new JsonSlurper().parseText(EntityUtils.toString(response.entity))
    }

    static File getIconFile(File resDir, def manifestXml) {
        def iconValue = manifestXml?.application?.@'android:icon'?.text() - '@'
        def (type, iconName) = iconValue?.split('/')

        File iconFile
        int xCount = 0;
        resDir.eachDirMatch(~"^${type}.*") { dir ->
            dir.eachFileMatch(FileType.FILES, ~"${iconName}.*") { file ->
                int currentXCount = dir.name.count('x')
                if (currentXCount < xCount) {
                    return
                }
                xCount = currentXCount
                iconFile = file
            }
        }
        return iconFile
    }

    static String getAppName(File resDir, def manifestXml) {
        def labelValue = manifestXml?.application?.@'android:label'?.text() - '@string/'
        File valuesFile = new File(resDir, 'values/values.xml')
        def values = new XmlSlurper().parse(valuesFile)
        return values.depthFirst().findAll({
            it.name() == 'string' && it.@'name'?.text() == labelValue
        })?.first()
    }

    static boolean uploadIcon(def cert, File iconFile) {
        def params = [
                "key"  : cert.key,
                "token": cert.token,
                "file" : iconFile
        ]
        def response = uploadFile(cert.upload_url, params)
        return new JsonSlurper().parseText(response).is_completed
    }

    static boolean uploadApk(
            def cert, File apkFile, def name, String versionName, def versionCode, def changeLog) {
        def params = [key          : cert.key,
                      token        : cert.token,
                      file         : apkFile,
                      "x:name"     : name,
                      "x:version"  : versionName,
                      "x:build"    : versionCode,
                      "x:changelog": changeLog
        ]
        String response = uploadFile(cert.upload_url, params)
        return new JsonSlurper().parseText(response).is_completed
    }

    static String uploadFile(def url, HashMap<String, Object> params) {
        HttpClient client = new DefaultHttpClient()
        HttpPost post = new HttpPost(url)
        SimpleMultipartEntity entity = new SimpleMultipartEntity()

        String fileKey
        File fileValue
        params.each { key, value ->
            if (value instanceof File) {
                fileKey = key
                fileValue = value
            } else {
                entity.addPart(key, value as String)
            }
        }
        if (fileKey && fileValue) {
            entity.addPart(fileKey, fileValue, true)
        }
        post.setEntity(entity);
        HttpResponse response = client.execute(post);
        return EntityUtils.toString(response.entity)
    }
}

