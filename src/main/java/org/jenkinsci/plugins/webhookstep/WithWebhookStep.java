package org.jenkinsci.plugins.webhookstep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.TaskListener;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

public class WithWebhookStep extends Step implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -7110853152812025894L;
	
	private String hookUrlEnv;
	
	private String token;

    @DataBoundConstructor
	public WithWebhookStep(String hookUrlEnv) {
    	this.setHookUrlEnv(hookUrlEnv);
        this.setToken(null);
	}
	
	public String getHookUrlEnv() {
		return hookUrlEnv;
	}

    @DataBoundSetter
	public void setHookUrlEnv(String hookUrlEnv) {
		this.hookUrlEnv = hookUrlEnv;
	}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new WithWebhookStepExecution(this, context);
    }
    
    public String getToken() {
		return token;
	}

    @DataBoundSetter
	public void setToken(String token) {
		this.token = token;
	}

	@Extension
    public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
		}

		@Override
		public String getFunctionName() {
			return "WithWebhook";
		}
    	
        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
        
        @Override
        public String getDisplayName() {
            return "Webhook controlled life cycle";
        }
    }
    
}
