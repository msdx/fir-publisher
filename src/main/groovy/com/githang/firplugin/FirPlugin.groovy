package com.githang.firplugin

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
    void apply(Project target) {

        target.extensions.create("fir", FirPluginExtension)

        def config = target.android.defaultConfig

        def cert;

        def firCert = target.tasks.create(name: "firCert") << {
            def fir = target.fir
            println "API_TOKEN:${fir.apiToken}"
            println "BUNDLE_ID:${fir.bundleId}"
            if (fir.bundleId == null || fir.apiToken == null) {
                throw new ProjectConfigurationException("Please config bundleId and apiToken in fir")
            }
            //获取上传凭证
            cert = getCert(fir.bundleId, fir.apiToken)
        }

        def firIcon = target.tasks.create(name: "firIcon") << {
            def fir = target.fir
            if (fir.icon == null || !new File(fir.icon).exists()) {
                throw new FileNotFoundException("The icon [$fir.icon] was not exists!")
            }
            // 上传图标
            uploadIcon(cert.cert.icon, fir.icon, config)
        }

        def firApk = target.tasks.create(name: "firApk") << {
            def fir = target.fir
            // 获取要上传的APK
            def apk = getApkFile(fir.flavor, target)
            if (apk == null) {
                throw new FileNotFoundException("The apk [$apk] was not found!")
            }

            if (fir.appName == null) {
                throw new ProjectConfigurationException("Please config fir.appName")
            }

            String changeLog = fir.changeLog == null ? "" : fir.changeLog
            if (uploadApk(cert.cert.binary, apk, fir.appName, config, changeLog)) {
                println "Publish Successful ^_^"
            } else {
                println "Publish Failed!!"
            }
        }

        def firAll = target.tasks.create(name: "firAll") {}

        target.afterEvaluate {
            String taskName = "assemble${target.fir.flavor}Release"
            firCert.dependsOn taskName
            firIcon.dependsOn firCert
            firApk.dependsOn firCert
            firAll.dependsOn firIcon
            firAll.dependsOn firApk
        }
    }

    static Object getCert(String bundleId, String apiToken) {
        HttpClient client = new DefaultHttpClient()
        HttpPost post = new HttpPost('http://api.fir.im/apps')
        post.setHeader('Content-Type', 'application/json')
        post.setEntity(new StringEntity("{\"type\":\"android\", \"bundle_id\":\"${bundleId}\", \"api_token\":\"${apiToken}\"}"))
        HttpResponse response = client.execute(post)
        return new JsonSlurper().parseText(EntityUtils.toString(response.entity))
    }

    static String getApkFile(String flavor, def project) {
        def apk = null
        project.android.applicationVariants.all { variant ->
            if ((variant.name).equals(flavor ? (flavor + "Release") : "release")) {
                variant.outputs.each { output ->
                    apk = output.outputFile
                }
            }
        }
        return apk
    }

    static boolean uploadIcon(def cert, def iconPath, def config) {
        def params = [
                "key"  : cert.key,
                "token": cert.token,
                "file" : iconPath
        ]
        def response = uploadFile(cert.upload_url, params)
        return new JsonSlurper().parseText(response).is_completed
    }

    static boolean uploadApk(def cert, def apkPath, def name, def config, def changeLog) {
        def params = [key          : cert.key,
                      token        : cert.token,
                      file         : new File(apkPath),
                      "x:name"     : name,
                      "x:version"  : config.versionName,
                      "x:build"    : config.versionCode,
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

class FirPluginExtension {
    String bundleId
    String apiToken
    String icon
    String flavor = ""
    String appName
    String changeLog

    void changeLog(File file) {
        if (file != null && file.exists()) {
            changeLog = file.text
        } else {
            println "changeLog [$file] was null or not exist"
        }
    }

    void changeLog(String log) {
        changeLog = log
    }
}
