package com.uc.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

public class ZeusJob {
	public static void main(String[] args){
		
		try {
			String workDir=System.getenv("instance.workDir");
			Configuration conf=new Configuration(false);
			conf.addResource(new Path(workDir+File.separator+"hadoop-site.xml"));
			
			
			FileWriter fw = new FileWriter(new File("/home/qiujw/zeus/logs/zeusjob.log"));
			Thread.sleep(1000);
			fw.write("sleep");
			System.out.println("sleep");
//			System.out.println("sleep");
			Thread.sleep(1000);
			fw.write("sleep");
//			Configuration conf=new Configuration(false);
			String temp = conf.get("a");
			System.out.println("a is:"+temp);
			fw.write("a is:"+temp);
			int time = conf.getInt("sleep",0);
			System.out.println("time is :"+time);
			fw.write("time is :"+time);
			Thread.sleep(time*1000);
			fw.close();
		} catch (InterruptedException e) {
			System.out.println("catch exception");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
