package com.morgan.design.android.dao;

import static com.morgan.design.android.util.ObjectUtils.isNotNull;

import java.sql.SQLException;
import java.util.concurrent.Callable;

import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.morgan.design.android.domain.orm.Notification;
import com.morgan.design.android.repository.DatabaseHelper;
import com.morgan.design.android.util.DBUtils;
import com.morgan.design.android.util.Logger;

public class NotificationDao extends AbstractDao<Notification, Integer> {

	private static final String LOG_TAG = "NotificationDao";

	public NotificationDao(final DatabaseHelper databaseHelper) {
		super(databaseHelper);
	}

	public Notification findNotificationForWeatherId(final int weatherChoiceId) {
		try {
			final QueryBuilder<Notification, Integer> queryBuilder = this.dao.queryBuilder();
			queryBuilder.where().eq(Notification.FK_WEATHER_CHOICE_ID, new SelectArg(weatherChoiceId));

			Logger.d(LOG_TAG, queryBuilder.prepareStatementString());
			return this.dao.queryForFirst(queryBuilder.prepare());
		}
		catch (final SQLException exception) {
			logError(exception);
		}
		return null;
	}

	public int delete(final Notification notification) {
		Logger.d(LOG_TAG, "Deleting notification, notification=[%s]", notification);
		return DBUtils.executeInSafety(new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				return NotificationDao.this.dao.delete(notification);
			}
		});
	}

	public Long getNumberOfNotifications() {
		return DBUtils.executeInSafety(new Callable<Long>() {
			@Override
			public Long call() throws Exception {
				return NotificationDao.this.dao.countOf();
			}
		});
	}

	public void addNotification(final Notification notification) {
		Logger.d(LOG_TAG, "Create new instance, notification=[%s]", notification);
		if (isNotNull(notification)) {
			try {
				this.dao.create(notification);
			}
			catch (final SQLException exception) {
				logError(exception);
			}
		}
	}
}