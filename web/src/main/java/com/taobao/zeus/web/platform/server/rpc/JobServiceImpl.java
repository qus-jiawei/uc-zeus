package com.taobao.zeus.web.platform.server.rpc;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONObject;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.sencha.gxt.data.shared.loader.PagingLoadConfig;
import com.sencha.gxt.data.shared.loader.PagingLoadResult;
import com.sencha.gxt.data.shared.loader.PagingLoadResultBean;
import com.taobao.zeus.client.ZeusException;
import com.taobao.zeus.model.JobDescriptor;
import com.taobao.zeus.model.JobDescriptor.JobRunType;
import com.taobao.zeus.model.JobDescriptor.JobScheduleType;
import com.taobao.zeus.model.JobHistory;
import com.taobao.zeus.model.JobStatus;
import com.taobao.zeus.model.JobStatus.Status;
import com.taobao.zeus.model.JobStatus.TriggerType;
import com.taobao.zeus.model.ZeusFollow;
import com.taobao.zeus.model.processer.Processer;
import com.taobao.zeus.socket.protocol.Protocol.ExecuteKind;
import com.taobao.zeus.socket.worker.ClientWorker;
import com.taobao.zeus.store.FollowManager;
import com.taobao.zeus.store.GroupBean;
import com.taobao.zeus.store.JobBean;
import com.taobao.zeus.store.JobHistoryManager;
import com.taobao.zeus.store.PermissionManager;
import com.taobao.zeus.store.UserManager;
import com.taobao.zeus.store.mysql.ReadOnlyGroupManager;
import com.taobao.zeus.store.mysql.persistence.ZeusUser;
import com.taobao.zeus.store.mysql.tool.ProcesserUtil;
import com.taobao.zeus.util.Tuple;
import com.taobao.zeus.web.LoginUser;
import com.taobao.zeus.web.PermissionGroupManager;
import com.taobao.zeus.web.platform.client.module.jobdisplay.job.JobHistoryModel;
import com.taobao.zeus.web.platform.client.module.jobmanager.JobModel;
import com.taobao.zeus.web.platform.client.util.GwtException;
import com.taobao.zeus.web.platform.client.util.ZUser;
import com.taobao.zeus.web.platform.shared.rpc.JobService;

public class JobServiceImpl implements JobService{
	private static Logger log=LogManager.getLogger(JobServiceImpl.class);
	@Autowired
	private PermissionGroupManager permissionGroupManager;
	@Autowired
	private ReadOnlyGroupManager readOnlyGroupManager;
	@Autowired
	private JobHistoryManager jobHistoryManager;
	@Autowired
	private UserManager userManager;
	@Autowired
	private FollowManager followManager;
	@Autowired
	private PermissionManager permissionManager;
	@Autowired
	private ClientWorker worker;
	@Override
	public JobModel createJob(String jobName, String parentGroupId, String jobType)
			throws GwtException {
		JobRunType type=null;
		if(JobModel.MapReduce.equals(jobType)){
			type=JobRunType.MapReduce;
		}else if(JobModel.SHELL.equals(jobType)){
			type=JobRunType.Shell;
		}else if(JobModel.HIVE.equals(jobType)){
			type=JobRunType.Hive;
		}
		try {
			JobDescriptor jd=permissionGroupManager.createJob(LoginUser.getUser().getUid(), jobName, parentGroupId,type);
			return getUpstreamJob(jd.getId());
		} catch (ZeusException e) {
			log.error(e);
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public JobModel getUpstreamJob(String jobId) throws GwtException {
		JobBean jobBean=permissionGroupManager.getUpstreamJobBean(jobId);
		JobModel jobModel=new JobModel();
		
		jobModel.setCronExpression(jobBean.getJobDescriptor().getCronExpression());
		jobModel.setDependencies(jobBean.getJobDescriptor().getDependencies());
		jobModel.setDesc(jobBean.getJobDescriptor().getDesc());
		jobModel.setGroupId(jobBean.getJobDescriptor().getGroupId());
		jobModel.setId(jobBean.getJobDescriptor().getId());
		String jobRunType=null;
		if(jobBean.getJobDescriptor().getJobType()==JobRunType.MapReduce){
			jobRunType=JobModel.MapReduce;
		}else if(jobBean.getJobDescriptor().getJobType()==JobRunType.Shell){
			jobRunType=JobModel.SHELL;
		}else if(jobBean.getJobDescriptor().getJobType()==JobRunType.Hive){
			jobRunType=JobModel.HIVE;
		}
		jobModel.setJobRunType(jobRunType);
		String jobScheduleType=null;
		if(jobBean.getJobDescriptor().getScheduleType()==JobScheduleType.Dependent){
			jobScheduleType=JobModel.DEPEND_JOB;
		}else if(jobBean.getJobDescriptor().getScheduleType()==JobScheduleType.Independent){
			jobScheduleType=JobModel.INDEPEN_JOB;
		}
		jobModel.setJobScheduleType(jobScheduleType);
		jobModel.setLocalProperties(jobBean.getJobDescriptor().getProperties());
		jobModel.setName(jobBean.getJobDescriptor().getName());
		jobModel.setOwner(jobBean.getJobDescriptor().getOwner());
		String ownerName=userManager.findByUid(jobModel.getOwner()).getName();
		if(ownerName==null || "".equals(ownerName.trim()) || "null".equals(ownerName)){
			ownerName=jobModel.getOwner();
		}
		jobModel.setOwnerName(ownerName);
		jobModel.setLocalResources(jobBean.getJobDescriptor().getResources());
		jobModel.setAllProperties(jobBean.getHierarchyProperties().getAllProperties());
		jobModel.setAllResources(jobBean.getHierarchyResources());
		 
		jobModel.setAuto(jobBean.getJobDescriptor().getAuto());
		jobModel.setScript(jobBean.getJobDescriptor().getScript());
		
		List<String> preList=new ArrayList<String>();
		if(!jobBean.getJobDescriptor().getPreProcessers().isEmpty()){
			for(Processer p:jobBean.getJobDescriptor().getPreProcessers()){
				JSONObject o=new JSONObject();
				o.put("id", p.getId());
				o.put("config", p.getConfig());
				preList.add(o.toString());
			}
		}
		jobModel.setPreProcessers(preList);
		
		List<String> postList=new ArrayList<String>();
		if(!jobBean.getJobDescriptor().getPostProcessers().isEmpty()){
			for(Processer p:jobBean.getJobDescriptor().getPostProcessers()){
				JSONObject o=new JSONObject();
				o.put("id", p.getId());
				o.put("config", p.getConfig());
				postList.add(o.toString());
			}
		}
		jobModel.setPostProcessers(postList);
		
		jobModel.setAdmin(permissionGroupManager.hasJobPermission(LoginUser.getUser().getUid(), jobId));
		
		List<ZeusFollow> follows=followManager.findJobFollowers(jobId);
		if(follows!=null){
			List<String> followNames=new ArrayList<String>();
			for(ZeusFollow zf:follows){
				String name=userManager.findByUid(zf.getUid()).getName();
				if(name==null || "".equals(name.trim())){
					name=zf.getUid();
				}
				followNames.add(name);
			}
			jobModel.setFollows(followNames);
		}
		List<String> ladmins=permissionManager.getJobAdmins(jobId);
		List<String> admins=new ArrayList<String>();
		for(String s:ladmins){
			String name=userManager.findByUid(s).getName();
			if(name==null || "".equals(name.trim()) || "null".equals(name)){
				name=s;
			}
			admins.add(name);
		}
		jobModel.setAdmins(admins);
		
		List<String> owners=new ArrayList<String>();
		owners.add(jobBean.getJobDescriptor().getOwner());
		GroupBean parent=jobBean.getGroupBean();
		while(parent!=null){
			if(!owners.contains(parent.getGroupDescriptor().getOwner())){
				owners.add(parent.getGroupDescriptor().getOwner());
			}
			parent=parent.getParentGroupBean();
		}
		jobModel.setOwners(owners);
		
		
		//所有secret. 开头的配置项都进行权限控制
		for(String key:jobModel.getAllProperties().keySet()){
			boolean isLocal=jobModel.getLocalProperties().get(key)==null?false:true;
			if(key.startsWith("secret.")){
				if(!isLocal){
					jobModel.getAllProperties().put(key, "*");
				}else{
					if(!jobModel.getAdmin() && !jobModel.getOwner().equals(LoginUser.getUser().getUid())){
						jobModel.getLocalProperties().put(key, "*");
					}
				}
			}
		}
		//本地配置项中的hadoop.hadoop.job.ugi 只有管理员和owner才能查看，继承配置项不能查看
		String SecretKey="hadoop.hadoop.job.ugi";
		if(jobModel.getLocalProperties().containsKey(SecretKey)){
			String value=jobModel.getLocalProperties().get(SecretKey);
			if(value.lastIndexOf("#")==-1){
				value="*";
			}else{
				value=value.substring(0, value.lastIndexOf("#"));
				value+="#*";
			}
			if(!jobModel.getAdmin() && !jobModel.getOwner().equals(LoginUser.getUser().getUid())){
				jobModel.getLocalProperties().put(SecretKey, value);
			}
			jobModel.getAllProperties().put(SecretKey, value);
		}else if(jobModel.getAllProperties().containsKey(SecretKey)){
			String value=jobModel.getAllProperties().get(SecretKey);
			if(value.lastIndexOf("#")==-1){
				value="*";
			}else{
				value=value.substring(0, value.lastIndexOf("#"));
				value+="#*";
			}
			jobModel.getAllProperties().put(SecretKey, value);
		}
		//如果zeus.secret.script=true 并且没有权限，对script进行加密处理
		if("true".equalsIgnoreCase(jobModel.getAllProperties().get("zeus.secret.script"))){
			if(!jobModel.getAdmin() && !jobModel.getOwner().equals(LoginUser.getUser().getUid())){
				jobModel.setScript("脚本已加密,如需查看请联系相关负责人分配权限");
			}
		}
		
		return jobModel;
	}
	
	
	@Override
	public JobModel updateJob(JobModel jobModel) throws GwtException {
		JobDescriptor jd=new JobDescriptor();
		jd.setCronExpression(jobModel.getCronExpression());
		jd.setDependencies(jobModel.getDependencies());
		jd.setDesc(jobModel.getDesc());
		jd.setGroupId(jobModel.getGroupId());
		jd.setId(jobModel.getId());
		JobRunType type=null;
		if(jobModel.getJobRunType().equals(JobModel.MapReduce)){
			type=JobRunType.MapReduce;
		}else if(jobModel.getJobRunType().equals(JobModel.SHELL)){
			type=JobRunType.Shell;
		}else if(jobModel.getJobRunType().equals(JobModel.HIVE)){
			type=JobRunType.Hive;
		}
		jd.setJobType(type);
		JobScheduleType scheduleType=null;
		if(JobModel.DEPEND_JOB.equals(jobModel.getJobScheduleType())){
			scheduleType=JobScheduleType.Dependent;
		}else if(JobModel.INDEPEN_JOB.equals(jobModel.getJobScheduleType())){
			scheduleType=JobScheduleType.Independent;
		}
		jd.setName(jobModel.getName());
		jd.setOwner(jobModel.getOwner());
		jd.setResources(jobModel.getLocalResources());
		jd.setProperties(jobModel.getLocalProperties());
		jd.setScheduleType(scheduleType);
		jd.setScript(jobModel.getScript());
		jd.setAuto(jobModel.getAuto());
		
		jd.setPreProcessers(parseProcessers(jobModel.getPreProcessers()));
		jd.setPostProcessers(parseProcessers(jobModel.getPostProcessers()));
		try {
			permissionGroupManager.updateJob(LoginUser.getUser().getUid(), jd);
			return getUpstreamJob(jd.getId());
		} catch (ZeusException e) {
			log.error(e);
			throw new GwtException(e.getMessage());
		}
	}
	
	private List<Processer> parseProcessers(List<String> ps){
		List<Processer> list=new ArrayList<Processer>();
		for(String s:ps){
			Processer p=ProcesserUtil.parse(JSONObject.fromObject(s));
			if(p!=null){
				list.add(p);
			}
		}
		return list;
	}
	@Override
	public void switchAuto(String jobId,Boolean auto) throws GwtException {
		Tuple<JobDescriptor, JobStatus> job=permissionGroupManager.getJobDescriptor(jobId);
		JobDescriptor jd=job.getX();
		if(!auto.equals(jd.getAuto())){
			jd.setAuto(auto);
			try {
				permissionGroupManager.updateJob(LoginUser.getUser().getUid(), jd);
			} catch (ZeusException e) {
				log.error(e);
				throw new GwtException(e.getMessage());
			}
		}
	}
	@Override
	public void run(String jobId, int type) throws GwtException {
		TriggerType triggerType=null;
		if(type==1){
			triggerType=TriggerType.MANUAL;
		}else if(type==2){
			triggerType=TriggerType.MANUAL_RECOVER;
		}
		if(!permissionManager.hasJobPermission(LoginUser.getUser().getUid(), jobId)){
			GwtException e=new GwtException("你没有权限执行该操作"); 
			log.error(e);
			throw e;
		}
		JobHistory history=new JobHistory();
		history.setJobId(jobId);
		history.setTriggerType(triggerType);
		history.setOperator(LoginUser.getUser().getUid());
		history.setIllustrate("触发人："+LoginUser.getUser().getUid());
		history.setStatus(Status.RUNNING);
		jobHistoryManager.addJobHistory(history);
		ExecuteKind kind=null;
		if(triggerType==TriggerType.MANUAL){
			kind=ExecuteKind.ManualKind;
		}else if(triggerType==TriggerType.MANUAL_RECOVER){
			kind=ExecuteKind.ScheduleKind;
		}
		try {
			worker.executeJobFromWeb(kind, history.getId());
		} catch (Exception e) {
			log.error("error",e);
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public void cancel(String historyId) throws GwtException {
		JobHistory history=jobHistoryManager.findJobHistory(historyId);
		if(!permissionManager.hasJobPermission(LoginUser.getUser().getUid(), history.getJobId())){
			throw new GwtException("你没有权限执行该操作");
		}
		ExecuteKind kind=null;
		if(history.getTriggerType()==TriggerType.MANUAL){
			kind=ExecuteKind.ManualKind;
		}else{
			kind=ExecuteKind.ScheduleKind;
		}
		try {
			worker.cancelJobFromWeb(kind, historyId,LoginUser.getUser().getUid());
		} catch (Exception e) {
			log.error("error",e);
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public JobHistoryModel getJobHistory(String id) {
		JobHistory his=jobHistoryManager.findJobHistory(id);
		JobHistoryModel d=new JobHistoryModel();
		d.setId(his.getId());
		d.setJobId(his.getJobId());
		d.setStartTime(his.getStartTime());
		d.setEndTime(his.getEndTime());
		d.setExecuteHost(his.getExecuteHost());
		d.setStatus(his.getStatus()==null?null:his.getStatus().toString());
		String type="";
		if(his.getTriggerType()!=null){
			if(his.getTriggerType()==TriggerType.MANUAL){
				type="手动调度";
			}else if(his.getTriggerType()==TriggerType.MANUAL_RECOVER){
				type="手动恢复";
			}else if(his.getTriggerType()==TriggerType.SCHEDULE){
				type="自动调度";
			}
		}
		d.setTriggerType(type);
		d.setIllustrate(his.getIllustrate());
		d.setLog(his.getLog().getContent());
		return d;
	}
	@Override
	public PagingLoadResult<JobHistoryModel> jobHistoryPaging(String jobId,PagingLoadConfig config) {
		List<JobHistory> list=jobHistoryManager.pagingList(jobId, config.getOffset(), config.getLimit());
		int total =jobHistoryManager.pagingTotal(jobId);
		
		List<JobHistoryModel> data=new ArrayList<JobHistoryModel>();
		for(JobHistory his:list){
			JobHistoryModel d=new JobHistoryModel();
			d.setId( his.getId());
			d.setJobId(his.getJobId());
			d.setStartTime(his.getStartTime());
			d.setEndTime(his.getEndTime());
			d.setExecuteHost(his.getExecuteHost());
			d.setOperator(his.getOperator());
			d.setStatus(his.getStatus()==null?null:his.getStatus().toString());
			String type="";
			if(his.getTriggerType()!=null){
				if(his.getTriggerType()==TriggerType.MANUAL){
					type="手动调度";
				}else if(his.getTriggerType()==TriggerType.MANUAL_RECOVER){
					type="手动恢复";
				}else if(his.getTriggerType()==TriggerType.SCHEDULE){
					type="自动调度";
				}
			}
			d.setTriggerType(type);
			d.setIllustrate(his.getIllustrate());
			data.add(d);
		} 
		
		return new PagingLoadResultBean<JobHistoryModel>(data,total,config.getOffset());
	}
	@Override
	public JobModel getJobStatus(String jobId) {
		JobStatus jobStatus=permissionGroupManager.getJobStatus(jobId);
		if(jobStatus==null){
			return null;
		}
		JobModel model=new JobModel();
		model.setId(jobStatus.getJobId());
		model.setReadyDependencies(jobStatus.getReadyDependency());
		model.setStatus(jobStatus.getStatus()==null?"":jobStatus.getStatus().getId());
		return model;
	}
	@Override
	public PagingLoadResult<JobModel> getSubJobStatus(String groupId,PagingLoadConfig config) {
		int start=config.getOffset();
		int limit=config.getLimit();
		GroupBean gb=permissionGroupManager.getDownstreamGroupBean(groupId);
		Map<String, JobBean> map=gb.getAllSubJobBeans();
		
		List<Tuple<JobDescriptor, JobStatus>> allJobs=new ArrayList<Tuple<JobDescriptor, JobStatus>>();
		for(String key:map.keySet()){
			Tuple<JobDescriptor, JobStatus> tuple=new Tuple<JobDescriptor, JobStatus>(map.get(key).getJobDescriptor(), map.get(key).getJobStatus());
			allJobs.add(tuple);
		}
		//按名次排序
		Collections.sort(allJobs, new Comparator<Tuple<JobDescriptor, JobStatus>>() {
			@Override
			public int compare(Tuple<JobDescriptor, JobStatus> o1,
					Tuple<JobDescriptor, JobStatus> o2) {
				return o1.getX().getName().compareToIgnoreCase(o2.getX().getName());
			}
		});
		
		int total=allJobs.size();
		if(start>=allJobs.size()){
			start=0;
		}
		allJobs=allJobs.subList(start, Math.min(start+limit, allJobs.size()));
		
		
		List<String> jobIds=new ArrayList<String>();
		for(Tuple<JobDescriptor, JobStatus> tuple:allJobs){
			jobIds.add(tuple.getX().getId());
		}
		Map<String,JobHistory> jobHisMap=jobHistoryManager.findLastHistoryByList(jobIds);
		List<JobModel> result=new ArrayList<JobModel>();
		for(Tuple<JobDescriptor, JobStatus> job:allJobs){
			JobStatus status=job.getY();
			JobDescriptor jd=job.getX();
			JobModel model=new JobModel();
			model.setId(status.getJobId());
			Map<String, String> dep=new HashMap<String, String>();
			for(String jobId:job.getX().getDependencies()){
				if(jobId!=null && !"".equals(jobId)){
					dep.put(jobId, null);
				}
			}
			dep.putAll(status.getReadyDependency());
			model.setReadyDependencies(dep);
			model.setStatus(status.getStatus()==null?null:status.getStatus().getId());
			model.setName(jd.getName());
			model.setAuto(jd.getAuto());
			JobHistory his=jobHisMap.get(jd.getId());
			if(his!=null && his.getStartTime()!=null){
				SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm:ss");
				model.setLastStatus(format.format(his.getStartTime())+" "+(his.getStatus()==null?"":his.getStatus().getId()));
			}
			result.add(model);
		}
		
		return new PagingLoadResultBean<JobModel>(result,total,start);
	}
	@Override
	public void deleteJob(String jobId) throws GwtException {
		try {
			permissionGroupManager.deleteJob(LoginUser.getUser().getUid(), jobId);
		} catch (ZeusException e) {
			log.error("删除Job失败",e);
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public void addJobAdmin(String jobId, String uid) throws GwtException {
		try {
			permissionGroupManager.addJobAdmin(LoginUser.getUser().getUid(),uid, jobId);
		} catch (ZeusException e) {
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public List<ZUser> getJobAdmins(String jobId) {
		List<ZeusUser> users= permissionGroupManager.getJobAdmins(jobId);
		List<ZUser> result=new ArrayList<ZUser>();
		for(ZeusUser zu:users){
			ZUser z=new ZUser();
			z.setName(zu.getName());
			z.setUid(zu.getUid());
			result.add(z);
		}
		return result;
	}
	@Override
	public void removeJobAdmin(String jobId, String uid)throws GwtException {
		try {
			permissionGroupManager.removeJobAdmin(LoginUser.getUser().getUid(),uid, jobId);
		} catch (ZeusException e) {
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public void transferOwner(String jobId, String uid) throws GwtException {
		try {
			permissionGroupManager.grantJobOwner(LoginUser.getUser().getUid(), uid, jobId);
		} catch (ZeusException e) {
			throw new GwtException(e.getMessage());
		}
	}
//	@Override
//	public List<JobHistoryModel> getRunningJobs(String groupId) {
//		List<JobHistoryModel> result=new ArrayList<JobHistoryModel>();
//		GroupBean gb=permissionGroupManager.getDownstreamGroupBean(groupId);
//		Map<String, JobBean> beans=gb.getAllSubJobBeans();
//		List<JobStatus> jobs=new ArrayList<JobStatus>();
//		for(String key:beans.keySet()){
//			if(beans.get(key).getJobStatus().getStatus()==Status.RUNNING){
//				jobs.add(beans.get(key).getJobStatus());
//			}
//		}
//		List<JobHistory> hiss=new ArrayList<JobHistory>();
//		for(JobStatus js:jobs){
//			if(js.getHistoryId()==null){
//				hiss.add(jobHistoryManager.findLastHistoryByList(Arrays.asList(js.getJobId())).get(js.getJobId()));
//			}else{
//				hiss.add(jobHistoryManager.findJobHistory(js.getHistoryId()));				
//			}
//		}
//		for(JobHistory his:hiss){
//			JobHistoryModel d=new JobHistoryModel();
//			d.setId(his.getId());
//			d.setName(beans.get(his.getJobId()).getJobDescriptor().getName());
//			d.setOwner(beans.get(his.getJobId()).getJobDescriptor().getOwner());
//			d.setJobId(his.getJobId());
//			d.setStartTime(his.getStartTime());
//			d.setEndTime(his.getEndTime());
//			d.setExecuteHost(his.getExecuteHost());
//			d.setStatus(his.getStatus()==null?null:his.getStatus().toString());
//			String type="";
//			if(his.getTriggerType()!=null){
//				if(his.getTriggerType()==TriggerType.MANUAL){
//					type="手动调度";
//				}else if(his.getTriggerType()==TriggerType.MANUAL_RECOVER){
//					type="手动恢复";
//				}else if(his.getTriggerType()==TriggerType.SCHEDULE){
//					type="自动调度";
//				}
//			}
//			d.setTriggerType(type);
//			d.setIllustrate(his.getIllustrate());
//			result.add(d);
//		}
//		
//		return result;
//	}
//	@Override
//	public List<JobHistoryModel> getManualRunningJobs(String groupId) {
//		GroupBean gb=null;
//		GroupBean globe=readOnlyGroupManager.getGlobeGroupBean();
//		if(globe.getGroupDescriptor().getId().equals(groupId)){
//			gb=globe;
//		}else{
//			gb=globe.getAllSubGroupBeans().get(groupId);
//		}
//		Set<String> jobs=gb.getAllSubJobBeans().keySet();
//		List<JobHistory> list=jobHistoryManager.findRecentRunningHistory();
//		for(Iterator<JobHistory> it=list.iterator();it.hasNext();){
//			JobHistory j=it.next();
//			if(j.getStatus()==Status.SUCCESS || j.getStatus()==Status.FAILED){
//				it.remove();
//				continue;
//			}
//			if(j.getTriggerType()!=TriggerType.MANUAL){
//				it.remove();
//				continue;
//			}
//			if(!jobs.contains(j.getJobId())){
//				it.remove();
//				continue;
//			}
//		}
//		List<JobHistoryModel> result=new ArrayList<JobHistoryModel>();
//		for(JobHistory his:list){
//			JobHistoryModel d=new JobHistoryModel();
//			d.setId(his.getId());
//			d.setName(gb.getAllSubJobBeans().get(his.getJobId()).getJobDescriptor().getName());
//			d.setOwner(gb.getAllSubJobBeans().get(his.getJobId()).getJobDescriptor().getOwner());
//			d.setJobId(his.getJobId());
//			d.setStartTime(his.getStartTime());
//			d.setEndTime(his.getEndTime());
//			d.setExecuteHost(his.getExecuteHost());
//			d.setStatus(his.getStatus()==null?null:his.getStatus().toString());
//			String type="";
//			if(his.getTriggerType()!=null){
//				if(his.getTriggerType()==TriggerType.MANUAL){
//					type="手动调度";
//				}else if(his.getTriggerType()==TriggerType.MANUAL_RECOVER){
//					type="手动恢复";
//				}else if(his.getTriggerType()==TriggerType.SCHEDULE){
//					type="自动调度";
//				}
//			}
//			d.setTriggerType(type);
//			d.setIllustrate(his.getIllustrate());
//			result.add(d);
//		}
//		return result;
//	}
	@Override
	public List<JobHistoryModel> getAutoRunning(String groupId) {
		List<JobHistoryModel> result=new ArrayList<JobHistoryModel>();
		GroupBean globe=readOnlyGroupManager.getGlobeGroupBean();
		GroupBean gb=null;
		if(globe.getGroupDescriptor().getId().equals(groupId)){
			gb=globe;
		}else{
			gb=globe.getAllSubGroupBeans().get(groupId);
		}
		Map<String, JobBean> beans=gb.getAllSubJobBeans();
		List<JobStatus> jobs=new ArrayList<JobStatus>();
		for(String key:beans.keySet()){
			if(beans.get(key).getJobStatus().getStatus()==Status.RUNNING){
				jobs.add(beans.get(key).getJobStatus());
			}
		}
		List<JobHistory> hiss=new ArrayList<JobHistory>();
		for(JobStatus js:jobs){
			if(js.getHistoryId()==null){
				hiss.add(jobHistoryManager.findLastHistoryByList(Arrays.asList(js.getJobId())).get(js.getJobId()));
			}else{
				hiss.add(jobHistoryManager.findJobHistory(js.getHistoryId()));				
			}
		}
		for(JobHistory his:hiss){
			JobHistoryModel d=new JobHistoryModel();
			d.setId(his.getId());
			d.setName(gb.getAllSubJobBeans().get(his.getJobId()).getJobDescriptor().getName());
			d.setOwner(gb.getAllSubJobBeans().get(his.getJobId()).getJobDescriptor().getOwner());
			d.setJobId(his.getJobId());
			d.setStartTime(his.getStartTime());
			d.setEndTime(his.getEndTime());
			d.setExecuteHost(his.getExecuteHost());
			d.setStatus(his.getStatus()==null?null:his.getStatus().toString());
			String type="";
			if(his.getTriggerType()!=null){
				if(his.getTriggerType()==TriggerType.MANUAL){
					type="手动调度";
				}else if(his.getTriggerType()==TriggerType.MANUAL_RECOVER){
					type="手动恢复";
				}else if(his.getTriggerType()==TriggerType.SCHEDULE){
					type="自动调度";
				}
			}
			d.setTriggerType(type);
			d.setIllustrate(his.getIllustrate());
			result.add(d);
		}
		
		return result;
	}
	@Override
	public List<JobHistoryModel> getManualRunning(String groupId) {
		GroupBean gb=null;
		GroupBean globe=readOnlyGroupManager.getGlobeGroupBeanForTreeDisplay(false);
		if(globe.getGroupDescriptor().getId().equals(groupId)){
			gb=globe;
		}else{
			gb=globe.getAllSubGroupBeans().get(groupId);
		}
		Set<String> jobs=gb.getAllSubJobBeans().keySet();
		List<JobHistory> list=jobHistoryManager.findRecentRunningHistory();
		for(Iterator<JobHistory> it=list.iterator();it.hasNext();){
			JobHistory j=it.next();
			if(j.getStatus()==Status.SUCCESS || j.getStatus()==Status.FAILED){
				it.remove();
				continue;
			}
			if(j.getTriggerType()!=TriggerType.MANUAL){
				it.remove();
				continue;
			}
			if(!jobs.contains(j.getJobId())){
				it.remove();
				continue;
			}
		}
		List<JobHistoryModel> result=new ArrayList<JobHistoryModel>();
		for(JobHistory his:list){
			JobHistoryModel d=new JobHistoryModel();
			d.setId(his.getId());
			d.setName(gb.getAllSubJobBeans().get(his.getJobId()).getJobDescriptor().getName());
			d.setOwner(gb.getAllSubJobBeans().get(his.getJobId()).getJobDescriptor().getOwner());
			d.setJobId(his.getJobId());
			d.setStartTime(his.getStartTime());
			d.setEndTime(his.getEndTime());
			d.setExecuteHost(his.getExecuteHost());
			d.setStatus(his.getStatus()==null?null:his.getStatus().toString());
			String type="";
			if(his.getTriggerType()!=null){
				if(his.getTriggerType()==TriggerType.MANUAL){
					type="手动调度";
				}else if(his.getTriggerType()==TriggerType.MANUAL_RECOVER){
					type="手动恢复";
				}else if(his.getTriggerType()==TriggerType.SCHEDULE){
					type="自动调度";
				}
			}
			d.setTriggerType(type);
			d.setIllustrate(his.getIllustrate());
			result.add(d);
		}
		return result;
	}
	@Override
	public void move(String jobId, String newGroupId) throws GwtException {
		try {
			permissionGroupManager.moveJob(LoginUser.getUser().getUid(), jobId, newGroupId);
		} catch (ZeusException e) {
			log.error("move",e);
			throw new GwtException(e.getMessage());
		}
	}
	@Override
	public void syncScript(String jobId, String script) throws GwtException {
		JobDescriptor jd=permissionGroupManager.getJobDescriptor(jobId).getX();
		jd.setScript(script);
		try {
			permissionGroupManager.updateJob(LoginUser.getUser().getUid(), jd);
		} catch (ZeusException e) {
			log.error("syncScript",e);
			throw new GwtException("同步失败，可能是因为目标任务没有配置一些必填项。请去调度中心配置完整的必填项. cause:"+e.getMessage());
		}
	}
}