package com.deltek.integration.data;

import java.util.Set;

import lombok.Data;

public @Data class Job {

	private String jobNumber;
	private Set<JobTask> jobTasks;
	
}
