package com.gentics.mesh.plugin.netlify.config;

import java.util.ArrayList;
import java.util.List;

public class NetlifyPluginConfig {

	private List<NetlifyHook> hooks = new ArrayList<>();

	public List<NetlifyHook> getHooks() {
		return hooks;
	}

	public void addHook(NetlifyHook hook) {
		hooks.add(hook);
	}

	public void removeHook(String hookId) {
		hooks.removeIf(h -> h.getId().equals(hookId));
	}

}
