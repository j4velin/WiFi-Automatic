/*
 * Copyright 2016 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.wifiAutoOff;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.Holder> {

    private final List<Log.Item> items;
    private final static DateFormat dateFormat = SimpleDateFormat.getTimeInstance();

    public LogAdapter(final List<Log.Item> items) {
        this.items = items;
    }

    @Override
    public Holder onCreateViewHolder(final ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.logitem, parent, false);
        Holder holder = new Holder(v);
        return holder;
    }

    @Override
    public void onBindViewHolder(final Holder holder, int position) {
        Log.Item item = items.get(position);
        holder.date.setText(dateFormat.format(new Date(item.date)));
        holder.text.setText(item.text);
        holder.text.setCompoundDrawablesWithIntrinsicBounds(item.type.drawable, 0, 0, 0);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class Holder extends RecyclerView.ViewHolder {

        private final TextView date, text;

        public Holder(final View itemView) {
            super(itemView);
            date = (TextView) itemView.findViewById(R.id.date);
            text = (TextView) itemView.findViewById(R.id.text);
        }
    }
}
