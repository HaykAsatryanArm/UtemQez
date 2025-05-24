package com.haykasatryan.utemqez;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final List<Map<String, Object>> userList;
    private final OnAdminStatusChangeListener adminStatusChangeListener;
    private final OnDeleteUserListener deleteUserListener;

    interface OnAdminStatusChangeListener {
        void onAdminStatusChange(String uid, boolean isAdmin);
    }

    interface OnDeleteUserListener {
        void onDeleteUser(String uid);
    }

    public UserAdapter(List<Map<String, Object>> userList, OnAdminStatusChangeListener adminStatusChangeListener,
                       OnDeleteUserListener deleteUserListener) {
        this.userList = userList;
        this.adminStatusChangeListener = adminStatusChangeListener;
        this.deleteUserListener = deleteUserListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.user_item, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        Map<String, Object> user = userList.get(position);
        holder.userName.setText((String) user.get("name"));
        holder.userEmail.setText((String) user.get("email"));
        Boolean isAdmin = (Boolean) user.get("isAdmin");
        holder.adminCheckBox.setChecked(isAdmin != null && isAdmin);

        holder.adminCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                adminStatusChangeListener.onAdminStatusChange((String) user.get("uid"), isChecked));

        holder.deleteButton.setOnClickListener(v ->
                deleteUserListener.onDeleteUser((String) user.get("uid")));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userName, userEmail;
        CheckBox adminCheckBox;
        Button deleteButton;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.userName);
            userEmail = itemView.findViewById(R.id.userEmail);
            adminCheckBox = itemView.findViewById(R.id.adminCheckBox);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}