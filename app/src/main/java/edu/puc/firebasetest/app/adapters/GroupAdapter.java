package edu.puc.firebasetest.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import edu.puc.firebasetest.app.R;
import edu.puc.firebasetest.app.cache.ContactsCache;
import edu.puc.firebasetest.app.model.entities.Contact;
import edu.puc.firebasetest.app.model.entities.Room;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by jose on 10/27/15.
 */
public class GroupAdapter extends ArrayAdapter<Room> {
    private LayoutInflater mLayoutInflater;
    private ContactsCache mContacts;
    private Context mContext;
    private DateFormat mDateFormat;

    public GroupAdapter(Context context, List<Room> objects, ContactsCache cache) {
        super(context, R.layout.listview_item_contact, objects);
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContacts = cache;
        mContext = context;
        mDateFormat = DateFormat.getDateTimeInstance();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.listview_item_group, parent, false);
        }

        Room room = getItem(position);

        TextView txtName = (TextView) convertView.findViewById(R.id.txt_name);
        txtName.setText(room.getName());
        TextView txtAuthor = (TextView) convertView.findViewById(R.id.txt_author);
        txtAuthor.setText(mContext.getString(R.string.group_author, mContacts.get(room.getAuthor()).getName()));
        TextView txtTimestamp = (TextView) convertView.findViewById(R.id.txt_timestamp);
        txtTimestamp.setText(mDateFormat.format(new Date(room.getTimestamp())));

        return convertView;
    }
}
