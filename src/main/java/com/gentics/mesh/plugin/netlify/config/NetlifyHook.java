package com.gentics.mesh.plugin.netlify.config;

import java.util.List;

public class NetlifyHook {

	private String id;
	private String triggerBranch;
	private List<String> projects;

	public String getId() {
		return id;
	}

	public NetlifyHook setId(String id) {
		this.id = id;
		return this;
	}

	public String getTriggerBranch() {
		return triggerBranch;
	}

	public NetlifyHook setTriggerBranch(String triggerBranch) {
		this.triggerBranch = triggerBranch;
		return this;
	}

	public List<String> getProjects() {
		return projects;
	}

	public void setProjects(List<String> projects) {
		this.projects = projects;
	}
	
	

	@Override
	public String toString() {
		return id;
	}
}
