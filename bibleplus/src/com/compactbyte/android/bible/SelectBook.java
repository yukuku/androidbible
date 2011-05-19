package com.compactbyte.android.bible;


import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;

class SelectBook extends ListActivity {
	
	static final String[] COUNTRIES = new String[] {
		"Afghanistan", "Albania", "Algeria", 
		"American Samoa", "Andorra"
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
  
		setListAdapter(new ArrayAdapter<String>(this,
						      android.R.layout.simple_list_item_1, COUNTRIES));
		getListView().setTextFilterEnabled(true);
	}

}
