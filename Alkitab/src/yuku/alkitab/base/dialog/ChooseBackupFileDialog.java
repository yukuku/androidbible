package yuku.alkitab.base.dialog;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.Sqlitil;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChooseBackupFileDialog extends DialogFragment {

	private LayoutInflater inflater;
	private ChooseBackupFileListener chooseBackupFileListener;
	private BackupFileAdapter adapter;

	public interface ChooseBackupFileListener {
		public void onSelect(File file);
	}

	public static ChooseBackupFileDialog getInstance(List<File> backupFiles) {
		ChooseBackupFileDialog dialog = new ChooseBackupFileDialog();
		dialog.adapter = dialog.new BackupFileAdapter();
		dialog.adapter.load(backupFiles);
		return dialog;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		this.inflater = inflater;
		View view;

		view = inflater.inflate(R.layout.dialog_choose_backup_file, null);
		ListView lsFiles = V.get(view, R.id.lsFiles);
		lsFiles.setAdapter(adapter);
		lsFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				chooseBackupFileListener.onSelect(adapter.backupFiles.get(position).first);
			}
		});

		getDialog().setTitle(App.context.getString(R.string.backup_select_file));
		return view;
	}

	public void setChooseBackupFileListener(ChooseBackupFileListener chooseBackupFileListener) {
		this.chooseBackupFileListener = chooseBackupFileListener;
	}

	class BackupFileAdapter extends EasyAdapter {
		List<Pair<File, String>> backupFiles = new ArrayList<Pair<File, String>>();
		public void load(List<File> backupFiles) {
			for (File file : backupFiles) {
				String description = Sqlitil.toLocalDateTimeSimple(new Date(file.lastModified()));
				Pattern pattern = Pattern.compile(".*?autobackup.*?");
				Matcher matcher = pattern.matcher(file.getAbsolutePath());
				if (matcher.matches()) {
					description = App.context.getString(R.string.backup_selection_autobackup_at, description);
				} else {
					description = App.context.getString(R.string.backup_selection_manualbackup_at, description);
				}
				Pair<File, String> backupFile = new Pair<File, String>(file, description);
				this.backupFiles.add(backupFile);
			}
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView tFilename = V.get(view, android.R.id.text1);
			TextView tFileDescription = V.get(view, android.R.id.text2);
			tFilename.setText(backupFiles.get(position).first.getName());
			tFileDescription.setText(backupFiles.get(position).second);
		}

		@Override
		public int getCount() {
			return backupFiles.size();
		}
	}
}
