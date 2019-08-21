package com.gentics.mesh.plugin.netlify;

import static com.gentics.mesh.test.ClientHelper.call;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import com.gentics.mesh.FieldUtil;
import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.project.ProjectCreateRequest;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.plugin.netlify.config.NetlifyHook;
import com.gentics.mesh.test.local.MeshLocalServer;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Test which demonstrates the basic functionality of the plugin. You need to change the hookId field to use is properly.
 */
@Ignore
public class NetlifyPluginTest {

	private static final String apiName = "netlify";

	private static final String hookId = "YOUR_HOOK";

	@ClassRule
	public static final MeshLocalServer server = new MeshLocalServer()
		.withInMemoryMode()
		.withPlugin(NetlifyPlugin.class, apiName)
		.waitForStartup();

	@Test
	public void testPlugin() throws Exception {
		Map<String, ProjectResponse> projects = new HashMap<>();
		for (int i = 0; i < 2; i++) {
			String name = "test" + i;
			ProjectCreateRequest request = new ProjectCreateRequest();
			request.setName(name);
			request.setSchemaRef("folder");
			projects.put(name, call(() -> server.client().createProject(request)));
		}

		JsonObject hooksResponse = new JsonObject(get("/api/v1/plugins/" + apiName + "/hooks"));
		System.out.println(hooksResponse.encodePrettily());

		NetlifyHook hook = new NetlifyHook();
		hook.setId(hookId);
		hook.setProjects(Arrays.asList("test0"));
		System.out.println("Posting:\n" + Json.encode(hook));
		post("/api/v1/plugins/" + apiName + "/hooks", hook);

		JsonObject hooksResponse2 = new JsonObject(get("/api/v1/plugins/" + apiName + "/hooks"));
		System.out.println(hooksResponse2.encodePrettily());

		JsonObject hookResponse = new JsonObject(get("/api/v1/plugins/" + apiName + "/hooks/" + hookId));
		System.out.println(hookResponse.encodePrettily());

		NodeCreateRequest nodeCreateRequest = new NodeCreateRequest();
		nodeCreateRequest.setLanguage("en");
		nodeCreateRequest.setSchemaName("folder");
		nodeCreateRequest.getFields().put("name", FieldUtil.createStringField("abc"));
		nodeCreateRequest.getFields().put("slug", FieldUtil.createStringField("abc"));
		nodeCreateRequest.setParentNodeUuid(projects.get("test0").getRootNode().getUuid());
		NodeResponse nodeResponse = call(() -> server.client().createNode("test0", nodeCreateRequest));
		call(() -> server.client().publishNode("test0", nodeResponse.getUuid()));

		Thread.sleep(5000);
	}

	private OkHttpClient client() {
		Builder builder = new OkHttpClient.Builder();
		return builder.build();
	}

	private String get(String path) throws IOException {
		Request request = new Request.Builder()
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + server.client().getAPIKey())
			.url("http://" + server.getHostname() + ":" + server.getPort() + path)
			.build();

		Response response = client().newCall(request).execute();
		return response.body().string();
	}

	private String post(String path, Object payload) throws IOException {

		Request request = new Request.Builder()
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + server.client().getAPIKey())
			.url("http://" + server.getHostname() + ":" + server.getPort() + path)
			.post(RequestBody.create(MediaType.get("application/json"), Json.encodePrettily(payload)))
			.build();

		Response response = client().newCall(request).execute();
		return response.body().string();
	}
}
