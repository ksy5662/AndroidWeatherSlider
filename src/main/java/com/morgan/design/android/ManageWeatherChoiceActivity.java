package com.morgan.design.android;

import static com.morgan.design.Constants.CANCEL_ALL_WEATHER_NOTIFICATIONS;
import static com.morgan.design.Constants.DELETE_CURRENT_NOTIFCATION;
import static com.morgan.design.Constants.FROM_INACTIVE_LOCATION;
import static com.morgan.design.Constants.REMOVE_CURRENT_NOTIFCATION;
import static com.morgan.design.Constants.WEATHER_ID;
import static com.morgan.design.android.util.ObjectUtils.isNotNull;
import static com.morgan.design.android.util.ObjectUtils.isNull;

import java.util.List;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

import com.j256.ormlite.android.apptools.OrmLiteBaseListActivity;
import com.morgan.design.Changelog;
import com.morgan.design.Constants;
import com.morgan.design.WeatherSliderApplication;
import com.morgan.design.android.SimpleGestureFilter.SimpleGestureListener;
import com.morgan.design.android.adaptor.CurrentChoiceAdaptor;
import com.morgan.design.android.dao.WeatherChoiceDao;
import com.morgan.design.android.domain.orm.WeatherChoice;
import com.morgan.design.android.repository.DatabaseHelper;
import com.morgan.design.android.service.RoamingLookupService;
import com.morgan.design.android.service.ServiceUpdateRegister;
import com.morgan.design.android.service.StaticLookupService;
import com.morgan.design.android.util.DateUtils;
import com.morgan.design.android.util.GoogleAnalyticsService;
import com.morgan.design.android.util.Logger;
import com.morgan.design.android.util.PreferenceUtils;
import com.weatherslider.morgan.design.R;

public class ManageWeatherChoiceActivity extends OrmLiteBaseListActivity<DatabaseHelper> implements SimpleGestureListener {

	private static final String LOG_TAG = "ManageWeatherChoiceActivity";

	private WeatherChoiceDao weatherDao;

	private List<WeatherChoice> woeidChoices;

	private CurrentChoiceAdaptor adaptor;

	private SimpleGestureFilter detector;

	private BroadcastReceiver weatherQueryCompleteBroadcastReceiver;
	private BroadcastReceiver notificationRemovedBroadcastReciever;

	private GoogleAnalyticsService googleAnalyticsService;

	protected ServiceUpdateRegister serviceUpdateRegister;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.weather_choice_layout);
		Changelog.show(this, false);

		this.weatherDao = new WeatherChoiceDao(getHelper());
		this.detector = new SimpleGestureFilter(this, this);
		this.detector.setEnabled(true);
		this.googleAnalyticsService = getToLevelApplication().getGoogleAnalyticsService();
		this.serviceUpdateRegister = new ServiceUpdateRegister(this);

		reLoadWeatherChoices();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (isNull(this.weatherQueryCompleteBroadcastReceiver)) {
			this.weatherQueryCompleteBroadcastReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(final Context context, final Intent intent) {
					Logger.d(LOG_TAG, "Recieved : %s", intent.getAction());
					reLoadWeatherChoices();
				}
			};
			registerReceiver(this.weatherQueryCompleteBroadcastReceiver, new IntentFilter(Constants.LATEST_WEATHER_QUERY_COMPLETE));
		}

		if (isNull(this.notificationRemovedBroadcastReciever)) {
			this.notificationRemovedBroadcastReciever = new BroadcastReceiver() {
				@Override
				public void onReceive(final Context context, final Intent intent) {
					Logger.d(LOG_TAG, "Recieved : %s", intent.getAction());
					reLoadWeatherChoices();
				}
			};
			registerReceiver(this.notificationRemovedBroadcastReciever, new IntentFilter(Constants.NOTIFICATION_REMOVED));
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (isNotNull(this.weatherQueryCompleteBroadcastReceiver)) {
			unregisterReceiver(this.weatherQueryCompleteBroadcastReceiver);
			this.weatherQueryCompleteBroadcastReceiver = null;
		}
		if (isNotNull(this.notificationRemovedBroadcastReciever)) {
			unregisterReceiver(this.notificationRemovedBroadcastReciever);
			this.notificationRemovedBroadcastReciever = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.home_menu, menu);
		return true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (isNotNull(this.serviceUpdateRegister)) {
			this.serviceUpdateRegister.unregisterReceiver();
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.home_menu_changelog:
				this.googleAnalyticsService.trackPageView(this, GoogleAnalyticsService.OPEN_CHANGE_LOG);
				Changelog.show(this, true);
				return true;
			case R.id.home_menu_settings:
				this.googleAnalyticsService.trackPageView(this, GoogleAnalyticsService.OPEN_PREFERENCES);
				PreferenceUtils.openUserPreferenecesActivity(this);
				return true;
			case R.id.home_menu_cancel_all:
				this.googleAnalyticsService.trackPageView(this, GoogleAnalyticsService.CANCEL_ALL);
				sendBroadcast(new Intent(CANCEL_ALL_WEATHER_NOTIFICATIONS));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		this.googleAnalyticsService.trackPageView(this, GoogleAnalyticsService.ADD_NEW_LOCATION);
		switch (requestCode) {
			case Constants.ENTER_LOCATION:
				if (resultCode == RESULT_OK) {
					Logger.d(LOG_TAG, "ENTER_LOCATION -> RESULT_OK");
				}
				break;
			case Constants.SELECT_LOCATION:
				if (resultCode == RESULT_OK) {
					Logger.d(LOG_TAG, "SELECT_LOCATION -> RESULT_OK");
				}
				break;
			case Constants.UPDATED_PREFERENCES:
				if (resultCode == RESULT_OK) {
					sendBroadcast(new Intent(Constants.PREFERENCES_UPDATED));
				}
				break;
			default:
				break;
		}
	}

	@Override
	protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
		super.onListItemClick(l, v, position, id);
		final WeatherChoice woeidChoice = this.woeidChoices.get(position);

		final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle("Manage Location");
		final String dialogText =
				"Location:\n" + woeidChoice.getCurrentLocationText() + "\nLast updated:\n"
					+ DateUtils.dateToSimpleDateFormat(woeidChoice.getLastUpdatedDateTime());
		alertDialog.setMessage(dialogText);

		if (woeidChoice.isActive()) {
			alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Remove", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int id) {
					attemptToKillNotifcation(woeidChoice);
				}
			});
		}
		else {
			alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Load", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int id) {
					onLoadWeatherChoice(woeidChoice);
				}
			});
		}

		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Delete", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int id) {
				attemptToDeleteNotifcation(woeidChoice);
			}
		});

		alertDialog.show();

	}

	// ///////////////////////////////////////
	// ///////////// Click Handlers //////////
	// ///////////////////////////////////////

	public void onAddNewLocation(final View view) {
		final Intent intent = new Intent(this, EnterLocationActivity.class);
		startActivityForResult(intent, Constants.ENTER_LOCATION);
	}

	protected void onLoadWeatherChoice(final WeatherChoice woeidChoice) {
		woeidChoice.setActive(true);
		this.weatherDao.update(woeidChoice);

		final Bundle bundle = new Bundle();
		bundle.putSerializable(WEATHER_ID, woeidChoice.getId());

		final Intent service = woeidChoice.isRoaming()
				? new Intent(this, RoamingLookupService.class)
				: new Intent(this, StaticLookupService.class);

		service.putExtra(FROM_INACTIVE_LOCATION, true);
		service.putExtras(bundle);
		startService(service);
	}

	// //////////////////////////////////////////
	// ///////////// General / Utility //////////
	// //////////////////////////////////////////

	private void reLoadWeatherChoices() {
		this.woeidChoices = this.weatherDao.findAllWoeidChoices();
		this.adaptor = new CurrentChoiceAdaptor(this, this.woeidChoices);
		setListAdapter(this.adaptor);
	}

	private void attemptToKillNotifcation(final WeatherChoice woeidChoice) {
		final Bundle bundle = new Bundle();
		bundle.putInt(WEATHER_ID, woeidChoice.getId());
		sendBroadcast(new Intent(REMOVE_CURRENT_NOTIFCATION).putExtras(bundle));
	}

	private void attemptToDeleteNotifcation(final WeatherChoice woeidChoice) {
		final Bundle bundle = new Bundle();
		bundle.putInt(WEATHER_ID, woeidChoice.getId());
		sendBroadcast(new Intent(DELETE_CURRENT_NOTIFCATION).putExtras(bundle));
		this.adaptor.remove(woeidChoice);
	}

	protected WeatherSliderApplication getToLevelApplication() {
		return ((WeatherSliderApplication) getApplication());
	}

	// /////////////////////////////////////////////
	// ////////// Swipe Gestures ///////////////////
	// /////////////////////////////////////////////

	@Override
	public void onSwipe(final int direction) {
		switch (direction) {
			case SimpleGestureFilter.SWIPE_LEFT:
				onAddNewLocation(null);
				break;
		}
	}

	@Override
	public boolean dispatchTouchEvent(final MotionEvent me) {
		this.detector.onTouchEvent(me);
		return super.dispatchTouchEvent(me);
	}

	@Override
	public void onDoubleTap() {
		// Do nothing at present
	}
}
