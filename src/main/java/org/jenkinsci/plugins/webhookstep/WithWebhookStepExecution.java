package org.jenkinsci.plugins.webhookstep;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.TaskListener;
import jenkins.model.CauseOfInterruption;

public class WithWebhookStepExecution extends AbstractStepExecutionImpl {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1581049075182942225L;

	private static final Logger LOGGER = Logger.getLogger(WithWebhookStepExecution.class.getName());
	@Inject
	private transient WithWebhookStep step;
	private BodyExecution body;
	private transient RunnableFuture<?> killer;
	private String token;
	private WebhookResponse response;
	// private Object triggered;

	public WithWebhookStepExecution(WithWebhookStep step, StepContext context) {
		super(context);
		this.step = step;
		this.response = null;
	}

	@Override
	public boolean start() throws Exception {
		// generate hook url
		token = (step == null || StringUtils.isEmpty(step.getToken())) ? java.util.UUID.randomUUID().toString()
				: URLEncoder.encode(step.getToken(), "UTF-8");
		LOGGER.log(Level.FINER, "webhook token=" + token + " generated.");
		String jenkinsUrl = getContext().get(hudson.EnvVars.class).get("JENKINS_URL");
		if (jenkinsUrl == null || jenkinsUrl.isEmpty()) {
			throw new RuntimeException("JENKINS_URL must be set in the Manage Jenkins console");
		}
		java.net.URI baseUri = new java.net.URI(jenkinsUrl);
		java.net.URI relative = new java.net.URI("with-webhook-step/" + token);
		java.net.URI path = baseUri.resolve(relative);
		Map<String, String> overridesM = new HashMap<>();
		overridesM.put(step.getHookUrlEnv(), path.toString());

		StepContext context = getContext();
		BodyInvoker bodyInvoker = context.newBodyInvoker();
		// .withCallback(new Callback());// to define a callback

		// start body execution
		this.setBody(bodyInvoker.withContext(EnvironmentExpander.merge(context.get(EnvironmentExpander.class),
				EnvironmentExpander.constant(overridesM))).withCallback(new Callback()).start());
		LOGGER.log(Level.FINER, "body script started.");
		setupHookProcessor();
		return false;
	}

	@Override
	public void stop(Throwable cause) throws Exception {
		WithWebhookRootAction.deregisterWebhook(this);
		getContext().onFailure(cause);
	}

	@Override
	public void onResume() {
		// setupTimer(System.currentTimeMillis(), false);
		setupHookProcessor();
	}

	public void onTriggered(WebhookResponse response) {
		LOGGER.log(Level.FINER, "web hook triggered");
		if (this.response == null) {
			this.response = response;
		}
		if (this.getResponse() != null) {
			if (killer != null) {
				synchronized (step) {
					step.notify();
				}
			}
		}
	}

	public synchronized WebhookResponse getResponse() {
		return this.response;
	}

	private TaskListener listener() {
		try {
			return getContext().get(TaskListener.class);
		} catch (Exception x) {
			LOGGER.log(Level.WARNING, null, x);
			return TaskListener.NULL;
		}
	}

	private class Callback extends BodyExecutionCallback.TailCall {

		@Override
		protected void finished(StepContext context) throws Exception {
			if (killer != null) {
				killer.cancel(true);
				killer = null;
				LOGGER.log(Level.FINER, "nested steps finished");
			}
		}

		private static final long serialVersionUID = 1L;

	}

	public static final class WebhookTriggeredException extends CauseOfInterruption {

		private static final long serialVersionUID = 1L;

		@Override
		public String getShortDescription() {
			return "Webhook has been triggered";
		}
	}

	private void cancel() {
		LOGGER.log(Level.FINER, "Cancelling nested steps due to webhook triggered");
		body.cancel(new WebhookTriggeredException());
	}

	private void setupHookProcessor() {
		LOGGER.log(Level.FINER, "setup hook processor");
		if (this.response == null) {
			LOGGER.log(Level.FINER, "register current execution");
			this.response = WithWebhookRootAction.registerWebhook(this);
		}
		if (killer != null) {
			if (this.getResponse() != null) {
				LOGGER.log(Level.FINER, "web hook response recieved");
				LOGGER.log(Level.FINER, "tell killer to kill the nested process");
				step.notify();
			}
			return;
		} else {
			LOGGER.log(Level.FINER, "killer created");
			setKiller(new FutureTask<>(new Runnable() {
				@Override
				public void run() {
					while (getResponse() == null) {
						try {
							LOGGER.log(Level.FINER, "waiting for response");
							synchronized (step) {
								step.wait();
							}
							LOGGER.log(Level.FINER, "triggered");
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						break;
					}
					if (getResponse() != null) {
						LOGGER.log(Level.FINER, "response recieved, cancel nested process");
						cancel();
					}
				}
			}, getResponse()));
			new Thread(killer).start();
		}
	}

	public RunnableFuture<?> getKiller() {
		return killer;
	}

	public void setKiller(RunnableFuture<?> killer) {
		this.killer = killer;
	}

	public BodyExecution getBody() {
		return body;
	}

	public String getToken() {
		return token;
	}

	public void setBody(BodyExecution body) {
		this.body = body;
	}

}
