package org.jenkinsci.plugins.webhookstep;

import hudson.FilePath;
import hudson.model.Result;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.URL;

public class WithWebhookTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testWaitHook() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "prj");
        URL url = this.getClass().getResource("/simple.json");
        String content = FileUtils.readFileToString(new File(url.getFile()));

        p.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  WithWebhook(token: 'test-token', hookUrlEnv:'WEBHOOK_URL') {\n"
                + "    isUnix() ? sh('echo ${WEBHOOK_URL}') : bat('echo %WEBHOOK_URL%')\n"
                + "    sleep 30\n"
                + "  }\n"
                + "}\n", true));
        
        WorkflowRun r = p.scheduleBuild2(0).waitForStart();

        j.assertBuildStatus(null, r);

        j.postJSON("with-webhook-step/test-token", content);

        j.waitForCompletion(r);
        j.assertBuildStatus(Result.ABORTED, r);
    }

	
}
