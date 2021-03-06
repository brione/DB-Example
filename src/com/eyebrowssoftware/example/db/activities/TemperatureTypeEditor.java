package com.eyebrowssoftware.example.db.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.eyebrowssoftware.example.db.R;
import com.eyebrowssoftware.example.db.TemperatureTypeRecords;
import com.eyebrowssoftware.example.db.TemperatureTypeRecords.TemperatureTypeRecord;

public class TemperatureTypeEditor extends Activity {
	public static final String TAG = "ClientNoteTypeEditor";
	
	private static final String[] PROJECTION = {
		TemperatureTypeRecord._ID,
		TemperatureTypeRecord.NAME,
		TemperatureTypeRecord.CREATED_DATE,
		TemperatureTypeRecord.MODIFIED_DATE
	};
	
	public static final int COLUMN_ID_INDEX = 0;
	public static final int COLUMN_NAME_INDEX = 1;
	public static final int COLUMN_CREATED_DATE_INDEX = 2;
	public static final int COLUMN_MODIFIED_DATE_INDEX = 3;
	
	// States the editor can take on
	private static final int STATE_EDIT = 0;
	private static final int STATE_INSERT = 1;

	// Keeper of the above state
	private int _state;
	
	// Bundle name of the saved Uri
	private static final String URI = "uri";
	
	// Uri of the edited or inserted item
	private Uri _uri = null;
	
	private Cursor _cursor;
	
	private Bundle _originalValues = null;
	
	private static final int MENU_SAVE_ID = 0;
	private static final int MENU_CANCEL_ID = 1;
	private static final int MENU_DELETE_ID = 2;
	
	private static final int DELETE_DIALOG_ID = 0;
	
	private EditText _nameView;
	
	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		setContentView(R.layout.temperature_type_editor);
		
		_nameView = (EditText) findViewById(R.id.name);
		
		Intent intent = getIntent();
		String action = intent.getAction();
		
		if(Intent.ACTION_EDIT.equals(action)) {
			_state = STATE_EDIT;
			_uri = intent.getData();
		} else if (Intent.ACTION_INSERT.equals(action)) {
			_state = STATE_INSERT;
			if(icicle != null) { // we saved the inserted Uri here in onPause()
				_uri = Uri.parse(icicle.getString(URI));
			} else {
				ContentValues cv = new ContentValues();
				_uri = getContentResolver().insert(TemperatureTypeRecords.CONTENT_URI, cv);
			}
		} else {
			Log.e(TAG, "Unknown action - Exiting!");
			finish();
			return;
		}
		_cursor = managedQuery(_uri, PROJECTION, null, null, null);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (_cursor != null && _cursor.moveToFirst()) {
			String name = _cursor.getString(COLUMN_NAME_INDEX);
			long createdAt = _cursor.getLong(COLUMN_CREATED_DATE_INDEX);
			long modifiedAt = _cursor.getLong(COLUMN_MODIFIED_DATE_INDEX);
			
			_nameView.setText(name);
			
			if (_originalValues == null) {
				_originalValues = new Bundle();
				_originalValues.putString(TemperatureTypeRecord.NAME, name);
				_originalValues.putLong(TemperatureTypeRecord.CREATED_DATE, createdAt);
				_originalValues.putLong(TemperatureTypeRecord.MODIFIED_DATE, modifiedAt);
			}
		} else {
			setTitle("CURSOR ERROR!!");
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
	
		if(_cursor != null) { // we have an open record
			String name = _nameView.getText().toString();
			long now = System.currentTimeMillis();
			
			ContentValues cv = new ContentValues();
			cv.put(TemperatureTypeRecord.NAME, name);
			cv.put(TemperatureTypeRecord.MODIFIED_DATE, now);
			getContentResolver().update(_uri, cv, null, null);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle icicle) {
		icicle.putAll(_originalValues);
		icicle.putString(URI, _uri.toString());
	}

	@Override
	public void onDestroy() {
		cleanup();
		super.onDestroy();
	}
	
	@Override
	protected void finalize() throws Throwable {
		cleanup();
		super.finalize();
	}
	
	private void cleanup() {
		if(_cursor != null) {
			_cursor.close();
			_cursor = null;
		}
	}
	
	@Override
	public void onBackPressed() {
		String name = _nameView.getText().toString();
		boolean failedValidation = false;
		
		if(name == null || name.length() == 0) {
			Toast.makeText(this, "Temperature Type Name field cannot be left blank", Toast.LENGTH_SHORT).show();
			failedValidation = true;
		} 
		if(! failedValidation) {
			finish();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		if(_state == STATE_EDIT) {
			menu.add(Menu.NONE, MENU_SAVE_ID, 0, R.string.TemperatureTypeEditor_SaveMenu);
			menu.add(Menu.NONE, MENU_CANCEL_ID, 1, R.string.TemperatureTypeEditor_CancelMenu);
			menu.add(Menu.NONE, MENU_DELETE_ID, 2, R.string.TemperatureTypeEditor_DeleteMenu);
			return true;
		} else if (_state == STATE_INSERT) {
			menu.add(Menu.NONE, MENU_SAVE_ID, 0, R.string.TemperatureTypeEditor_SaveMenu);
			menu.add(Menu.NONE, MENU_CANCEL_ID, 1, R.string.TemperatureTypeEditor_CancelMenu);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle all of the possible menu actions.
		switch (item.getItemId()) {
		case MENU_DELETE_ID:
			showDialog(DELETE_DIALOG_ID);
			return true;
		case MENU_CANCEL_ID:
			cancelRecord();
			return true;
		case MENU_SAVE_ID:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private final void cancelRecord() {
		if (_cursor != null) {
			if (_state == STATE_EDIT) {
				// Restore the original information we loaded at first.
				cleanup();
				getContentResolver().update(_uri, getOriginalContentValues(), null, null);
			} else if (_state == STATE_INSERT) {
				// We inserted an empty record, make sure to delete it
				deleteRecord();
			}
		}
		setResult(RESULT_CANCELED);
		finish();
	}


	private ContentValues getOriginalContentValues() {
		ContentValues cv = new ContentValues();
		if(_originalValues != null) {
			cv.put(TemperatureTypeRecord.NAME, _originalValues.getString(TemperatureTypeRecord.NAME));
			cv.put(TemperatureTypeRecord.CREATED_DATE, _originalValues.getLong(TemperatureTypeRecord.CREATED_DATE));
			cv.put(TemperatureTypeRecord.MODIFIED_DATE, _originalValues.getLong(TemperatureTypeRecord.MODIFIED_DATE));
		}
		return cv;
	}

	private final void deleteRecord() {
		cleanup();
		getContentResolver().delete(_uri, null, null);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DELETE_DIALOG_ID:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.TemperatureTypeEditor_ReallyDelete))
				.setCancelable(false)
				.setPositiveButton(getString(R.string.TemperatureTypeEditor_Yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						deleteRecord();
						setResult(RESULT_OK);
						finish();
					}
				})
				.setNegativeButton(getString(R.string.TemperatureTypeEditor_No), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});
			return builder.create();
		default:
			return null;
		}
	}
}
