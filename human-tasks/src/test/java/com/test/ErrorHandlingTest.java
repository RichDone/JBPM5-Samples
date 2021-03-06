package com.test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.hornetq.CommandBasedHornetQWSHumanTaskHandler;
import org.junit.Test;

public class ErrorHandlingTest extends BaseHumanTaskTest{

	StatefulKnowledgeSession session;
	private long wid;

	@Override
	protected String[] getProcessPaths() {
		return new String[]{"errorHandling.bpmn"};
	}

	@Override
	protected String[] getTestGroups() {
		return new String[]{};
	}
	
	@Override
	protected String[] getTestUsers() {
		return new String[]{"Administrator", "tito"};
	}
	@Override
	protected Map<String, List<String>> getTestUserGroupsAssignments() {
		return Collections.EMPTY_MAP;
	}
	@Test
	public void test_Error() throws Exception {
		KnowledgeBase kbase = createKnowledgeBase();


		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		CommandBasedHornetQWSHumanTaskHandler ht = new CommandBasedHornetQWSHumanTaskHandler(session);
		ht.setClient(this.client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task", ht);
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("limit", new Long(1));
		parameters.put("count", new Long(2));

		int sessionId = session.getId();

		Thread.sleep(1000);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		session.getWorkItemManager().registerWorkItemHandler("TaskFail",
				new WorkItemHandler() {

					@Override
					public void executeWorkItem(WorkItem wi, WorkItemManager wim) {
						try {
							//save the wi id
							wid = wi.getId();
							throw new RuntimeException();
						} catch (Exception e) {
							session.signalEvent("Error", null);
						}
					}

					@Override
					public void abortWorkItem(WorkItem arg0,
							WorkItemManager arg1) {
						// TODO Auto-generated method stub

					}
				});

		ProcessInstance process = session.createProcessInstance("test",
				parameters);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		long processInstanceId = process.getId();

		session.startProcessInstance(processInstanceId);

		session.dispose();

		session = JPAKnowledgeService.loadStatefulKnowledgeSession(sessionId,
				kbase, null, env);
		
		//Now the process failed and there should be a task created for the admin
		List<TaskSummary> tasks = this.client.getTasksAssignedAsPotentialOwner("Administrator", "en-UK", null);
		Assert.assertEquals(1, tasks.size());
		Map<String, Object> res = new HashMap<String, Object>();
		res.put("wid", wid);
		this.client.start(tasks.get(0).getId(), "Administrator");
		this.client.complete(tasks.get(0).getId(), "Administrator", res);
		Thread.sleep(1000);
	}

}