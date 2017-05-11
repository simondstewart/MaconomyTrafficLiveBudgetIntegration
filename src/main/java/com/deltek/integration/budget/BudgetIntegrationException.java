package com.deltek.integration.budget;

public class BudgetIntegrationException extends RuntimeException {

	
	public BudgetIntegrationException() {
		super();
	}

	public BudgetIntegrationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public BudgetIntegrationException(String message, Throwable cause) {
		super(message, cause);
	}

	public BudgetIntegrationException(Throwable cause) {
		super(cause);
	}

	public BudgetIntegrationException(String string) {
		super(string);
	}

}
