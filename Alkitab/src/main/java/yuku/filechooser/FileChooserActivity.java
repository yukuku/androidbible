package yuku.filechooser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileChooserActivity extends BaseActivity {
	static final String EXTRA_config = "config";
	static final String EXTRA_result = "result";

	public static Intent createIntent(Context context, FileChooserConfig config) {
		Intent res = new Intent(context, FileChooserActivity.class);
		res.putExtra(EXTRA_config, config);
		return res;
	}
	
	public static FileChooserResult obtainResult(Intent data) {
		if (data == null) return null;
		return data.getParcelableExtra(EXTRA_result);
	}
	
	ListView lsFile;
	
	FileChooserConfig config;
	FileAdapter adapter;
	File cd;

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.willNeedStoragePermission();
        super.onCreate(savedInstanceState);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

        config = getIntent().getParcelableExtra(EXTRA_config);
        
		Utils.configureTitles(this, config.title, config.subtitle);

        setContentView(R.layout.filechooser_activity_filechooser);
        
        lsFile = (ListView) findViewById(R.id.filechooser_lsFile);
        lsFile.setAdapter(adapter = new FileAdapter());
        lsFile.setOnItemClickListener(lsFile_itemClick);
        
        init();
    }

    private AdapterView.OnItemClickListener lsFile_itemClick = (parent, view, position, id) -> {
		File file = adapter.getItem(position);
		if (file != null) {
			if (file.isDirectory()) {
				cd = file;
				ls();
			} else {
				FileChooserResult result = new FileChooserResult();
				result.currentDir = cd.getAbsolutePath();
				result.firstFilename = file.getAbsolutePath();

				Intent data = new Intent();
				data.putExtra(EXTRA_result, result);

				setResult(RESULT_OK, data);
				finish();
			}
		}
	};

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void init() {
		if (config.initialDir != null) {
			cd = new File(config.initialDir);
		} else {
			cd = Environment.getExternalStorageDirectory();
		}
		
		ls();
	}

	void ls() {
		File[] files = cd.listFiles(new FileFilter() {
			Matcher m;

			@Override
			public boolean accept(File pathname) {
				if (config.pattern == null) {
					return true;
				}
				
				if (pathname.isDirectory()) {
					return true;
				}
				
				if (m == null) {
					m = Pattern.compile(config.pattern).matcher("");
				}
				
				m.reset(pathname.getName());
				return m.matches();
			}
		});
		
		if (files == null) {
			files = new File[0];
		}
		
		Arrays.sort(files, (a, b) -> {
			if (a.isDirectory() && !b.isDirectory()) {
				return -1;
			} else if (!a.isDirectory() && b.isDirectory()) {
				return +1;
			}
			// both files or both dirs

			String aname = a.getName();
			String bname = b.getName();

			// dot-files are later
			if (aname.startsWith(".") && !bname.startsWith(".")) {
				return +1;
			} else if (!aname.startsWith(".") && bname.startsWith(".")) {
				return -1;
			}

			return aname.compareToIgnoreCase(bname);
		});
		
		adapter.setNewData(files);
		lsFile.setSelection(0);
	}
    
	class FileAdapter extends BaseAdapter {
		File[] files;
		
		@Override
		public int getCount() {
			return (files == null? 0: files.length) + 1;
		}

		public void setNewData(File[] files) {
			this.files = files;
			notifyDataSetChanged();
		}

		@Override
		public File getItem(int position) {
			if (files == null) return null;
			if (position == 0) return cd.getParentFile();
			return files[position - 1];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView res = (TextView) (convertView != null? convertView: getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false));
			
			if (position == 0) {
				res.setText(R.string.filechooser_parent_folder);
				res.setCompoundDrawablesWithIntrinsicBounds(R.drawable.filechooser_up, 0, 0, 0);
			} else {
				File file = getItem(position);
				res.setText(file.getName());
				res.setCompoundDrawablesWithIntrinsicBounds(file.isDirectory()? R.drawable.filechooser_folder: R.drawable.filechooser_file, 0, 0, 0);
			}
			
			return res;
		}
	}
}
