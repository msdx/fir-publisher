Fir Publisher
===


发布android apk到fir.im的一个插件。

编译某个flavor的release版本，并发布到fir.im的一个插件。

之前曾使用过官方的`fir-cli`来发布apk，但是发现它执行的似乎是`./gradlew build`，会对所有flavor的debug以及release版本都进行构建，速度较慢，而上传的是最后的一个apk。而我们公司的需求是需要构建连接测试服务器的flavor并上传，由于官方暂未支持（正在研发中），于是决定自己编写gradle脚本来实现，并修改为gradle插件。

使用方式，先在项目根目录的build.gradle中加入以下代码：

```
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.githang:fir:0.1.1'
    }
}
```

然后在app的build.gradle中加入：

```
apply plugin: 'fir'


fir {
    apiToken //fir.im上的apitoken
    bundleId android.defaultConfig.applicationId
    flavor "Test" （如果没有，可不配置此项），仅在上传apk时需要
    appName 你的应用名称，仅在上传apk时需要
    icon 应用图标路径，仅在上传图标时需要
    changeLog "更新日志" // 或者file("日志文件路径")
}
```

然后就可以执行以下任务进行上传：

- `firApk`:上传apk
- `firIcon`: 上传图标，进行配置
- `firAll`: 上传图标以及apk

## 捐赠支持

如果你觉得 fir-publisher 对你有所帮助, 欢迎微信打赏支持作者:smile:

![](http://7xpdix.com1.z0.glb.clouddn.com/wechat.png)
