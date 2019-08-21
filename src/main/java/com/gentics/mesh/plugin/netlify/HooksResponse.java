package com.gentics.mesh.plugin.netlify;

import java.util.ArrayList;
import java.util.List;

import com.gentics.mesh.plugin.netlify.config.NetlifyHook;

public class HooksResponse {

	private List<NetlifyHook> hooks = new ArrayList<>();

	public List<NetlifyHook> getHooks() {
		return hooks;
	}

	public void setHooks(List<NetlifyHook> hooks) {
		this.hooks = hooks;
	}

}
