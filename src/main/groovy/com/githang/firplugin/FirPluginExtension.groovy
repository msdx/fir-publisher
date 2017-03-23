package com.githang.firplugin

class FirPluginExtension {
    String icon = ""
    String appName = ""
    String changeLog = ""
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
