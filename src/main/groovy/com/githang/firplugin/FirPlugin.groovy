package com.githang.firplugin

import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.ProductFlavor
import groovy.json.JsonSlurper
import net.dongliu.apk.parser.ApkParser
import net.dongliu.apk.parser.bean.Icon
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class FirPlugin implements Plugin<Project> {
    private static final Logger LOG = Logging.getLogger(FirPlugin.class)

    @Override
    void apply(Project project) {
        LOG.isEnabled(LogLevel.DEBUG)
        project.extensions.create("fir", FirPluginExtension)

        project.afterEvaluate {
            FirPluginExtension fir = project.extensions.findByName("fir") as FirPluginExtension

            if (!fir.upload) {
                LOG.debug "fir.upload is false, skip."
                return
            }

            if (!project.plugins.hasPlugin("com.android.application")) {
                throw new RuntimeException("FirPlugin can only be applied for android application module.")
            }

            project.android.applicationVariants.all { variant ->
                if ("release".equalsIgnoreCase(variant.buildType.name)) {
                    injectFirTask(project, variant, fir)
                }
            }
        }
    }

    void injectFirTask(Project project, ApplicationVariant variant, FirPluginExtension config) {
        ProductFlavor mergedFlavor = variant.mergedFlavor
        String name = variant.flavorName
        String versionName
        if (config.version) {
            versionName = config.version
        } else {
            versionName = mergedFlavor.versionName + (mergedFlavor.versionNameSuffix ? mergedFlavor.versionNameSuffix : "")
        }
        int versionCode = mergedFlavor.versionCode
        String bundleId = mergedFlavor.applicationId
        String changeLog = config.changeLog
        String token = config.apiTokens[name == "" ? "main" : name]

        if (token == null) {
            LOG.debug "Could not found token for the flavor [${name}], skip."
            return
        }

        def firTask = project.tasks.create(name: "fir${name}") << {
            def cert = getCert(bundleId, token)

            // 获取要上传的APK
            File apk = variant.outputs
                    .find { variantOutput -> variantOutput instanceof ApkVariantOutput }
                    .outputFile
            ApkParser apkParser = new ApkParser(apk.absolutePath)
            List<Icon> iconList = apkParser.getIconFiles()
            Icon icon = iconList.sort { it.data.length }.last()
            String appName = apkParser.apkMeta.name
            File iconFile = saveIconFile(icon.data, apk.parent, icon.path.split("/").last())
            if (iconFile == null || !uploadIcon(cert.cert.icon, iconFile)) {
                LOG.error "Upload ${name} icon [${iconFile}] failed."
            }
            LOG.debug("The apk file path is: {}", apk.path)
            if (uploadApk(cert.cert.binary, apk, appName, versionName, versionCode, changeLog)) {
                LOG.debug "Publish apk Successful ^_^"
            } else {
                LOG.error "Publish apk Failed!"
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

    static File saveIconFile(byte[] bytes, String path, String name) {
        try {
            File file = new File("$path/$name")
            FileOutputStream fos = new FileOutputStream(file)
            fos.write(bytes)
            fos.close()
            return file
        } catch (IOException e) {
            e.printStackTrace()
            return null
        }
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
        post.setEntity(entity)
        HttpResponse response = client.execute(post)
        return EntityUtils.toString(response.entity)
    }
}

