package edu.puc.firebasetest.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import edu.puc.firebasetest.app.R;
import edu.puc.firebasetest.app.model.entities.Contact;

import java.util.List;

/**
 * Created by jose on 10/20/15.
 */
public class ContactsAdapter extends ArrayAdapter<Contact> {
    private LayoutInflater mLayoutInflater;


    public ContactsAdapter(Context context, List<Contact> objects) {
        super(context, R.layout.listview_item_contact, objects);
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.listview_item_contact, parent, false);
        }

        Contact contact = getItem(position);

        TextView txtContact = (TextView) convertView.findViewById(R.id.txt_contact);
        txtContact.setText(contact.getName());
        return convertView;
    }
}
