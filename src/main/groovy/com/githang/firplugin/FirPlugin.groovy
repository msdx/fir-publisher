package com.githang.firplugin

import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

class FirPlugin implements Plugin<Project> {

    @Override
    void apply(Project target) {
        target.extensions.create("fir", FirPluginExtension)

        def fir = target.extensions.findByName("fir") as FirPluginExtension
        target.afterEvaluate {
            if (!fir.upload) {
                println "skip fir upload"
                return
            }

            if (!target.plugins.hasPlugin("com.android.application")) {
                throw new RuntimeException("FirPlugin can only be applied for android application module.")
            }

            if (target.android.productFlavors.size() > 0) {
                boolean hasTaskAdded = false;
                target.android.productFlavors.each {
                    if (injectFirTask(target, it.name, fir)) {
                        hasTaskAdded = true;
                    }
                }
                if (!hasTaskAdded) {
                    throw new GradleException("Missing the `apiToken` configuration? " +
                            "\nYou have ${target.android.productFlavors.size()} productFlavors, " +
                            "please try to add `apiTokens` to fir DSL, for example: \n" +
                            "\nfir {\n" +
                            "\n    apiTokens Develop: \"your_api_token\"" +
                            "\n}")
                }
            } else {
                injectFirTask(target, "", fir)
            }
        }
    }

    boolean injectFirTask(Project project, String name, FirPluginExtension config) {
        String key = name == "" ? "main" : name;
        String token = config.apiTokens.get(key)
        if (token == null) {
            return false;
        }

        def firTask = project.tasks.create(name: "fir${name}") << {
            if (config.bundleId == null) {
                throw new ProjectConfigurationException("Please config bundleId in fir DSL")
            }

            if (config.appName == null) {
                throw new ProjectConfigurationException("Please config fir.appName")
            }

            def cert = getCert(config.bundleId, token)

            if (config.icon == null || !new File(config.icon).exists()) {
                System.err.println "The icon [$config.icon] was not exists, skip upload icon!"
            } else {
                def firIconResult = uploadIcon(cert.cert.icon, config.icon)
                if (!firIconResult) {
                    System.err.println "upload ${name} icon failed."
                }
            }

            // 获取要上传的APK
            def apk = getApkFile(name, project)
            if (apk == null) {
                throw new FileNotFoundException("The apk was not found!")
            }

            String changeLog = config.changeLog == null ? "" : config.changeLog
            if (uploadApk(cert.cert.binary, apk, config.appName, project.android.defaultConfig, changeLog)) {
                println "Publish apk Successful ^_^"
            } else {
                System.err.println "Publish apk Failed!"
            }
        }

        project.tasks.getByPath("assemble${name}Release").dependsOn firTask
        firTask.dependsOn project.tasks.getByPath("package${name}Release")
        return true
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

    static boolean uploadIcon(def cert, def iconPath) {
        def params = [
                "key"  : cert.key,
                "token": cert.token,
                "file" : new File(iconPath)
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
    String icon
    String appName
    String changeLog
    boolean upload = false

    Map<String, String> apiTokens;

    void changeLog(File file) {
        if (file != null && file.exists()) {
            changeLog = file.text
        } else {
            println "changeLog [$file] was null or not exists"
        }
    }

    void changeLog(String log) {
        changeLog = log
    }

    public void apiToken(String apiToken) {
        if (apiTokens == null) {
            apiTokens = new HashMap<>()
        }
        apiTokens.put("main", apiToken)
    }
}

