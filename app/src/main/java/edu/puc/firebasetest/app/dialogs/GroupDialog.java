package edu.puc.firebasetest.app.dialogs;

import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import edu.puc.firebasetest.app.R;

/**
 * Created by jose on 10/27/15.
 */
public class GroupDialog extends DialogFragment {
    private GroupDialogListener mListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_add_group, container, false);

        final EditText editGroupName = (EditText) view.findViewById(R.id.edit_group_name);
        Button btnCreateGroup = (Button) view.findViewById(R.id.btn_create_group);
        btnCreateGroup.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onGroupCreated(editGroupName.getText().toString());
                    dismiss();
                }
            }
        });

        return view;
    }

    public void setListener(GroupDialogListener listener) {
        mListener = listener;
    }

    public interface GroupDialogListener {
        void onGroupCreated(String name);
    }
}
