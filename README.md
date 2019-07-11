[ ![Download](https://api.bintray.com/packages/msdx/maven/FirPublisher/images/download.svg) ](https://bintray.com/msdx/maven/FirPublisher/_latestVersion) 
 
Fir Publisher
===

## 简介

发布android apk到fir.im的一个插件。

编译某个flavor的release版本，并发布到fir.im的一个插件。

之前曾使用过官方的`fir-cli`来发布apk，但是发现它执行的似乎是`./gradlew build`，会对所有flavor的debug以及release版本都进行构建，速度较慢，而上传的是最后的一个apk。而我们公司的需求是需要构建连接测试服务器的flavor并上传，由于官方暂未支持（现已支持），于是决定自己编写gradle脚本来实现，并修改为gradle插件。

## 使用

先在项目根目录的build.gradle中加入以下代码：
```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.githang:fir:0.6.1' // android build plugin 3.0以下版本请使用0.4.3
    }
}
```

然后在app的build.gradle中加入：

```groovy
apply plugin: 'fir'

fir {
    apiToken //fir.im上的apitoken
    changeLog "更新日志" // 或者file("日志文件路径")
    upload true
}
```
在执行assembleRelease时就会进行上传。

### 多productFlavor发布

```groovy
fir {
    apiTokens([flavor1: "your api token1",
               flavor2: "your api token2"])
    changeLog "更新日志" // 或者file("日志文件路径")
    upload true
}

```

## 上传

```shell
./gradlew assembleRelease
```

或

```shell
./gradlew firFlavorName
```

## 捐赠支持

如果你觉得 fir-publisher 对你有所帮助, 欢迎微信打赏支持作者:smile:

![](http://disk.githang.com/wechat.png)
