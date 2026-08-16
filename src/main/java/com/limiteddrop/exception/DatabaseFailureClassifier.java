package com.limiteddrop.exception;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

public final class DatabaseFailureClassifier {
    private DatabaseFailureClassifier() { }

    public static boolean isUnavailable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataAccessResourceFailureException || current instanceof CannotCreateTransactionException
                    || current instanceof JDBCConnectionException || current instanceof java.sql.SQLTransientConnectionException
                    || current instanceof CannotAcquireLockException || current instanceof QueryTimeoutException
                    || current instanceof TransactionTimedOutException) return true;
            if (current instanceof java.sql.SQLException sqlException
                    && sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("08")) return true;
        }
        return false;
    }
}
