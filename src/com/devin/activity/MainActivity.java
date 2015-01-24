package com.devin.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.devin.draggridview.R;
import com.devin.widget.DragGridView;
import com.devin.widget.DragGridView.OnShiftListener;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		DragGridView grid = (DragGridView)findViewById(R.id.gridview);
		List<String> list = new ArrayList<String>();
		for (int i = 0; i < 100; i++) {
			list.add(i + "");
		}
		final CustomAdapter adapter = new CustomAdapter(list);
		grid.setAdapter(adapter);
		grid.setOnShiftListener(new OnShiftListener() {
			
			@Override
			public void onShift(int dragPos, int movePos) {
				List<String> list = adapter.getList();
				if (movePos == -1) {
					String drag = list.get(dragPos);
					for (int i = dragPos; i < list.size() - 1; i++) {
						list.set(i, list.get(i + 1));
					}
					list.set(list.size() - 1, drag);
				} else if (dragPos < movePos) {
					String drag = list.get(dragPos);
					for (int i = dragPos; i < movePos; i++) {
						list.set(i, list.get(i + 1));
					}
					list.set(movePos, drag);
				} else {
					String drag = list.get(dragPos);
					for (int i = dragPos; i > movePos; i--) {
						list.set(i, list.get(i - 1));
					}
					list.set(movePos, drag);
				}
				adapter.notifyDataSetChanged();
			}
		});
	}
	
	class CustomAdapter extends BaseAdapter {
		
		List<String> list;

		public CustomAdapter(List<String> list) {
			this.list =list;
		}
		
		public List<String> getList() {
			return list;
		}
		
		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public Object getItem(int position) {
			return list.get(position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = new TextView(getApplicationContext());
				TextView text = (TextView) convertView;
				AbsListView.LayoutParams p = new AbsListView.LayoutParams(200, 200);
				text.setGravity(Gravity.CENTER);
				text.setBackgroundColor(Color.BLUE);
				text.setLayoutParams(p);
			}
			TextView textview = (TextView) convertView;
			textview.setText(getItem(position) + "");
			return convertView;
		}

		@Override
		public long getItemId(int position) {
			return Long.valueOf(list.get(position));
		}
		
	}

}
