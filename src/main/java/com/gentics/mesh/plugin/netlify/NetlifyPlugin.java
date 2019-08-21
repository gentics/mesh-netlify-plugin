package com.gentics.mesh.plugin.netlify;

import static com.gentics.mesh.core.rest.MeshEvent.NODE_DELETED;
import static com.gentics.mesh.core.rest.MeshEvent.NODE_PUBLISHED;
import static com.gentics.mesh.core.rest.MeshEvent.NODE_UNPUBLISHED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.pf4j.PluginWrapper;

import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.plugin.AbstractPlugin;
import com.gentics.mesh.plugin.RestPlugin;
import com.gentics.mesh.plugin.env.PluginEnvironment;
import com.gentics.mesh.plugin.netlify.config.NetlifyHook;
import com.gentics.mesh.plugin.netlify.config.NetlifyPluginConfig;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NetlifyPlugin extends AbstractPlugin implements RestPlugin {

	private static final Logger log = LoggerFactory.getLogger(NetlifyPlugin.class);

	private static final String NETLIFY_HOOKS_URL = "https://api.netlify.com/build_hooks/";

	private List<MessageConsumer<?>> consumers = new ArrayList<>();

	private NetlifyPluginConfig config;

	private OkHttpClient client;

	public NetlifyPlugin(PluginWrapper wrapper, PluginEnvironment env) {
		super(wrapper, env);
		client = new OkHttpClient.Builder().build();
	}

	@Override
	public Completable initialize() {
		return Completable.fromAction(() -> {
			loadConfig();
			registerHookHandler(NODE_PUBLISHED, NODE_DELETED, NODE_UNPUBLISHED);
		});
	}

	private void loadConfig() throws IOException {
		config = readConfig(NetlifyPluginConfig.class);
		if (config == null) {
			config = new NetlifyPluginConfig();
			writeConfig(config);
		}
	}

	private void registerHookHandler(MeshEvent... events) {
		EventBus eb = vertx().eventBus();
		for (MeshEvent event : events) {
			log.info("Registering event handler {" + event + "}");
			consumers.add(eb.consumer(event.address, (Message<JsonObject> eh) -> {
				if (log.isDebugEnabled()) {
					log.debug("Received event {" + event + "}. Triggering webhooks.");
				}
				triggerWebhooks(eh.body());
			}));
		}
	}

	/**
	 * Trigger the webhooks which match the given event.
	 * 
	 * @param event
	 */
	private void triggerWebhooks(JsonObject event) {
		config.getHooks().forEach(hook -> {
			List<String> projectList = hook.getProjects();
			if (projectList != null) {
				JsonObject project = event.getJsonObject("project");
				if (project != null) {
					String projectName = project.getString("name");
					if (projectList.contains(projectName)) {
						triggerHook(hook);
					} else {
						log.trace("Event for project {" + projectName + "} was not whitelisted for hook {" + hook + "}");
					}
				} else {
					log.debug("Hook contains project whitelist but event is not project specific. Ignoring event");
				}
			} else {
				triggerHook(hook);
			}
		});
	}

	/**
	 * Trigger the hook via http client.
	 * 
	 * @param hook
	 */
	private void triggerHook(NetlifyHook hook) {
		String query = "?trigger_title=triggered+by+Gentics+Mesh";
		String triggerBranch = hook.getTriggerBranch();
		if (triggerBranch != null) {
			query += "&trigger_branch=" + hook.getTriggerBranch();
		}
		Request request = new Request.Builder()
			.post(RequestBody.create(MediaType.get("application/json"), "{}"))
			.url(NETLIFY_HOOKS_URL + hook.getId() + query)
			.build();
		try {
			Response response = client.newCall(request).execute();
			if (!response.isSuccessful()) {
				log.error("Triggering webhook failed with code {" + response.code() + "} and message {" + response.message() + "}");
			} else {
				log.debug("Hook {" + hook + "} triggered.");
			}
		} catch (IOException e) {
			log.error("Error while triggering webhook {" + hook + "}", e);
		}

	}

	@Override
	public Completable shutdown() {
		return Observable.fromIterable(consumers).flatMapCompletable(consumer -> {
			return Completable.create(sub -> {
				consumer.unregister(ch -> {
					if (ch.failed()) {
						sub.onError(ch.cause());
					} else {
						sub.onComplete();
					}
				});
			});
		}).doOnComplete(() -> {
			log.info("Unregistered all event handlers");
		});
	}

	@Override
	public Router createGlobalRouter() {
		Router router = Router.router(vertx());

		// Secure all plugin routes
		router.route().handler(rh -> {
			if (!isAdmin(rh)) {
				rh.response().setStatusCode(401).end();
			} else {
				rh.next();
			}
		});

		// List handler
		router.route("/hooks").method(HttpMethod.GET).handler(rh -> {

			HooksResponse hooks = new HooksResponse();
			hooks.setHooks(config.getHooks());
			rh.response().end(Json.encodeToBuffer(hooks));
		});

		// Load single hook
		router.route("/hooks/:hook").method(HttpMethod.GET).handler(rh -> {
			String hookId = rh.request().getParam("hook");
			Optional<NetlifyHook> existingHook = config.getHooks().stream().filter(h -> h.getId().equals(hookId)).findAny();
			boolean hasHook = existingHook.isPresent();
			if (hasHook) {
				rh.response().end(Json.encode(existingHook.get()));
			} else {
				rh.response().setStatusCode(404).end();
			}
		});

		// Add handler
		router.route("/hooks").method(HttpMethod.POST).handler(rh -> {

			NetlifyHook hook = Json.decodeValue(rh.getBody(), NetlifyHook.class);
			Optional<NetlifyHook> existingHook = config.getHooks().stream().filter(h -> h.getId().equals(hook.getId())).findAny();

			boolean hasHook = existingHook.isPresent();
			if (hasHook) {
				config.removeHook(hook.getId());
			}
			config.addHook(hook);
			try {
				writeConfig(config);
				rh.response().setStatusCode(200).end();
			} catch (IOException e) {
				log.error("Writing of config has failed", e);
				rh.fail(e);
			}
		});

		// Removal handler
		router.route("/hooks/:hook").method(HttpMethod.DELETE).handler(rh -> {
			String hookId = rh.request().getParam("hook");
			boolean hasHook = config.getHooks().stream().filter(h -> h.getId().equals(hookId)).findAny().isPresent();
			if (!hasHook) {
				rh.response().setStatusCode(404).end();
			} else {
				config.removeHook(hookId);
				try {
					writeConfig(config);
					rh.response().setStatusCode(204).end();
				} catch (IOException e) {
					log.error("Writing of config has failed", e);
					rh.fail(e);
				}
			}
		});

		return router;
	}

	/**
	 * Check whether the user is in fact an admin.
	 * 
	 * @param rh
	 * @return
	 */
	private boolean isAdmin(RoutingContext rh) {
		User user = rh.user();
		if (user != null) {
			JsonObject principal = user.principal();
			JsonArray roles = principal.getJsonArray("roles");
			boolean hasAdminRole = roles.stream()
				.map(role -> ((JsonObject) role))
				.filter(role -> role.getString("name").equals("admin"))
				.findAny()
				.isPresent();
			return hasAdminRole;
		}
		return false;
	}
}
