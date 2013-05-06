这个是山寨版本！！ 仅仅用来调试 试用而已！！！


 
####宙斯开源，不仅仅是开源技术，更是开源产品  
开发中心，一个文档管理，开发调试的环境，在任务上线前的主要工作区域  
<a href="http://xuhengfei.github.io/assets/images/articles/zeus/snapshot-dev.png" target="_blank"><img src="http://xuhengfei.github.io/assets/images/articles/zeus/snapshot-dev.png" /></a>
调度中心，生产任务的调度环境，当任务调试通过后，在此处配置调度信息进行生产调度  
<a href="http://xuhengfei.github.io/assets/images/articles/zeus/snapshot-schedule.png" target="_blank"><img src="http://xuhengfei.github.io/assets/images/articles/zeus/snapshot-schedule.png" /></a>  

###宙斯运行原理
<a href="http://xuhengfei.github.io/assets/images/articles/zeus/graph-network.png" target="_blank"><img src="http://xuhengfei.github.io/assets/images/articles/zeus/graph-network.png" /></a>  

<a href="http://xuhengfei.github.io/assets/images/articles/zeus/graph-struct.png" target="_blank"><img src="http://xuhengfei.github.io/assets/images/articles/zeus/graph-struct.png" /></a>  

<a href="http://xuhengfei.github.io/assets/images/articles/zeus/graph-workflow.png" target="_blank"><img src="http://xuhengfei.github.io/assets/images/articles/zeus/graph-workflow.png" /></a>  

<a href="http://xuhengfei.github.io/assets/images/articles/zeus/graph-schedule.png" target="_blank"><img src="http://xuhengfei.github.io/assets/images/articles/zeus/graph-schedule.png" /></a>   

###使用指南    
快速启动(Quick Start)：  
1.设置配置项  
在/web/src/main/filter/antx.properties 中对配置项进行设置  
设置完成后，复制到${user.home}/antx.properties处  
2.pom.xml本地jar地址修改  
在/web/pom.xml中修改properties中的local.highcharts  
因为此jar不在maven仓库中，此jar已经在/web/libs/highcharts-1.4.0.jar  
将systemPath路径设置为绝对路径  
3.数据库配置  
zeus数据库:/web/src/main/resources/persistence.xml中对数据库进行配置  
hive元数据库:/web/src/main/resources/templates/hive-site.xml中对Hive metastore数据库进行配置  
4.打包  
mvn package   
打包在/web/target/exploded/zeus-web.war下  
使用tomcat之类容器运行即可  


以上步骤可以保证这个web项目正常启动，如果需要正式上线此项目，还需要配置以下内容：  
1.动态模板配置  
宙斯系统中有很多模板是可以动态修改的，包括以下一些，建议在正式运行之前都配置好  
(1)首页展示内容 启动后参见页面指南  
(2)首页通知内容 启动后参见页面指南  
(3)hive 默认udf函数 com.taobao.zeus.jobs.sub.HiveJob实现TODO内容  

2.登陆系统  
宙斯不包含单独的注册系统  
建议使用单点登陆来实现登陆  
大致原理：   
(1) web.xml添加一个filter，用来跳转到单点登陆系统  
(2) Spring容器中添加一个Bean，实现com.taobao.zeus.web.Login.Filter.SSOLogin接口  

3.配置hadoop相关环境
默认的hadoop-site.xml和hive-site.xml在 /web/src/main/resources/templates下  
修改相应的配置以对应相应的hadoop集群    
服务器安装hadoop和hive客户端，并将相应的配置写入环境变量中
```shell
export HADOOP_HOME=hadoop_home_path
export HADOOP_CONF_DIR=$HADOOP_HOME/conf
export HIVE_HOME=hive_home_path
export HIVE_CONF_DIR=$HIVE_HOME/conf
export HIVE_LIB=${HIVE_HOME}/lib
export HIVE_AUX_JARS_PATH=udf_jar_path
```

4.超级管理员配置  
在com.taobao.zeus.store.Super中进行配置

5.关于浏览器兼容性  
默认只支持webkit内核的浏览器，建议使用chrome  
可以扩大浏览器范围，方法：/web/src/main/java/com/taobao/zeus/web/platform/Platform.gwt.xml 中注释掉 user.agent 这一行  
当然这样会大致打包时间加长(gwt为了兼容不同的浏览器会编译更多的代码，导致打包变慢)  
即便如此，我们也不保证IE等浏览器能够正常使用！  