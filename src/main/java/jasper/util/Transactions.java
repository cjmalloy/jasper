package jasper.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class Transactions {

	/**
	 * Run after the current transaction commits, or immediately when there is no transaction.
	 * Skipped if the transaction rolls back.
	 */
	public static void afterCommit(Runnable fn) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			fn.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				fn.run();
			}
		});
	}
}
